package me.krunsh.kfaction;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import me.krunsh.kfaction.api.KfactionAPI;
import me.krunsh.kfaction.commands.KfactionCommand;
import me.krunsh.kfaction.hooks.HookManager;
import me.krunsh.kfaction.listeners.AntiSethomeListener;
import me.krunsh.kfaction.listeners.CombatListener;
import me.krunsh.kfaction.listeners.FlyListener;
import me.krunsh.kfaction.listeners.PlayerConnectionListener;
import me.krunsh.kfaction.listeners.ProtectionListener;
import me.krunsh.kfaction.listeners.QuestListener;
import me.krunsh.kfaction.listeners.KcraftQuestBridge;
import me.krunsh.kfaction.listeners.KSpawnerQuestBridge;
import me.krunsh.kfaction.listeners.KMineraiQuestBridge;
import me.krunsh.kfaction.listeners.ShopGuiPlusQuestListener;
import me.krunsh.kfaction.listeners.TerritoryListener;
import me.krunsh.kfaction.managers.ClaimManager;
import me.krunsh.kfaction.managers.ConfigManager;
import me.krunsh.kfaction.managers.EconomyManager;
import me.krunsh.kfaction.managers.FPlayerManager;
import me.krunsh.kfaction.managers.FactionChestManager;
import me.krunsh.kfaction.managers.FactionManager;
import me.krunsh.kfaction.managers.LevelManager;
import me.krunsh.kfaction.managers.LogManager;
import me.krunsh.kfaction.managers.MapManager;
import me.krunsh.kfaction.managers.MessageManager;
import me.krunsh.kfaction.managers.PermissionManager;
import me.krunsh.kfaction.managers.PowerManager;
import me.krunsh.kfaction.managers.QuestManager;
import me.krunsh.kfaction.managers.RelationManager;
import me.krunsh.kfaction.managers.RewardManager;
import me.krunsh.kfaction.managers.StorageManager;
import me.krunsh.kfaction.managers.TerritoryManager;
import me.krunsh.kfaction.progression.PlacedBlockTracker;

/**
 * Kfaction - Plugin Factions moderne pour Minecraft 1.8.8
 * 
 * Plugin de factions amélioré avec intégration complète dans l'écosystème K:
 * - Kcore: Combat, cooldowns
 * - Kchat: Chat faction, nametags, tags
 * - Kgui: Menus configurables
 * - Kclassement: F-Top
 * 
 * @author Krunsh
 * @version 1.0.0
 */
public class Kfaction extends JavaPlugin {

    private static Kfaction instance;
    
    // ===== MANAGERS =====
    private ConfigManager configManager;
    private MessageManager messageManager;
    private StorageManager storageManager;
    private FactionManager factionManager;
    private FPlayerManager fPlayerManager;
    private ClaimManager claimManager;
    private PowerManager powerManager;
    private RelationManager relationManager;
    private PermissionManager permissionManager;
    private TerritoryManager territoryManager;
    private EconomyManager economyManager;
    private MapManager mapManager;
    private LogManager logManager;
    private LevelManager levelManager;
    private QuestManager questManager;
    private RewardManager rewardManager;
    private FactionChestManager factionChestManager;
    private PlacedBlockTracker placedBlockTracker;
    
    // ===== LISTENERS =====
    private TerritoryListener territoryListener;
    private FlyListener flyListener;
    
    // ===== HOOKS =====
    private HookManager hookManager;
    
    // ===== API =====
    private KfactionAPI api;
    
    // ===== DEBUG =====
    private boolean debugMode = false;

