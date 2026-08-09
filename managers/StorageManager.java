package me.krunsh.kfaction.managers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import java.util.concurrent.atomic.AtomicBoolean;

import me.krunsh.kfaction.core.concurrent.BoundedSerialExecutor;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.storage.FlatFileStorage;
import me.krunsh.kfaction.storage.JsonStorageCodec;
import me.krunsh.kfaction.storage.SQLiteStorage;
import me.krunsh.kfaction.storage.Storage;
import me.krunsh.kfaction.storage.StorageSnapshot;
import me.krunsh.kfaction.storage.StorageWriterStats;

/**
 * Coordinateur de persistance V2.
 *
 * Règle fondamentale :
 * - capture Faction/FPlayer sur le thread Bukkit principal ;
 * - writer unique en arrière-plan ;
 * - writer reçoit uniquement StorageSnapshot immuable.
 *
 * Il n'existe plus qu'UN autosave, possédé par ce manager.
 */
public final class StorageManager {

    private static final long WRITER_SHUTDOWN_TIMEOUT_SECONDS = 30L;

    private final Kfaction plugin;
    private final JsonStorageCodec codec;

    private final Set<String> dirtyFactions;
    private final Set<UUID> dirtyPlayers;
    private final AtomicBoolean dirtyGlobalZones;
    private final AtomicBoolean dirtyGraceState;

    private final Set<String> pendingFactionDeletes;
    private final Set<String> pendingPlayerDeletes;
    private final AtomicBoolean deleteDrainScheduled;

    private Storage storage;
    private BoundedSerialExecutor writer;
    private BukkitTask autoSaveTask;

    private int saveIntervalTicks;
    private volatile boolean shuttingDown;
    private volatile long lastBackpressureLogAt;

    public StorageManager(Kfaction plugin) {
        this.plugin = plugin;
        this.codec = new JsonStorageCodec();

        this.dirtyFactions =
                java.util.concurrent.ConcurrentHashMap
                        .newKeySet();

        this.dirtyPlayers =
                java.util.concurrent.ConcurrentHashMap
                        .newKeySet();

        this.dirtyGlobalZones =
                new AtomicBoolean(false);

        this.dirtyGraceState =
                new AtomicBoolean(false);

        this.pendingFactionDeletes =
                java.util.concurrent.ConcurrentHashMap
                        .newKeySet();

        this.pendingPlayerDeletes =
                java.util.concurrent.ConcurrentHashMap
                        .newKeySet();

        this.deleteDrainScheduled =
                new AtomicBoolean(false);

        this.shuttingDown = false;
        this.lastBackpressureLogAt = 0L;
    }

    // ============================================================
    // Lifecycle
    // ============================================================

    public void initialize() {
        String storageType =
                plugin.getConfigManager()
                        .getString(
                                "storage.type",
                                "sqlite"
                        );

        if (storageType == null) {
            storageType = "sqlite";
        }

        String normalized =
                storageType.trim()
                        .toLowerCase(
                                java.util.Locale.ROOT
                        );

        if ("sqlite".equals(normalized)) {
            storage = new SQLiteStorage(plugin);
        } else if ("flatfile".equals(normalized)
                || "json".equals(normalized)) {
            storage = new FlatFileStorage(plugin);

            plugin.getLogger().warning(
                    "FlatFile est conservé uniquement comme "
                            + "backend legacy. SQLite est recommandé."
            );
        } else if ("mysql".equals(normalized)) {
            throw new IllegalStateException(
                    "MySQL n'est pas encore implémenté. "
                            + "Utilise storage.type: SQLITE "
                            + "ou FLATFILE."
            );
        } else {
            throw new IllegalStateException(
                    "Backend storage inconnu: "
                            + storageType
            );
        }

        /*
         * Pas de fallback silencieux si SQLite échoue :
         * utiliser une ancienne source JSON à la place créerait deux
         * sources de vérité et pourrait réintroduire des données supprimées.
         */
        storage.initialize();

        writer = createWriter();

        reloadSettings();

        plugin.getLogger().info(
                "StorageManager V2 initialisé "
                        + "(backend=" + storage.getType()
                        + ", writer=single-thread)"
        );
    }

