package me.krunsh.kfaction.managers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.storage.Storage;

/**
 * Gestionnaire des FPlayer.
 *
 * Transition V2 :
 *
 * - findLoaded(...) : lecture mémoire pure, ne charge et ne crée rien.
 * - find(...)       : cherche mémoire puis stockage, ne crée jamais.
 * - getOrCreate(...) : charge si possible puis crée explicitement si absent.
 *
 * Les anciennes méthodes getFPlayer(...) sont conservées temporairement pour
 * compatibilité, mais elles sont désormais clairement des alias get-or-create.
 */
public class FPlayerManager {

    private final Kfaction plugin;

    private final Map<UUID, FPlayer> playersCache;
    private final Map<String, Set<UUID>> factionPlayerIndex;
    private final Map<String, UUID> nameIndex;

    private double cachedStartPower = 10.0D;
    private double cachedMaxPower = 10.0D;

    public FPlayerManager(Kfaction plugin) {
        this.plugin = plugin;
        this.playersCache = new ConcurrentHashMap<>();
        this.factionPlayerIndex = new ConcurrentHashMap<>();
        this.nameIndex = new ConcurrentHashMap<>();
    }

    public void initialize() {
        reloadPowerSettings();
        plugin.getLogger().info("FPlayerManager initialisé");
    }