    @Override
    public void onEnable() {
        instance = this;
        
        logInfo("&6===========================================");
        logInfo("&6  Kfaction v" + getDescription().getVersion());
        logInfo("&6  Plugin Factions Moderne - Écosystème K");
        logInfo("&6===========================================");
        
        // 1. Charger la configuration
        initConfig();
        
        // 2. Initialiser les hooks
        initHooks();
        
        // 3. Initialiser le storage
        initStorage();
        
        // 4. Initialiser les managers
        initManagers();
        
        // 5. Charger les données
        loadData();
        
        // 6. Enregistrer les listeners
        registerListeners();
        
        // 7. Enregistrer les commandes
        registerCommands();
        
        // 8. Enregistrer PlaceholderAPI
        registerPlaceholders();
        
        // 9. Démarrer les tasks
        startTasks();
        
        // 10. Initialiser l'API
        api = new KfactionAPI(this);
        
        // 11. Enregistrer les ContentProviders Kgui
        registerKguiProviders();
        
        logInfo("&aKfaction activé avec succès!");
    }

    @Override
    public void onDisable() {
        logInfo("&6Désactivation de Kfaction...");
        
        // Arrêter le power regen task (évite le task leak sur /reload)
        if (powerManager != null) {
            powerManager.shutdown();
        }
        
        // Flush les logs en attente sur disque
        if (logManager != null) {
            logManager.shutdown();
        }

        if (placedBlockTracker != null) {
            placedBlockTracker.shutdown();
        }
        
        // Sauvegarder toutes les données de manière synchrone
        if (storageManager != null) {
            logInfo("&7Sauvegarde des données...");
            storageManager.shutdown();
        }
        
        // Nettoyer les caches mémoire
        if (fPlayerManager != null) {
            fPlayerManager.shutdown();
        }
        if (claimManager != null) {
            claimManager.shutdown();
        }
        
        // Sauvegarder les coffres faction
        if (factionChestManager != null) {
            factionChestManager.saveAll();
        }
        
        // Désactiver le fly de tous les joueurs
        if (flyListener != null) {
            flyListener.disableAll();
        }
        
        logInfo("&cKfaction désactivé.");
        instance = null;
    }

    // ==================== INITIALISATION ====================
    
    private void initConfig() {
        saveDefaultConfig();
        configManager = new ConfigManager(this);
        messageManager = new MessageManager(this);
        messageManager.initialize(); // Charger messages.yml
        debugMode = configManager.getBoolean("debug", false);
        
        logInfo("&7Configuration chargée");
    }
    
    private void initHooks() {
        hookManager = new HookManager(this);
        hookManager.initialize();
    }
    
    private void initStorage() {
        storageManager = new StorageManager(this);
        storageManager.initialize();
    }
    
    private void initManagers() {
        // Ordre important - certains managers dépendent d'autres
        factionManager = new FactionManager(this);
        fPlayerManager = new FPlayerManager(this);
        claimManager = new ClaimManager(this);
        powerManager = new PowerManager(this);
        relationManager = new RelationManager(this);
        permissionManager = new PermissionManager(this);
        territoryManager = new TerritoryManager(this);
        economyManager = new EconomyManager(this);
        mapManager = new MapManager(this);
        mapManager.initialize();
        logManager = new LogManager(this);
        levelManager = new LevelManager(this);
        questManager = new QuestManager(this);
        rewardManager = new RewardManager(this);
        factionChestManager = new FactionChestManager(this);
        
        // Initialiser tous les managers
        factionManager.initialize();
        fPlayerManager.initialize();
        claimManager.initialize();
        powerManager.initialize();
        territoryManager.initialize();
        logManager.initialize();
        levelManager.loadConfig();
        questManager.loadConfig();
        placedBlockTracker = new PlacedBlockTracker(this);
        placedBlockTracker.initialize();
        
        logInfo("&7Managers initialisés (+ level system)");
    }
    
