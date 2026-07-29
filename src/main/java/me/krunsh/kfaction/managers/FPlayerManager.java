package me.krunsh.kfaction.managers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;

/**
 * Gestionnaire des joueurs de faction (FPlayer) — optimisé pour 1000+ joueurs
 * 
 * Optimisations:
 * - Index faction→joueurs (O(1) au lieu de O(n) scan complet)
 * - Index nom→UUID (O(1) au lieu de O(n) scan complet)
 * - Config power cachée (évite les appels à getDouble sur chaque nouveau joueur)
 */
public class FPlayerManager {
    
    private final Kfaction plugin;
    
    // Cache principal des FPlayers par UUID
    private final Map<UUID, FPlayer> playersCache;
    
    // Index: factionId → Set<UUID> — O(1) lookup des membres d'une faction
    private final Map<String, Set<UUID>> factionPlayerIndex;
    
    // Index: nom (lowercase) → UUID — O(1) lookup par nom
    private final Map<String, UUID> nameIndex;
    
    // Config cachée pour éviter des appels à getDouble sur chaque nouveau joueur
    private double cachedStartPower = 10.0;
    private double cachedMaxPower = 10.0;
    
    public FPlayerManager(Kfaction plugin) {
        this.plugin = plugin;
        this.playersCache = new ConcurrentHashMap<>();
        this.factionPlayerIndex = new ConcurrentHashMap<>();
        this.nameIndex = new ConcurrentHashMap<>();
    }
    
    /**
     * Initialise le manager et cache la config power
     */
    public void initialize() {
        cachedStartPower = plugin.getConfigManager().getDouble("power.start", 10.0);
        cachedMaxPower = plugin.getConfigManager().getDouble("power.max", 10.0);
        plugin.getLogger().info("FPlayerManager initialisé");
    }
    
    /**
     * Ferme le manager
     */
    public void shutdown() {
        playersCache.clear();
        factionPlayerIndex.clear();
        nameIndex.clear();
    }
    
    // === Opérations de base ===
    
    /**
     * Obtient ou crée un FPlayer pour un UUID
     * @param uuid UUID du joueur
     * @return Le FPlayer (jamais null)
     */
    public FPlayer getFPlayer(UUID uuid) {
        if (uuid == null) return null;
        
        return playersCache.computeIfAbsent(uuid, id -> {
            FPlayer fPlayer = new FPlayer(id);
            initializeNewPlayer(fPlayer);
            return fPlayer;
        });
    }
    
    /**
     * Obtient ou crée un FPlayer pour un joueur Bukkit
     * @param player Le joueur
     * @return Le FPlayer (jamais null)
     */
    public FPlayer getFPlayer(Player player) {
        if (player == null) return null;
        FPlayer fPlayer = getFPlayer(player.getUniqueId());
        String oldName = fPlayer.getLastKnownName();
        String newName = player.getName();
        fPlayer.setLastKnownName(newName);
        // Mettre à jour l'index de noms si changé
        if (!newName.equals(oldName)) {
            if (oldName != null && !oldName.isEmpty()) {
                nameIndex.remove(oldName.toLowerCase());
            }
            nameIndex.put(newName.toLowerCase(), player.getUniqueId());
        }
        return fPlayer;
    }
    
    /**
     * Alias pour getFPlayer
     */
    public FPlayer get(Player player) {
        return getFPlayer(player);
    }
    
    /**
     * Alias pour getFPlayer
     */
    public FPlayer getOrCreate(UUID uuid) {
        return getFPlayer(uuid);
    }
    
    /**
     * Vérifie si un FPlayer est en cache
     */
    public boolean isLoaded(UUID uuid) {
        return playersCache.containsKey(uuid);
    }
    
    /**
     * Obtient tous les FPlayers en cache
     */
    public Collection<FPlayer> getAllPlayers() {
        return Collections.unmodifiableCollection(playersCache.values());
    }
    
    /**
     * Obtient tous les FPlayers d'une faction — O(1) via index
     */
    public List<FPlayer> getPlayersInFaction(String factionId) {
        Set<UUID> memberUuids = factionPlayerIndex.get(factionId);
        if (memberUuids == null || memberUuids.isEmpty()) {
            return Collections.emptyList();
        }
        List<FPlayer> result = new ArrayList<>(memberUuids.size());
        for (UUID uuid : memberUuids) {
            FPlayer fp = playersCache.get(uuid);
            if (fp != null) {
                result.add(fp);
            }
        }
        return result;
    }
    
    /**
     * Obtient les joueurs en ligne d'une faction — O(k) k=membres de la faction
     */
    public List<FPlayer> getOnlinePlayersInFaction(String factionId) {
        Set<UUID> memberUuids = factionPlayerIndex.get(factionId);
        if (memberUuids == null || memberUuids.isEmpty()) {
            return Collections.emptyList();
        }
        List<FPlayer> result = new ArrayList<>();
        for (UUID uuid : memberUuids) {
            FPlayer fp = playersCache.get(uuid);
            if (fp != null && fp.isOnline()) {
                result.add(fp);
            }
        }
        return result;
    }
    