    /**
     * Recharge les valeurs de base power utilisées lors des créations/loads.
     *
     * Lot25D: power.max ne doit pas rester figé à la valeur du boot après
     * /kf reload. Les profils déjà chargés reçoivent également le nouveau
     * maximum de base; si le maximum baisse et tronque le power courant, le
     * profil est marqué dirty afin que la valeur persistée reste cohérente.
     */
    public void reloadPowerSettings() {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            new Runnable() {
                                @Override
                                public void run() {
                                    reloadPowerSettings();
                                }
                            }
                    );
            return;
        }

        double newStartPower =
                plugin.getConfigManager()
                        .getDouble(
                                "power.start",
                                10.0D
                        );

        double newMaxPower =
                plugin.getConfigManager()
                        .getDouble(
                                "power.max",
                                10.0D
                        );

        cachedStartPower = newStartPower;
        cachedMaxPower = newMaxPower;

        for (FPlayer fPlayer : getAllPlayers()) {
            if (fPlayer == null) {
                continue;
            }

            double beforePower = fPlayer.getPower();
            double beforeMax = fPlayer.getMaxPower();

            fPlayer.setMaxPower(newMaxPower);

            if (Double.compare(beforePower, fPlayer.getPower()) != 0
                    || Double.compare(beforeMax, newMaxPower) != 0) {
                plugin.getStorageManager()
                        .markDirty(
                                fPlayer
                        );
            }
        }
    }

    public void shutdown() {
        playersCache.clear();
        factionPlayerIndex.clear();
        nameIndex.clear();
    }

    // ============================================================
    // API V2 explicite
    // ============================================================

    /**
     * Lecture mémoire pure.
     *
     * @return le profil déjà chargé ou null.
     */
    public FPlayer findLoaded(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return playersCache.get(uuid);
    }

    /**
     * Recherche un profil existant.
     *
     * 1. cache mémoire ;
     * 2. stockage persistant ;
     * 3. null si le profil n'existe réellement pas.
     *
     * Cette méthode NE CRÉE JAMAIS de FPlayer.
     *
     * L'accès stockage est synchrone dans le backend V1. Il convient aux
     * commandes/login/outils staff, pas à une boucle hot-path.
     */
    public FPlayer find(UUID uuid) {
        if (uuid == null) {
            return null;
        }

        FPlayer cached = playersCache.get(uuid);
        if (cached != null) {
            return cached;
        }

        /*
         * Charger depuis le storage modifie ensuite le cache/index FPlayer.
         * Cette transition reste donc confinée au main thread.
         *
         * Hors main thread, find() devient une lecture cache-only.
         */
        if (!Bukkit.isPrimaryThread()) {
            return null;
        }

        Storage storage = getStorageSafely();
        if (storage == null) {
            return null;
        }

        FPlayer loaded = storage.loadFPlayer(uuid.toString());
        if (loaded == null) {
            return null;
        }

        loadFPlayer(loaded);
        return playersCache.get(uuid);
    }

    /**
     * Charge un profil existant, ou le crée explicitement s'il n'existe pas.
     */
    public FPlayer getOrCreate(UUID uuid) {
        if (uuid == null) {
            return null;
        }

        if (!Bukkit.isPrimaryThread()) {
            return findLoaded(uuid);
        }

        FPlayer existing = find(uuid);
        if (existing != null) {
            return existing;
        }

        FPlayer created = new FPlayer(uuid);
        initializeNewPlayer(created);

        FPlayer raced = playersCache.putIfAbsent(uuid, created);
        return raced != null ? raced : created;
    }

    /**
     * Variante Bukkit de getOrCreate avec mise à jour de l'index de nom.
     */
    public FPlayer getOrCreate(Player player) {
        if (player == null) {
            return null;
        }

        FPlayer fPlayer = getOrCreate(player.getUniqueId());
        updateKnownName(fPlayer, player.getName());
        return fPlayer;
    }

    /**
     * Recherche mémoire par nom.
     *
     * Le stockage V1 ne possède pas d'index nom global, donc cette méthode
     * n'effectue volontairement pas de scan disque.
     */
    public FPlayer findLoadedByName(String name) {
        if (name == null) {
            return null;
        }

        UUID uuid = nameIndex.get(name.toLowerCase(Locale.ROOT));
        return uuid != null ? playersCache.get(uuid) : null;
    }

    // ============================================================
    // Compatibilité V1
    // ============================================================

    /**
     * @deprecated ambigu : utiliser findLoaded, find ou getOrCreate.
     */
    @Deprecated
    public FPlayer getFPlayer(UUID uuid) {
        return getOrCreate(uuid);
    }

    /**
     * @deprecated ambigu : utiliser getOrCreate(Player).
     */
    @Deprecated
    public FPlayer getFPlayer(Player player) {
        return getOrCreate(player);
    }

    public FPlayer get(Player player) {
        return getOrCreate(player);
    }

    /**
     * Ancien alias conservé.
     */
    public FPlayer getOrCreatePlayer(UUID uuid) {
        return getOrCreate(uuid);
    }

    // ============================================================
    // État/cache
    // ============================================================

    public boolean isLoaded(UUID uuid) {
        return uuid != null && playersCache.containsKey(uuid);
    }

    public Collection<FPlayer> getAllPlayers() {
        return Collections.unmodifiableList(
                new ArrayList<FPlayer>(
                        playersCache.values()
                )
        );
    }

    public List<FPlayer> getPlayersInFaction(String factionId) {
        if (factionId == null) {
            return Collections.emptyList();
        }

        Set<UUID> memberUuids = factionPlayerIndex.get(factionId);
        if (memberUuids == null || memberUuids.isEmpty()) {
            return Collections.emptyList();
        }

        List<FPlayer> result = new ArrayList<>(memberUuids.size());
        for (UUID uuid : memberUuids) {
            FPlayer fPlayer = playersCache.get(uuid);
            if (fPlayer != null) {
                result.add(fPlayer);
            }
        }

        return result;
    }

    public List<FPlayer> getOnlinePlayersInFaction(String factionId) {
        if (factionId == null) {
            return Collections.emptyList();
        }

        Set<UUID> memberUuids = factionPlayerIndex.get(factionId);
        if (memberUuids == null || memberUuids.isEmpty()) {
            return Collections.emptyList();
        }

        List<FPlayer> result = new ArrayList<>();
        for (UUID uuid : memberUuids) {
            FPlayer fPlayer = playersCache.get(uuid);
            if (fPlayer != null && fPlayer.isOnline()) {
                result.add(fPlayer);
            }
        }

        return result;
    }

    // ============================================================
    // Index management
    // ============================================================

    public void notifyFactionChange(
            UUID uuid,
            String oldFactionId,
            String newFactionId
    ) {
        if (uuid == null) {
            return;
        }

        if (oldFactionId != null) {
            Set<UUID> oldSet = factionPlayerIndex.get(oldFactionId);
            if (oldSet != null) {
                oldSet.remove(uuid);
                if (oldSet.isEmpty()) {
                    factionPlayerIndex.remove(oldFactionId, oldSet);
                }
            }
        }

        if (newFactionId != null) {
            factionPlayerIndex
                    .computeIfAbsent(newFactionId, key -> ConcurrentHashMap.newKeySet())
                    .add(uuid);
        }
    }

    private void updateKnownName(FPlayer fPlayer, String newName) {
        if (fPlayer == null || newName == null || newName.isEmpty()) {
            return;
        }

        String oldName = fPlayer.getLastKnownName();

        if (oldName != null
                && !oldName.isEmpty()
                && !oldName.equalsIgnoreCase(newName)) {
            nameIndex.remove(oldName.toLowerCase(Locale.ROOT), fPlayer.getUuid());
        }

        fPlayer.setLastKnownName(newName);
        nameIndex.put(newName.toLowerCase(Locale.ROOT), fPlayer.getUuid());
    }

    private void removeIndexes(FPlayer fPlayer) {
        if (fPlayer == null) {
            return;
        }

        if (fPlayer.hasFaction()) {
            Set<UUID> set = factionPlayerIndex.get(fPlayer.getFactionId());
            if (set != null) {
                set.remove(fPlayer.getUuid());
                if (set.isEmpty()) {
                    factionPlayerIndex.remove(fPlayer.getFactionId(), set);
                }
            }
        }

        String name = fPlayer.getLastKnownName();
        if (name != null && !name.isEmpty()) {
            nameIndex.remove(name.toLowerCase(Locale.ROOT), fPlayer.getUuid());
        }
    }

    private void index(FPlayer fPlayer) {
        if (fPlayer.hasFaction()) {
            factionPlayerIndex
                    .computeIfAbsent(
                            fPlayer.getFactionId(),
                            key -> ConcurrentHashMap.newKeySet()
                    )
                    .add(fPlayer.getUuid());
        }

        String name = fPlayer.getLastKnownName();
        if (name != null && !name.isEmpty()) {
            nameIndex.put(name.toLowerCase(Locale.ROOT), fPlayer.getUuid());
        }
    }

    // ============================================================
    // Création / chargement
    // ============================================================

    private void initializeNewPlayer(FPlayer fPlayer) {
        fPlayer.setMaxPower(cachedMaxPower);
        fPlayer.setPower(cachedStartPower);
        fPlayer.setFirstJoin(System.currentTimeMillis());
        fPlayer.updateLastSeen();
    }

    /**
     * Charge/remplace un profil en cache et reconstruit ses index.
     */
    public void loadFPlayer(FPlayer fPlayer) {
        if (fPlayer == null || fPlayer.getUuid() == null) {
            return;
        }

        // power.max configuré reste la base canonique du serveur.
        fPlayer.setMaxPower(cachedMaxPower);

        FPlayer previous = playersCache.put(fPlayer.getUuid(), fPlayer);
        if (previous != null && previous != fPlayer) {
            removeIndexes(previous);
        }

        index(fPlayer);
    }

    public void unloadFPlayer(UUID uuid) {
        if (uuid == null) {
            return;
        }

        FPlayer fPlayer = playersCache.remove(uuid);
        removeIndexes(fPlayer);
    }

    // ============================================================
    // Connexion / déconnexion
    // ============================================================

    public FPlayer onPlayerJoin(Player player) {
        FPlayer fPlayer = getOrCreate(player);

        fPlayer.updateLastSeen();
        updateKnownName(fPlayer, player.getName());

        if (fPlayer.hasFaction()) {
            Faction faction = plugin.getFactionManager().getFaction(fPlayer.getFactionId());
            if (faction != null) {
                faction.updateActivity();
            }
        }

        return fPlayer;
    }

    public void onPlayerQuit(Player player) {
        if (player == null) {
            return;
        }

        FPlayer fPlayer = findLoaded(player.getUniqueId());
        if (fPlayer == null) {
            return;
        }

        fPlayer.updateLastSeen();
        fPlayer.setAutoClaimEnabled(false);
        fPlayer.setMapAutoUpdateEnabled(false);
        fPlayer.setSeeingChunks(false);

        plugin.getStorageManager().markDirty(fPlayer);
    }

    // ============================================================
    // Compatibilité noms / diagnostics
    // ============================================================

    /**
     * @deprecated utiliser findLoadedByName.
     */
    @Deprecated
    public FPlayer getByName(String name) {
        return findLoadedByName(name);
    }

    public int getCacheSize() {
        return playersCache.size();
    }

    public void saveAll() {
        for (FPlayer fPlayer : playersCache.values()) {
            plugin.getStorageManager().markDirty(fPlayer);
        }
    }

    private Storage getStorageSafely() {
        if (plugin.getStorageManager() == null) {
            return null;
        }
        return plugin.getStorageManager().getStorage();
    }
}