    private void loadData() {
        logInfo("&7Chargement des données...");
        
        // Charger toutes les données via StorageManager
        storageManager.loadAll();

        // Le candidat progression.yml est validé avant le chargement. La
        // migration ne démarre qu'une fois toutes les factions disponibles.
        if (questManager != null) {
            questManager.migrateLoadedFactions();
        }

        // Reconstruire l'index des claims après chargement des factions
        claimManager.rebuildClaimIndex();
        
        logInfo("&7Données chargées");
        // Les joueurs sont chargés à la demande (lazy loading)
    }
    
    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new PlayerConnectionListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ProtectionListener(this), this);
        Bukkit.getPluginManager().registerEvents(new CombatListener(this), this);
        territoryListener = new TerritoryListener(this);
        territoryListener.loadConfig();
        Bukkit.getPluginManager().registerEvents(territoryListener, this);
        me.krunsh.kfaction.listeners.ExploitProtectionListener exploitListener = new me.krunsh.kfaction.listeners.ExploitProtectionListener(this);
        exploitListener.loadConfig();
        Bukkit.getPluginManager().registerEvents(exploitListener, this);
        
        // Chat faction listener (intercepte AVANT Kchat)
        me.krunsh.kfaction.listeners.FactionChatListener chatListener = new me.krunsh.kfaction.listeners.FactionChatListener(this);
        chatListener.loadConfig();
        Bukkit.getPluginManager().registerEvents(chatListener, this);
        
        // Level system listeners
        Bukkit.getPluginManager().registerEvents(new QuestListener(this), this);
        if (new me.krunsh.kfaction.listeners.VanillaCraftQuestBridge(this).register()) {
            logInfo("&7Quêtes CRAFT connectées aux transactions vanilla du fork");
        }
        if (new me.krunsh.kfaction.listeners.BreedQuestBridge(this).register()) {
            logInfo("&7Quêtes BREED connectées aux reproductions validées du fork");
        }
        if (new KcraftQuestBridge(this).register()) {
            logInfo("&7Quêtes CUSTOM_CRAFT connectées à KCraft");
        }
        if (new KSpawnerQuestBridge(this).register()) {
            logInfo("&7Quêtes SPAWNER_PLACE/BREAK connectées à KSpawner");
        }
        if (new KMineraiQuestBridge(this).register()) {
            logInfo("&7Quêtes CUSTOM_ORE_MINE connectées à Kminerai");
        }
        if (Bukkit.getPluginManager().isPluginEnabled("ShopGUIPlus")) {
            Bukkit.getPluginManager().registerEvents(new ShopGuiPlusQuestListener(this), this);
            logInfo("&7Quetes ITEM_SELL connectees a ShopGUIPlus (transactions reussies)");
        } else {
            logInfo("&eShopGUIPlus absent: les quetes ITEM_SELL attendent l'API externe");
        }
        flyListener = new FlyListener(this);
        flyListener.loadConfig();
        Bukkit.getPluginManager().registerEvents(flyListener, this);
        AntiSethomeListener antiSethomeListener = new AntiSethomeListener(this);
        antiSethomeListener.loadConfig();
        Bukkit.getPluginManager().registerEvents(antiSethomeListener, this);
        
        logInfo("&7Listeners enregistrés (+ anti-exploit + chat faction + level system)");
    }
    
    private void registerCommands() {
        // Commande joueur /f, /faction, /fac
        KfactionCommand playerCommand = new KfactionCommand(this);
        getCommand("f").setExecutor(playerCommand);
        getCommand("f").setTabCompleter(playerCommand);
        
        // Commande admin /kfaction, /kf
        me.krunsh.kfaction.commands.KfactionAdminCommand adminCommand = 
            new me.krunsh.kfaction.commands.KfactionAdminCommand(this);
        getCommand("kfaction").setExecutor(adminCommand);
        getCommand("kfaction").setTabCompleter(adminCommand);
        
        logInfo("&7Commandes enregistrées (joueur: /f, admin: /kfaction)");
    }
    
    private void registerPlaceholders() {
        // PlaceholderAPI expansion enregistrée si disponible
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new me.krunsh.kfaction.placeholders.KfactionExpansion(this).register();
            logInfo("&7PlaceholderAPI expansion enregistrée");
        }
    }
    
    private void registerKguiProviders() {
        // Enregistrer les ContentProviders dans Kgui (quêtes, récompenses)
        if (Bukkit.getPluginManager().getPlugin("Kgui") != null) {
            new me.krunsh.kfaction.hooks.KguiContentProviders(this).register();
        }
    }
    
    private void startTasks() {
        // Power regen est géré par PowerManager.initialize()
        // qui démarre sa propre tâche de régénération
        
        // Sauvegarde automatique (intervalle configurable)
        long autoSaveInterval = getConfig().getLong("auto-save-interval", 300);
        long autoSaveTicks = 20L * autoSaveInterval;
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            storageManager.saveAsync();
        }, autoSaveTicks, autoSaveTicks);
        
        // Task d'inactivité (vérifie et transfère le leadership)
        new me.krunsh.kfaction.tasks.InactivityTask(this).start();
        
        logInfo("&7Tasks démarrées");
    }

    // ==================== LOGGING ====================
    
    public void logInfo(String message) {
        getLogger().info(colorize(message));
    }
    
    public void logError(String message) {
        getLogger().severe(colorize(message));
    }
    
    public void debug(String message) {
        if (debugMode) {
            getLogger().info(colorize("&8[DEBUG] &7" + message));
        }
    }
    
    private String colorize(String message) {
        return message.replace("&", "§");
    }

    // ==================== GETTERS ====================
    
    public static Kfaction getInstance() {
        return instance;
    }
    
    public ConfigManager getConfigManager() { return configManager; }
    public MessageManager getMessageManager() { return messageManager; }
    public StorageManager getStorageManager() { return storageManager; }
    public FactionManager getFactionManager() { return factionManager; }
    public FPlayerManager getFPlayerManager() { return fPlayerManager; }
    public ClaimManager getClaimManager() { return claimManager; }
    public PowerManager getPowerManager() { return powerManager; }
    public RelationManager getRelationManager() { return relationManager; }
    public PermissionManager getPermissionManager() { return permissionManager; }
    public TerritoryManager getTerritoryManager() { return territoryManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public MapManager getMapManager() { return mapManager; }
    public LogManager getLogManager() { return logManager; }
    public LevelManager getLevelManager() { return levelManager; }
    public QuestManager getQuestManager() { return questManager; }
    public RewardManager getRewardManager() { return rewardManager; }
    public FactionChestManager getFactionChestManager() { return factionChestManager; }
    public PlacedBlockTracker getPlacedBlockTracker() { return placedBlockTracker; }
    public FlyListener getFlyListener() { return flyListener; }
    public HookManager getHookManager() { return hookManager; }
    public KfactionAPI getAPI() { return api; }
    
    public boolean isDebugMode() { return debugMode; }
    
    // === Admin Bypass ===
    
    private final java.util.Set<java.util.UUID> bypassingPlayers = new java.util.HashSet<>();
    
    /**
     * Bascule le mode bypass admin pour un joueur
     * @param playerId UUID du joueur
     */
    public void toggleBypass(java.util.UUID playerId) {
        if (bypassingPlayers.contains(playerId)) {
            bypassingPlayers.remove(playerId);
        } else {
            bypassingPlayers.add(playerId);
        }
        // Mettre à jour le FPlayer aussi
        me.krunsh.kfaction.data.FPlayer fPlayer = fPlayerManager.getFPlayer(playerId);
        if (fPlayer != null) {
            fPlayer.setBypassing(bypassingPlayers.contains(playerId));
        }
    }
    
    /**
     * Vérifie si un joueur est en mode bypass
     * @param playerId UUID du joueur
     * @return true si en bypass
     */
    public boolean isBypassing(java.util.UUID playerId) {
        return bypassingPlayers.contains(playerId);
    }
    
    /**
     * Recharge la configuration du plugin
     */
    public void reload() {
        // Recharger la config principale
        configManager.reload();
        
        // Recharger les messages
        messageManager.reload();
        
        // Recharger la config des managers
        relationManager.loadConfig();
        
        // levels.yml/quests.yml restent uniquement disponibles pour migration
        // et rollback. progression.yml remplace son snapshot de façon sûre.
        if (questManager != null) questManager.loadConfig();

        // Relire les paramètres de la carte (width, height, symboles, couleurs)
        if (mapManager != null) mapManager.initialize();
        
        debugMode = configManager.getBoolean("debug", false);
        
        logInfo("&aConfiguration rechargée!");
    }
}