    // === Index management ===
    
    /**
     * Notifie un changement de faction pour un joueur.
     * Met à jour l'index factionId→UUID.
     * DOIT être appelé chaque fois que le factionId d'un FPlayer change.
     *
     * @param uuid UUID du joueur
     * @param oldFactionId ancien factionId (peut être null)
     * @param newFactionId nouveau factionId (peut être null)
     */
    public void notifyFactionChange(UUID uuid, String oldFactionId, String newFactionId) {
        if (oldFactionId != null) {
            Set<UUID> oldSet = factionPlayerIndex.get(oldFactionId);
            if (oldSet != null) {
                oldSet.remove(uuid);
                if (oldSet.isEmpty()) {
                    factionPlayerIndex.remove(oldFactionId);
                }
            }
        }
        if (newFactionId != null) {
            factionPlayerIndex.computeIfAbsent(newFactionId, 
                k -> ConcurrentHashMap.newKeySet()).add(uuid);
        }
    }
    
    // === Initialisation et configuration ===
    
    /**
     * Initialise un nouveau joueur avec les valeurs de config cachées
     */
    private void initializeNewPlayer(FPlayer fPlayer) {
        fPlayer.setPower(cachedStartPower);
        fPlayer.setMaxPower(cachedMaxPower);
        fPlayer.setFirstJoin(System.currentTimeMillis());
        fPlayer.updateLastSeen();
    }
    
    // === Événements joueur ===
    
    /**
     * Appelé quand un joueur se connecte
     */
    public FPlayer onPlayerJoin(Player player) {
        FPlayer fPlayer = getFPlayer(player);
        fPlayer.updateLastSeen();
        fPlayer.updateName();
        
        // Mettre à jour l'index de noms
        String name = player.getName();
        if (name != null && !name.isEmpty()) {
            nameIndex.put(name.toLowerCase(), player.getUniqueId());
        }
        
        // Vérifier s'il était déjà dans une faction pour mettre à jour l'activité
        if (fPlayer.hasFaction()) {
            Faction faction = plugin.getFactionManager().getFaction(fPlayer.getFactionId());
            if (faction != null) {
                faction.updateActivity();
            }
        }
        
        return fPlayer;
    }
    
    /**
     * Appelé quand un joueur se déconnecte
     */
    public void onPlayerQuit(Player player) {
        FPlayer fPlayer = playersCache.get(player.getUniqueId());
        if (fPlayer != null) {
            fPlayer.updateLastSeen();
            fPlayer.setAutoClaimEnabled(false);
            fPlayer.setMapAutoUpdateEnabled(false);
            fPlayer.setSeeingChunks(false);
            
            plugin.getStorageManager().markDirty(fPlayer);
        }
    }
    
    // === Chargement depuis stockage ===
    
    /**
     * Charge un FPlayer dans le cache et met à jour les index
     */
    public void loadFPlayer(FPlayer fPlayer) {
        if (fPlayer == null) return;
        // Migration des anciennes valeurs persistées : power.max est la base
        // canonique par membre, les bonus de permission restent dynamiques.
        fPlayer.setMaxPower(cachedMaxPower);
        playersCache.put(fPlayer.getUuid(), fPlayer);
        
        // Indexer par faction
        if (fPlayer.hasFaction()) {
            factionPlayerIndex.computeIfAbsent(fPlayer.getFactionId(), 
                k -> ConcurrentHashMap.newKeySet()).add(fPlayer.getUuid());
        }
        
        // Indexer par nom
        String name = fPlayer.getLastKnownName();
        if (name != null && !name.isEmpty()) {
            nameIndex.put(name.toLowerCase(), fPlayer.getUuid());
        }
    }
    
    /**
     * Décharge un FPlayer du cache et nettoie les index
     */
    public void unloadFPlayer(UUID uuid) {
        FPlayer fPlayer = playersCache.remove(uuid);
        if (fPlayer != null) {
            // Nettoyer l'index faction
            if (fPlayer.hasFaction()) {
                Set<UUID> set = factionPlayerIndex.get(fPlayer.getFactionId());
                if (set != null) {
                    set.remove(uuid);
                }
            }
            // Nettoyer l'index nom
            String name = fPlayer.getLastKnownName();
            if (name != null && !name.isEmpty()) {
                nameIndex.remove(name.toLowerCase());
            }
        }
    }
    
    /**
     * Recherche un FPlayer par nom (insensible à la casse) — O(1) via index
     */
    public FPlayer getByName(String name) {
        if (name == null) return null;
        UUID uuid = nameIndex.get(name.toLowerCase());
        return uuid != null ? playersCache.get(uuid) : null;
    }
    
    /**
     * @return Nombre de joueurs en cache
     */
    public int getCacheSize() {
        return playersCache.size();
    }
    
    /**
     * Sauvegarde tous les joueurs modifiés
     */
    public void saveAll() {
        for (FPlayer fPlayer : playersCache.values()) {
            plugin.getStorageManager().markDirty(fPlayer);
        }
    }
}
