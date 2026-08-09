package me.krunsh.kfaction.storage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;

/**
 * Backend JSON V2.
 *
 * Propriétés :
 * - lecture compatible avec les fichiers V1 ;
 * - écriture à partir de StorageSnapshot immuable ;
 * - remplacement atomique pour factions ET joueurs ;
 * - aucun accès à Faction/FPlayer depuis le writer async.
 */
public final class FlatFileStorage implements Storage {

    private final Kfaction plugin;
    private final JsonStorageCodec codec;

    private File factionsFolder;
    private File playersFolder;
    private File zonesFolder;
    private File graceFolder;

    private volatile boolean connected;

    public FlatFileStorage(Kfaction plugin) {
        this.plugin = plugin;
        this.codec = new JsonStorageCodec();
        this.connected = false;
    }

    @Override
    public void initialize() {
        factionsFolder =
                new File(
                        plugin.getDataFolder(),
                        "data/factions"
                );

        playersFolder =
                new File(
                        plugin.getDataFolder(),
                        "data/players"
                );

        zonesFolder =
                new File(
                        plugin.getDataFolder(),
                        "data/zones"
                );

        graceFolder =
                new File(
                        plugin.getDataFolder(),
                        "data/grace"
                );

        ensureDirectory(factionsFolder);
        ensureDirectory(playersFolder);
        ensureDirectory(zonesFolder);
        ensureDirectory(graceFolder);

        connected = true;

        plugin.getLogger().info(
                "FlatFileStorage V2 initialisé dans "
                        + plugin.getDataFolder().getPath()
        );
    }

    @Override
    public void shutdown() {
        connected = false;
    }

    // ============================================================
    // Lecture factions
    // ============================================================

    @Override
    public void loadFactions(
            Consumer<Faction> consumer
    ) {
        if (consumer == null) {
            return;
        }

        File[] files =
                factionsFolder.listFiles(
                        (directory, name) ->
                                name.endsWith(".json")
                );

        if (files == null) {
            return;
        }

        int loaded = 0;

        for (File file : files) {
            Faction faction =
                    loadFactionFromFile(file);

            if (faction != null) {
                consumer.accept(faction);
                loaded++;
            }
        }

        plugin.getLogger().info(
                "Chargé " + loaded + " factions"
        );
    }

    @Override
    public Faction loadFaction(String factionId) {
        if (!isSafeEntityId(factionId)) {
            return null;
        }

        return loadFactionFromFile(
                new File(
                        factionsFolder,
                        factionId + ".json"
                )
        );
    }

    private Faction loadFactionFromFile(File file) {
        String payload = readUtf8(file);

        if (payload == null) {
            return null;
        }

        try {
            return codec.decodeFaction(payload);
        } catch (Exception exception) {
            plugin.getLogger().severe(
                    "Erreur de chargement faction "
                            + file.getName()
                            + ": "
                            + exception.getMessage()
            );
            return null;
        }
    }

    // ============================================================
    // Lecture FPlayers
    // ============================================================

    @Override
    public void loadFPlayers(
            Consumer<FPlayer> consumer
    ) {
        if (consumer == null) {
            return;
        }

        File[] files =
                playersFolder.listFiles(
                        (directory, name) ->
                                name.endsWith(".json")
                );

        if (files == null) {
            return;
        }

        int loaded = 0;

        for (File file : files) {
            FPlayer fPlayer =
                    loadFPlayerFromFile(file);

            if (fPlayer != null) {
                consumer.accept(fPlayer);
                loaded++;
            }
        }

        plugin.getLogger().info(
                "Chargé " + loaded + " joueurs"
        );
    }

    @Override
    public FPlayer loadFPlayer(String uuid) {
        if (!isSafeEntityId(uuid)) {
            return null;
        }

        return loadFPlayerFromFile(
                new File(
                        playersFolder,
                        uuid + ".json"
                )
        );
    }

    private FPlayer loadFPlayerFromFile(File file) {
        String payload = readUtf8(file);

        if (payload == null) {
            return null;
        }

        try {
            return codec.decodeFPlayer(payload);
        } catch (Exception exception) {
            plugin.getLogger().severe(
                    "Erreur de chargement joueur "
                            + file.getName()
                            + ": "
                            + exception.getMessage()
            );
            return null;
        }
    }

    @Override
    public String loadGlobalZonesPayload() {
        if (!connected || zonesFolder == null) {
            return null;
        }

        return readUtf8(
                new File(
                        zonesFolder,
                        "global.json"
                )
        );
    }

    @Override
    public String loadGraceStatePayload() {
        if (!connected || graceFolder == null) {
            return null;
        }

        return readUtf8(
                new File(
                        graceFolder,
                        "global.json"
                )
        );
    }

    // ============================================================
    // Écriture snapshot
    // ============================================================

    @Override
    public boolean writeSnapshot(
            StorageSnapshot snapshot
    ) {
        if (!connected || snapshot == null) {
            return false;
        }

        File folder;

        switch (snapshot.getEntityType()) {
            case FACTION:
                folder = factionsFolder;
                break;

            case FPLAYER:
                folder = playersFolder;
                break;

            case GLOBAL_ZONES:
                folder = zonesFolder;
                break;

            case GRACE_STATE:
                folder = graceFolder;
                break;

            default:
                return false;
        }

        return atomicWrite(
                folder,
                snapshot.getEntityId(),
                snapshot.getPayloadJson()
        );
    }

