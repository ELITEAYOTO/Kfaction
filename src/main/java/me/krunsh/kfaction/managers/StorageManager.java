package me.krunsh.kfaction.managers;

import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.bukkit.scheduler.BukkitTask;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.storage.FlatFileStorage;
import me.krunsh.kfaction.storage.Storage;

/**
 * Gestionnaire du stockage persistant
 * Gère la sauvegarde asynchrone et le système de dirty-tracking
 */
public class StorageManager {
    
    private final Kfaction plugin;
    
    // Backend de stockage
    private Storage storage;
    
    // Dirty tracking pour sauvegarde différée
    private final Set<String> dirtyFactions;
    private final Set<String> dirtyPlayers;
    
    // Queue des suppressions
    private final Queue<String> factionDeletionQueue;
    private final Queue<String> playerDeletionQueue;
    
    // Tâche de sauvegarde automatique
    private BukkitTask autoSaveTask;
    
    // Intervalle de sauvegarde en ticks (20 ticks = 1 seconde)
    private int saveInterval;
    
    public StorageManager(Kfaction plugin) {
        this.plugin = plugin;
        this.dirtyFactions = ConcurrentHashMap.newKeySet();
        this.dirtyPlayers = ConcurrentHashMap.newKeySet();
        this.factionDeletionQueue = new ConcurrentLinkedQueue<>();
        this.playerDeletionQueue = new ConcurrentLinkedQueue<>();
    }
    
    /**
     * Initialise le stockage
     */
    public void initialize() {
        // Charger la configuration
        String storageType = plugin.getConfigManager().getString("storage.type", "flatfile");
        saveInterval = plugin.getConfigManager().getInt("storage.auto-save-interval", 300) * 20;
        
        // Initialiser le backend de stockage
        if (storageType.equalsIgnoreCase("mysql")) {
            // TODO: Implémenter MySQLStorage
            plugin.getLogger().warning("MySQL non implémenté, utilisation de flatfile");
            storage = new FlatFileStorage(plugin);
        } else {
            storage = new FlatFileStorage(plugin);
        }
        
        // Initialiser le stockage
        storage.initialize();
        
        // NOTE: loadAll() est appelé dans loadData() après initManagers()
        // pour éviter les NPE (les managers doivent être initialisés d'abord)
        
        // Démarrer l'auto-save
        startAutoSave();
        
        plugin.getLogger().info("StorageManager initialisé (type: " + storageType + ")");
    }
    
    /**
     * Ferme le stockage et sauvegarde tout
     */
    public void shutdown() {
        // Arrêter l'auto-save
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }
        
        // Sauvegarder tout de manière synchrone
        saveAllSync();
        
        // Fermer le stockage
        if (storage != null) {
            storage.shutdown();
        }
        