    /**
     * Recharge uniquement les réglages du coordinateur.
     */
    public void reloadSettings() {
        int seconds =
                plugin.getConfigManager()
                        .getInt(
                                "storage.auto-save-interval",
                                300
                        );

        saveIntervalTicks =
                seconds > 0
                        ? seconds * 20
                        : 0;

        restartAutoSave();
    }

    public void shutdown() {
        if (shuttingDown) {
            return;
        }

        shuttingDown = true;

        cancelAutoSave();

        /*
         * onDisable est exécuté sur le thread principal.
         * Les coffres doivent avoir été synchronisés AVANT cet appel.
         */
        boolean finalSaveOk =
                saveAllSync();

        if (!finalSaveOk) {
            plugin.getLogger().severe(
                    "Shutdown storage: le snapshot final complet n'a pas été confirmé durable."
            );
        }

        boolean finalDeletesOk =
                flushPendingDeletesSync();

        if (!finalDeletesOk
                || getPendingDeleteCount() > 0) {
            plugin.getLogger().severe(
                    "Shutdown storage: suppressions non confirmées="
                            + getPendingDeleteCount()
            );
        }

        if (writer != null) {
            writer.shutdown();

            try {
                if (!writer.awaitTermination(
                        WRITER_SHUTDOWN_TIMEOUT_SECONDS,
                        TimeUnit.SECONDS
                )) {
                    plugin.getLogger().severe(
                            "Le writer stockage n'a pas terminé "
                                    + "dans le délai; arrêt forcé."
                    );
                    writer.shutdownNow();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                writer.shutdownNow();
            }
        }

        if (storage != null) {
            storage.shutdown();
        }

        dirtyFactions.clear();
        dirtyPlayers.clear();
        dirtyGlobalZones.set(false);
        dirtyGraceState.set(false);

        pendingFactionDeletes.clear();
        pendingPlayerDeletes.clear();
        deleteDrainScheduled.set(false);
    }

    // ============================================================
    // Dirty tracking
    // ============================================================

    public void markDirty(Faction faction) {
        if (faction != null
                && !faction.isSystemFaction()
                && !pendingFactionDeletes.contains(
                        faction.getId()
                )) {
            dirtyFactions.add(
                    faction.getId()
            );
        }
    }

    public void markDirty(FPlayer fPlayer) {
        if (fPlayer != null
                && fPlayer.getUuid() != null
                && !pendingPlayerDeletes.contains(
                        fPlayer.getUuid()
                                .toString()
                )) {
            dirtyPlayers.add(
                    fPlayer.getUuid()
            );
        }
    }

    public void markGlobalZonesDirty() {
        dirtyGlobalZones.set(true);
    }

    public void markGraceStateDirty() {
        dirtyGraceState.set(true);
    }

    public String loadGlobalZonesPayload() {
        return storage != null
                ? storage.loadGlobalZonesPayload()
                : null;
    }

    public String loadGraceStatePayload() {
        return storage != null
                ? storage.loadGraceStatePayload()
                : null;
    }

    // ============================================================
    // Chargement
    // ============================================================

    public void loadAll() {
        if (plugin.getFactionManager() == null) {
            plugin.getLogger().severe(
                    "Impossible de charger les données: "
                            + "FactionManager non initialisé."
            );
            return;
        }

        storage.loadFactions(
                plugin.getFactionManager()::loadFaction
        );

        /*
         * Les FPlayers restent lazy-loaded par FPlayerManager.
         * L'index des claims est reconstruit dans Kfaction.loadData(),
         * une seule fois après les migrations progression.
         */
        plugin.getLogger().info(
                "Données factions chargées avec succès"
        );
    }

    // ============================================================
    // Flush async sécurisé
    // ============================================================

    /**
     * API legacy conservée.
     *
     * La méthode ne sérialise jamais le domaine depuis un thread async :
     * si elle est appelée hors main-thread, elle programme la capture sur
     * le thread Bukkit.
     */
    public void saveAsync() {
        flushAsync();
    }

    public void flushAsync() {
        if (shuttingDown) {
            return;
        }

        if (!Bukkit.isPrimaryThread()) {
            plugin.getServer()
                    .getScheduler()
                    .runTask(
                            plugin,
                            this::flushAsync
                    );
            return;
        }

        scheduleDeleteDrain();

        List<StorageSnapshot> snapshots =
                captureDirtySnapshots();

        if (!snapshots.isEmpty()) {
            submitSnapshots(snapshots);
        }
    }

    private List<StorageSnapshot> captureDirtySnapshots() {
        assertPrimaryThread(
                "captureDirtySnapshots"
        );

        List<StorageSnapshot> snapshots =
                new ArrayList<>();

        for (String factionId
                : new ArrayList<>(dirtyFactions)) {
            if (!dirtyFactions.remove(factionId)) {
                continue;
            }

            if (pendingFactionDeletes.contains(
                    factionId
            )) {
                continue;
            }

            Faction faction =
                    plugin.getFactionManager()
                            .getFaction(factionId);

            if (faction == null
                    || faction.isSystemFaction()) {
                continue;
            }

            try {
                snapshots.add(
                        codec.captureFaction(faction)
                );
            } catch (Exception exception) {
                dirtyFactions.add(factionId);

                plugin.getLogger().severe(
                        "Snapshot faction impossible "
                                + factionId
                                + ": "
                                + exception.getMessage()
                );
            }
        }

        for (UUID playerId
                : new ArrayList<>(dirtyPlayers)) {
            if (!dirtyPlayers.remove(playerId)) {
                continue;
            }

            if (pendingPlayerDeletes.contains(
                    playerId.toString()
            )) {
                continue;
            }

            FPlayer fPlayer =
                    plugin.getFPlayerManager()
                            .findLoaded(playerId);

            if (fPlayer == null) {
                /*
                 * Ne jamais recréer un profil uniquement pour le sauvegarder.
                 */
                continue;
            }

            try {
                snapshots.add(
                        codec.captureFPlayer(fPlayer)
                );
            } catch (Exception exception) {
                dirtyPlayers.add(playerId);

                plugin.getLogger().severe(
                        "Snapshot FPlayer impossible "
                                + playerId
                                + ": "
                                + exception.getMessage()
                );
            }
        }

        if (dirtyGlobalZones.compareAndSet(
                true,
                false
        )) {
            try {
                if (plugin.getClaimManager() != null
                        && plugin.getClaimManager()
                                .getZoneService() != null) {
                    snapshots.add(
                            StorageSnapshot.globalZones(
                                    plugin.getClaimManager()
                                            .getZoneService()
                                            .capturePayloadJson()
                            )
                    );
                }
            } catch (Exception exception) {
                dirtyGlobalZones.set(true);

                plugin.getLogger().severe(
                        "Snapshot Global Zones impossible: "
                                + exception.getMessage()
                );
            }
        }

        if (dirtyGraceState.compareAndSet(
                true,
                false
        )) {
            try {
                if (plugin.getPermissionManager() != null
                        && plugin.getPermissionManager()
                                .getGraceService() != null) {
                    snapshots.add(
                            StorageSnapshot.graceState(
                                    plugin.getPermissionManager()
                                            .getGraceService()
                                            .capturePayloadJson()
                            )
                    );
                }
            } catch (Exception exception) {
                dirtyGraceState.set(true);

                plugin.getLogger().severe(
                        "Snapshot Grace State impossible: "
                                + exception.getMessage()
                );
            }
        }

        return snapshots;
    }

    private void submitSnapshots(
            List<StorageSnapshot> snapshots
    ) {
        if (snapshots == null
                || snapshots.isEmpty()) {
            return;
        }

        BoundedSerialExecutor executor = writer;

        if (executor == null
                || executor.isShutdown()) {
            requeueSnapshots(snapshots);
            return;
        }

        if (!executor.tryExecute(
                () -> writeSnapshots(snapshots)
        )) {
            requeueSnapshots(snapshots);
            logBackpressure(
                    "snapshot-batch",
                    snapshots.size()
            );
        }
    }

    private boolean writeSnapshots(
            Collection<StorageSnapshot> snapshots
    ) {
        if (snapshots == null
                || snapshots.isEmpty()) {
            return true;
        }

        boolean success;

        try {
            /*
             * SQLite surcharge writeSnapshots() :
             * tout le batch est alors une transaction unique.
             * FlatFile conserve son comportement atomique fichier par fichier.
             */
            success =
                    storage.writeSnapshots(snapshots);
        } catch (Exception exception) {
            success = false;

            plugin.getLogger().severe(
                    "Erreur writer batch stockage: "
                            + exception.getMessage()
            );
        }

        if (!success) {
            requeueSnapshots(snapshots);
        }

        return success;
    }

    // ============================================================
    // Flush durable synchrone
    // ============================================================

    /**
     * Capture tous les objets chargés sur le main thread, soumet au writer
     * unique puis attend sa fin.
     */
    public boolean saveAllSync() {
        if (plugin.getFactionManager() == null
                || plugin.getFPlayerManager() == null
                || storage == null) {
            return false;
        }

        if (!Bukkit.isPrimaryThread()) {
            plugin.getLogger().severe(
                    "saveAllSync doit être appelé sur le thread principal."
            );
            return false;
        }

        List<StorageSnapshot> snapshots =
                captureAllSnapshots();

        return submitAndWait(snapshots);
    }

    /**
     * Point durable utilisé par les transitions progression.
     */
    public boolean saveFactionNow(Faction faction) {
        if (faction == null
                || faction.isSystemFaction()
                || storage == null) {
            return false;
        }

        if (!Bukkit.isPrimaryThread()) {
            plugin.getLogger().severe(
                    "saveFactionNow refusé hors thread principal "
                            + "pour protéger la cohérence du snapshot."
            );
            return false;
        }

        StorageSnapshot snapshot;

        try {
            snapshot =
                    codec.captureFaction(faction);
        } catch (Exception exception) {
            dirtyFactions.add(
                    faction.getId()
            );

            plugin.getLogger().severe(
                    "Snapshot durable impossible "
                            + faction.getId()
                            + ": "
                            + exception.getMessage()
            );
            return false;
        }

        boolean success =
                submitAndWait(
                        java.util.Collections.singletonList(
                                snapshot
                        )
                );

        if (success) {
            dirtyFactions.remove(
                    faction.getId()
            );
        } else {
            dirtyFactions.add(
                    faction.getId()
            );
        }

        return success;
    }

    private List<StorageSnapshot> captureAllSnapshots() {
        assertPrimaryThread(
                "captureAllSnapshots"
        );

        List<StorageSnapshot> snapshots =
                new ArrayList<>();

        for (Faction faction
                : plugin.getFactionManager()
                        .getPlayerFactions()) {
            try {
                snapshots.add(
                        codec.captureFaction(faction)
                );
            } catch (Exception exception) {
                dirtyFactions.add(
                        faction.getId()
                );

                plugin.getLogger().severe(
                        "Snapshot shutdown faction impossible "
                                + faction.getId()
                                + ": "
                                + exception.getMessage()
                );
            }
        }

        for (FPlayer fPlayer
                : plugin.getFPlayerManager()
                        .getAllPlayers()) {
            try {
                snapshots.add(
                        codec.captureFPlayer(fPlayer)
                );
            } catch (Exception exception) {
                dirtyPlayers.add(
                        fPlayer.getUuid()
                );

                plugin.getLogger().severe(
                        "Snapshot shutdown FPlayer impossible "
                                + fPlayer.getUuid()
                                + ": "
                                + exception.getMessage()
                );
            }
        }

        try {
            if (plugin.getClaimManager() != null
                    && plugin.getClaimManager()
                            .getZoneService() != null) {
                snapshots.add(
                        StorageSnapshot.globalZones(
                                plugin.getClaimManager()
                                        .getZoneService()
                                        .capturePayloadJson()
                        )
                );
                dirtyGlobalZones.set(false);
            }
        } catch (Exception exception) {
            dirtyGlobalZones.set(true);

            plugin.getLogger().severe(
                    "Snapshot shutdown Global Zones impossible: "
                            + exception.getMessage()
            );
        }

        try {
            if (plugin.getPermissionManager() != null
                    && plugin.getPermissionManager()
                            .getGraceService() != null) {
                snapshots.add(
                        StorageSnapshot.graceState(
                                plugin.getPermissionManager()
                                        .getGraceService()
                                        .capturePayloadJson()
                        )
                );
                dirtyGraceState.set(false);
            }
        } catch (Exception exception) {
            dirtyGraceState.set(true);

            plugin.getLogger().severe(
                    "Snapshot shutdown Grace State impossible: "
                            + exception.getMessage()
            );
        }

        return snapshots;
    }

    private boolean submitAndWait(
            List<StorageSnapshot> snapshots
    ) {
        BoundedSerialExecutor executor = writer;

        if (executor == null
                || executor.isShutdown()) {
            return writeSnapshots(snapshots);
        }

        try {
            Future<Boolean> future =
                    shuttingDown
                            ? executor.submitWithTimeout(
                                    () -> writeSnapshots(
                                            snapshots
                                    ),
                                    getShutdownEnqueueTimeoutSeconds(),
                                    TimeUnit.SECONDS
                            )
                            : executor.trySubmit(
                                    () -> writeSnapshots(
                                            snapshots
                                    )
                            );

            if (future == null) {
                requeueSnapshots(snapshots);
                logBackpressure(
                        "durable-write",
                        snapshots != null
                                ? snapshots.size()
                                : 0
                );
                return false;
            }

            return future.get();

        } catch (Exception exception) {
            requeueSnapshots(snapshots);

            plugin.getLogger().severe(
                    "Erreur d'attente du writer stockage: "
                            + exception.getMessage()
            );

            return false;
        }
    }

    // ============================================================
    // Suppressions ordonnées / tombstones
    // ============================================================

    public void deleteFaction(
            String factionId
    ) {
        if (factionId == null
                || factionId.trim().isEmpty()) {
            return;
        }

        dirtyFactions.remove(
                factionId
        );

        pendingFactionDeletes.add(
                factionId
        );

        scheduleDeleteDrain();
    }

    public void deleteFPlayer(
            String playerId
    ) {
        if (playerId == null
                || playerId.trim().isEmpty()) {
            return;
        }

        try {
            dirtyPlayers.remove(
                    UUID.fromString(
                            playerId
                    )
            );
        } catch (IllegalArgumentException ignored) {
            // Le backend validera aussi l'identifiant.
        }

        pendingPlayerDeletes.add(
                playerId
        );

        scheduleDeleteDrain();
    }

    /**
     * Programme au maximum UNE tâche de drain.
     *
     * Les IDs restent dans les sets jusqu'à la suppression durable réussie.
     * Une saturation de queue ne perd donc jamais un delete.
     */
    private void scheduleDeleteDrain() {
        if (pendingFactionDeletes.isEmpty()
                && pendingPlayerDeletes.isEmpty()) {
            return;
        }

        BoundedSerialExecutor executor =
                writer;

        if (executor == null
                || executor.isShutdown()) {
            return;
        }

        if (!deleteDrainScheduled.compareAndSet(
                false,
                true
        )) {
            return;
        }

        if (!executor.tryExecute(
                new Runnable() {
                    @Override
                    public void run() {
                        boolean failed =
                                !drainPendingDeletesOnce();

                        deleteDrainScheduled.set(
                                false
                        );

                        /*
                         * Si de nouveaux deletes sont apparus pendant le drain
                         * et qu'aucune erreur DB n'a eu lieu, on programme la
                         * suite. En cas d'erreur backend on attend le prochain
                         * autosave/appel explicite afin d'éviter une boucle CPU.
                         */
                        if (!failed
                                && (!pendingFactionDeletes
                                        .isEmpty()
                                || !pendingPlayerDeletes
                                        .isEmpty())) {
                            scheduleDeleteDrain();
                        }
                    }
                }
        )) {
            deleteDrainScheduled.set(
                    false
            );

            logBackpressure(
                    "delete-drain",
                    getPendingDeleteCount()
            );
        }
    }

    private boolean drainPendingDeletesOnce() {
        boolean success = true;

        for (String factionId
                : new ArrayList<String>(
                        pendingFactionDeletes
                )) {
            try {
                storage.deleteFaction(
                        factionId
                );

                pendingFactionDeletes.remove(
                        factionId
                );

            } catch (Exception exception) {
                success = false;

                plugin.getLogger().severe(
                        "Suppression faction différée impossible "
                                + factionId
                                + ": "
                                + exception.getMessage()
                );
            }
        }

        for (String playerId
                : new ArrayList<String>(
                        pendingPlayerDeletes
                )) {
            try {
                storage.deleteFPlayer(
                        playerId
                );

                pendingPlayerDeletes.remove(
                        playerId
                );

            } catch (Exception exception) {
                success = false;

                plugin.getLogger().severe(
                        "Suppression FPlayer différée impossible "
                                + playerId
                                + ": "
                                + exception.getMessage()
                );
            }
        }

        return success;
    }

    /**
     * Utilisé au shutdown après saveAllSync().
     *
     * La tâche est mise derrière les writes déjà acceptés dans le writer,
     * ce qui conserve l'ordre write -> delete.
     */
    private boolean flushPendingDeletesSync() {
        if (pendingFactionDeletes.isEmpty()
                && pendingPlayerDeletes.isEmpty()) {
            return true;
        }

        BoundedSerialExecutor executor =
                writer;

        if (executor == null
                || executor.isShutdown()) {
            return drainPendingDeletesOnce();
        }

        try {
            Future<Boolean> future =
                    executor.submitWithTimeout(
                            () -> drainPendingDeletesOnce(),
                            getShutdownEnqueueTimeoutSeconds(),
                            TimeUnit.SECONDS
                    );

            if (future == null) {
                logBackpressure(
                        "shutdown-delete-drain",
                        getPendingDeleteCount()
                );

                return false;
            }

            return Boolean.TRUE.equals(
                    future.get()
            );

        } catch (Exception exception) {
            plugin.getLogger().severe(
                    "Erreur flush suppressions storage: "
                            + exception.getMessage()
            );

            return false;
        }
    }

    // ============================================================
    // Autosave unique
    // ============================================================

    private void restartAutoSave() {
        cancelAutoSave();

        if (saveIntervalTicks <= 0
                || shuttingDown) {
            return;
        }

        autoSaveTask =
                plugin.getServer()
                        .getScheduler()
                        .runTaskTimer(
                                plugin,
                                this::flushAsync,
                                saveIntervalTicks,
                                saveIntervalTicks
                        );
    }

    private void cancelAutoSave() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }
    }

    // ============================================================
    // Writer / retry
    // ============================================================

    private BoundedSerialExecutor createWriter() {
        int queueCapacity =
                clamp(
                        plugin.getConfigManager()
                                .getInt(
                                        "storage.writer.queue-capacity",
                                        256
                                ),
                        16,
                        4096
                );

        return new BoundedSerialExecutor(
                "Kfaction-StorageWriter",
                queueCapacity,
                true
        );
    }

    private void logBackpressure(
            String operation,
            int batchSize
    ) {
        long now =
                System.currentTimeMillis();

        /*
         * Rate limit 5 s afin qu'un disque en panne ne spamme pas la console.
         */
        if (now - lastBackpressureLogAt
                < 5000L) {
            return;
        }

        lastBackpressureLogAt =
                now;

        StorageWriterStats stats =
                getWriterStats();

        plugin.getLogger().warning(
                "Storage writer saturé: operation="
                        + operation
                        + ", batch="
                        + batchSize
                        + ", queue="
                        + stats.getQueueSize()
                        + "/"
                        + stats.getQueueCapacity()
                        + ", rejected="
                        + stats.getRejectedTasks()
                        + ". Les snapshots restent dirty et seront retentés."
        );
    }

    private void requeueSnapshots(
            Collection<StorageSnapshot> snapshots
    ) {
        if (snapshots == null) {
            return;
        }

        for (StorageSnapshot snapshot : snapshots) {
            requeueSnapshot(snapshot);
        }
    }

    private void requeueSnapshot(
            StorageSnapshot snapshot
    ) {
        if (snapshot == null) {
            return;
        }

        switch (snapshot.getEntityType()) {
            case FACTION:
                dirtyFactions.add(
                        snapshot.getEntityId()
                );
                break;

            case FPLAYER:
                try {
                    dirtyPlayers.add(
                            UUID.fromString(
                                    snapshot.getEntityId()
                            )
                    );
                } catch (IllegalArgumentException ignored) {
                    // Identifiant invalide : impossible à retenter.
                }
                break;

            case GLOBAL_ZONES:
                dirtyGlobalZones.set(true);
                break;

            case GRACE_STATE:
                dirtyGraceState.set(true);
                break;

            default:
                break;
        }
    }

    private long getShutdownEnqueueTimeoutSeconds() {
        return clamp(
                plugin.getConfigManager()
                        .getInt(
                                "storage.writer.shutdown-enqueue-timeout-seconds",
                                10
                        ),
                1,
                30
        );
    }

    private static int clamp(
            int value,
            int min,
            int max
    ) {
        return Math.max(
                min,
                Math.min(
                        max,
                        value
                )
        );
    }

    private static void assertPrimaryThread(
            String operation
    ) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(
                    operation
                            + " must run on Bukkit primary thread"
            );
        }
    }

    // ============================================================
    // Diagnostics
    // ============================================================

    public Storage getStorage() {
        return storage;
    }

    public int getDirtyFactionCount() {
        return dirtyFactions.size();
    }

    public int getDirtyPlayerCount() {
        return dirtyPlayers.size();
    }

    public boolean isGlobalZonesDirty() {
        return dirtyGlobalZones.get();
    }

    public boolean isGraceStateDirty() {
        return dirtyGraceState.get();
    }

    public int getPendingWriteCount() {
        BoundedSerialExecutor executor =
                writer;

        return executor != null
                ? executor.getQueueSize()
                : 0;
    }

    public int getWriterQueueCapacity() {
        BoundedSerialExecutor executor =
                writer;

        return executor != null
                ? executor.getQueueCapacity()
                : 0;
    }

    public long getRejectedWriteTaskCount() {
        BoundedSerialExecutor executor =
                writer;

        return executor != null
                ? executor.getRejectedTasks()
                : 0L;
    }

    public int getPendingDeleteCount() {
        return pendingFactionDeletes.size()
                + pendingPlayerDeletes.size();
    }

    public StorageWriterStats getWriterStats() {
        BoundedSerialExecutor executor =
                writer;

        if (executor == null) {
            return new StorageWriterStats(
                    0,
                    0,
                    0L,
                    0L,
                    0L,
                    0L,
                    pendingFactionDeletes.size(),
                    pendingPlayerDeletes.size()
            );
        }

        return new StorageWriterStats(
                executor.getQueueSize(),
                executor.getQueueCapacity(),
                executor.getAcceptedTasks(),
                executor.getRejectedTasks(),
                executor.getCompletedTasks(),
                executor.getFailedTasks(),
                pendingFactionDeletes.size(),
                pendingPlayerDeletes.size()
        );
    }
}