    @Override
    @Deprecated
    public void saveFaction(Faction faction) {
        saveFactionChecked(faction);
    }

    @Override
    @Deprecated
    public boolean saveFactionChecked(
            Faction faction
    ) {
        if (faction == null
                || faction.isSystemFaction()) {
            return false;
        }

        /*
         * Compatibilité uniquement.
         * Le StorageManager V2 n'utilise plus ce chemin.
         */
        try {
            return writeSnapshot(
                    codec.captureFaction(faction)
            );
        } catch (Exception exception) {
            plugin.getLogger().severe(
                    "Erreur de snapshot faction "
                            + faction.getId()
                            + ": "
                            + exception.getMessage()
            );
            return false;
        }
    }

    @Override
    @Deprecated
    public void saveFPlayer(FPlayer fPlayer) {
        if (fPlayer == null) {
            return;
        }

        try {
            if (!writeSnapshot(
                    codec.captureFPlayer(fPlayer)
            )) {
                plugin.getLogger().severe(
                        "Erreur de sauvegarde joueur "
                                + fPlayer.getUuid()
                );
            }
        } catch (Exception exception) {
            plugin.getLogger().severe(
                    "Erreur de snapshot joueur "
                            + fPlayer.getUuid()
                            + ": "
                            + exception.getMessage()
            );
        }
    }

    // ============================================================
    // Suppressions
    // ============================================================

    @Override
    public void deleteFaction(String factionId) {
        deleteEntity(
                factionsFolder,
                factionId
        );
    }

    @Override
    public void deleteFPlayer(String uuid) {
        deleteEntity(
                playersFolder,
                uuid
        );
    }

    private void deleteEntity(
            File folder,
            String entityId
    ) {
        if (!isSafeEntityId(entityId)) {
            return;
        }

        File file =
                new File(
                        folder,
                        entityId + ".json"
                );

        File temporary =
                new File(
                        folder,
                        entityId + ".json.tmp"
                );

        try {
            Files.deleteIfExists(
                    temporary.toPath()
            );
            Files.deleteIfExists(
                    file.toPath()
            );
        } catch (IOException exception) {
            plugin.getLogger().severe(
                    "Erreur de suppression "
                            + entityId
                            + ": "
                            + exception.getMessage()
            );
        }
    }

    // ============================================================
    // IO
    // ============================================================

    private boolean atomicWrite(
            File folder,
            String entityId,
            String payload
    ) {
        if (!isSafeEntityId(entityId)
                || payload == null) {
            return false;
        }

        File destination =
                new File(
                        folder,
                        entityId + ".json"
                );

        File temporary =
                new File(
                        folder,
                        entityId + ".json.tmp"
                );

        try {
            Files.write(
                    temporary.toPath(),
                    payload.getBytes(
                            StandardCharsets.UTF_8
                    )
            );

            try {
                Files.move(
                        temporary.toPath(),
                        destination.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(
                        temporary.toPath(),
                        destination.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            return true;
        } catch (Exception exception) {
            plugin.getLogger().severe(
                    "Erreur d'écriture atomique "
                            + entityId
                            + ": "
                            + exception.getMessage()
            );

            try {
                Files.deleteIfExists(
                        temporary.toPath()
                );
            } catch (IOException ignored) {
                // Rien d'autre à faire.
            }

            return false;
        }
    }

    private String readUtf8(File file) {
        if (file == null || !file.exists()) {
            return null;
        }

        try {
            byte[] bytes =
                    Files.readAllBytes(
                            file.toPath()
                    );

            return new String(
                    bytes,
                    StandardCharsets.UTF_8
            );
        } catch (Exception exception) {
            plugin.getLogger().severe(
                    "Erreur de lecture "
                            + file.getName()
                            + ": "
                            + exception.getMessage()
            );
            return null;
        }
    }

    private static void ensureDirectory(File folder) {
        if (folder.exists()) {
            if (!folder.isDirectory()) {
                throw new IllegalStateException(
                        "Not a directory: "
                                + folder.getAbsolutePath()
                );
            }
            return;
        }

        if (!folder.mkdirs() && !folder.isDirectory()) {
            throw new IllegalStateException(
                    "Cannot create directory: "
                            + folder.getAbsolutePath()
            );
        }
    }

    private static boolean isSafeEntityId(
            String entityId
    ) {
        if (entityId == null
                || entityId.isEmpty()) {
            return false;
        }

        for (int i = 0;
                i < entityId.length();
                i++) {
            char character =
                    entityId.charAt(i);

            boolean valid =
                    character >= 'a'
                            && character <= 'z'
                    || character >= 'A'
                            && character <= 'Z'
                    || character >= '0'
                            && character <= '9'
                    || character == '-'
                    || character == '_';

            if (!valid) {
                return false;
            }
        }

        return true;
    }

    @Override
    public String getType() {
        return "flatfile";
    }

    @Override
    public boolean isConnected() {
        return connected;
    }
}