        dirtyFactions.clear();
        dirtyPlayers.clear();
    }
    
    // === Dirty tracking ===
    
    /**
     * Marque une faction comme modifiée
     * @param faction La faction modifiée
     */
    public void markDirty(Faction faction) {
        if (faction != null && !faction.isSystemFaction()) {
            dirtyFactions.add(faction.getId());
        }
    }
    
    /**
     * Marque un FPlayer comme modifié
     * @param fPlayer Le FPlayer modifié
     */
    public void markDirty(FPlayer fPlayer) {
        if (fPlayer != null) {
            dirtyPlayers.add(fPlayer.getUuid().toString());
        }
    }
    
    // === Chargement ===
    
    /**
     * Charge toutes les données
     */
    public void loadAll() {
        // Protection si managers non initialisés
        if (plugin.getFactionManager() == null || plugin.getClaimManager() == null) {
            plugin.getLogger().severe("Impossible de charger les données: managers non initialisés!");
            return;
        }
        
        // Charger les factions
        storage.loadFactions(faction -> {
            plugin.getFactionManager().loadFaction(faction);
        });
        
        // Reconstruire l'index de claims
        plugin.getClaimManager().rebuildIndex();
        
        // TODO: Charger les FPlayers à la demande ou au login
        
        plugin.getLogger().info("Données chargées avec succès");
    }
    
    // === Sauvegarde ===
    
    /**
     * Sauvegarde les entités modifiées de manière asynchrone
     */
    public void saveAsync() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::saveModified);
    }
    
    /**
     * Sauvegarde les entités modifiées
     */
    private void saveModified() {
        // Traiter les suppressions d'abord
        String factionId;
        while ((factionId = factionDeletionQueue.poll()) != null) {
            storage.deleteFaction(factionId);
        }
        
        String playerId;
        while ((playerId = playerDeletionQueue.poll()) != null) {
            storage.deleteFPlayer(playerId);
        }
        
        // Sauvegarder les factions dirty (drain pattern — ne perd aucun ajout concurrent)
        Iterator<String> factionIt = dirtyFactions.iterator();
        while (factionIt.hasNext()) {
            String id = factionIt.next();
            factionIt.remove();
            Faction faction = plugin.getFactionManager().getFaction(id);
            if (faction != null) {
                storage.saveFaction(faction);
            }
        }
        
        // Sauvegarder les FPlayers dirty (drain pattern)
        Iterator<String> playerIt = dirtyPlayers.iterator();
        while (playerIt.hasNext()) {
            String uuid = playerIt.next();
            playerIt.remove();
            FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(
                java.util.UUID.fromString(uuid));
            if (fPlayer != null) {
                storage.saveFPlayer(fPlayer);
            }
        }
    }
    
    /**
     * Sauvegarde toutes les données de manière synchrone
     */
    public void saveAllSync() {
        // Protection si managers non initialisés
        if (plugin.getFactionManager() == null || plugin.getFPlayerManager() == null) {
            return;
        }
        
        // Marquer tout comme dirty
        for (Faction faction : plugin.getFactionManager().getAllFactions()) {
            if (!faction.isSystemFaction()) {
                dirtyFactions.add(faction.getId());
            }
        }
        
        for (FPlayer fPlayer : plugin.getFPlayerManager().getAllPlayers()) {
            dirtyPlayers.add(fPlayer.getUuid().toString());
        }
        
        // Sauvegarder
        saveModified();
    }

    /**
     * Point de persistance synchrone pour les transitions de progression.
     * La faction est retirée de la file dirty uniquement après confirmation.
     */
    public boolean saveFactionNow(Faction faction) {
        if (storage == null || faction == null || faction.isSystemFaction()) {
            return false;
        }
        boolean saved = storage.saveFactionChecked(faction);
        if (saved) {
            dirtyFactions.remove(faction.getId());
        } else {
            dirtyFactions.add(faction.getId());
        }
        return saved;
    }
    
    /**
     * Démarre la tâche d'auto-sauvegarde
     */
    private void startAutoSave() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
        }
        
        if (saveInterval > 0) {
            autoSaveTask = plugin.getServer().getScheduler()
                .runTaskTimerAsynchronously(plugin, this::saveModified, 
                    saveInterval, saveInterval);
        }
    }
    
    // === Suppressions ===
    
    /**
     * Supprime une faction du stockage
     * @param factionId ID de la faction
     */
    public void deleteFaction(String factionId) {
        dirtyFactions.remove(factionId);
        factionDeletionQueue.add(factionId);
    }
    
    /**
     * Supprime un FPlayer du stockage
     * @param playerId UUID du joueur (en string)
     */
    public void deleteFPlayer(String playerId) {
        dirtyPlayers.remove(playerId);
        playerDeletionQueue.add(playerId);
    }
    
    // === Accès au backend ===
    
    /**
     * @return Le backend de stockage
     */
    public Storage getStorage() {
        return storage;
    }
    
    /**
     * @return Nombre de factions en attente de sauvegarde
     */
    public int getDirtyFactionCount() {
        return dirtyFactions.size();
    }
    
    /**
     * @return Nombre de joueurs en attente de sauvegarde
     */
    public int getDirtyPlayerCount() {
        return dirtyPlayers.size();
    }
}
