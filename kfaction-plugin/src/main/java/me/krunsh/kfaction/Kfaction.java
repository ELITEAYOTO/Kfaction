package me.krunsh.kfaction;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Filter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import me.krunsh.kfaction.api.KfactionAPI;
import me.krunsh.kfaction.api.v2.KfactionApiV2;
import me.krunsh.kfaction.commands.KfactionCommand;
import me.krunsh.kfaction.hooks.HookManager;
import me.krunsh.kfaction.hooks.IntegrationState;
import me.krunsh.kfaction.hooks.IntegrationState.Status;
import me.krunsh.kfaction.listeners.AntiSethomeListener;
import me.krunsh.kfaction.listeners.CombatListener;
import me.krunsh.kfaction.listeners.FlyListener;
import me.krunsh.kfaction.listeners.ExploitProtectionListener;
import me.krunsh.kfaction.listeners.FactionChatListener;
import me.krunsh.kfaction.listeners.KMineraiQuestBridge;
import me.krunsh.kfaction.listeners.KSpawnerQuestBridge;
import me.krunsh.kfaction.listeners.KcraftQuestBridge;
import me.krunsh.kfaction.listeners.PlayerConnectionListener;
import me.krunsh.kfaction.listeners.ProtectionListener;
import me.krunsh.kfaction.listeners.QuestListener;
import me.krunsh.kfaction.listeners.ShopGuiPlusQuestBridge;
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
import me.krunsh.kfaction.storage.Storage;
import me.krunsh.kfaction.storage.StorageSnapshot;
import me.krunsh.kfaction.utils.KfactionLogger;

/**
 * Bootstrap principal Kfaction V2.
 *
 * Lot 25C:
 * - startup/shutdown compact en production;
 * - détails et timings complets uniquement avec debug=true;
 * - lifecycle V2 ré-aligné;
 * - autosave centralisé uniquement dans StorageManager;
 * - PlaceholderAPI possédé uniquement par HookManager;
 * - ordre de shutdown sécurisé pour le flush final.
 *
 * @author Krunsh
 */
public class Kfaction extends JavaPlugin {

    private static Kfaction instance;

    // ============================================================
    // Managers
    // ============================================================

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

    // ============================================================
    // Listeners
    // ============================================================

    private TerritoryListener territoryListener;
    private FlyListener flyListener;
    private ExploitProtectionListener exploitProtectionListener;
    private FactionChatListener factionChatListener;
    private AntiSethomeListener antiSethomeListener;

    // ============================================================
    // Integrations / API
    // ============================================================

    private HookManager hookManager;
    private KfactionAPI api;

    // ============================================================
    // Runtime
    // ============================================================

    private boolean debugMode;
    private boolean enableCompleted;

    private final Map<String, Long> startupTimings =
            new LinkedHashMap<String, Long>();

    private Filter previousLifecycleFilter;
    private boolean lifecycleFilterInstalled;

    // ============================================================
    // Bukkit lifecycle
    // ============================================================

    @Override
    public void onEnable() {
        instance = this;
        enableCompleted = false;

        long startedAt =
                System.nanoTime();

        try {
            timed(
                    "Configuration",
                    new StartupStep() {
                        @Override
                        public void run() {
                            initConfig();
                        }
                    }
            );

            timed(
                    "Hooks",
                    new StartupStep() {
                        @Override
                        public void run() {
                            initHooks();
                        }
                    }
            );

            timed(
                    "Storage",
                    new StartupStep() {
                        @Override
                        public void run() {
                            initStorage();
                        }
                    }
            );

            timed(
                    "Managers",
                    new StartupStep() {
                        @Override
                        public void run() {
                            initManagers();
                        }
                    }
            );

            timed(
                    "Données",
                    new StartupStep() {
                        @Override
                        public void run() {
                            loadData();
                        }
                    }
            );

            timed(
                    "Listeners",
                    new StartupStep() {
                        @Override
                        public void run() {
                            registerListeners();
                        }
                    }
            );

            timed(
                    "Commandes",
                    new StartupStep() {
                        @Override
                        public void run() {
                            registerCommands();
                        }
                    }
            );

            timed(
                    "Tasks",
                    new StartupStep() {
                        @Override
                        public void run() {
                            startTasks();
                        }
                    }
            );

            timed(
                    "API",
                    new StartupStep() {
                        @Override
                        public void run() {
                            api =
                                    new KfactionAPI(
                                            Kfaction.this
                                    );
                        }
                    }
            );

            enableCompleted = true;

            restoreLifecycleInfoGate();

            long elapsedMillis =
                    elapsedMillis(
                            startedAt
                    );

            try {
                logStartupSummary(
                        elapsedMillis
                );
            } catch (Throwable throwable) {
                KfactionLogger.warn(
                        this,
                        "Résumé de démarrage indisponible: "
                                + throwable.getClass()
                                        .getSimpleName()
                                + (throwable.getMessage() != null
                                        ? " — "
                                                + throwable.getMessage()
                                        : "")
                );
            }

            try {
                logDebugStartupDetails(
                        elapsedMillis
                );
            } catch (Throwable throwable) {
                KfactionLogger.debug(
                        this,
                        "Détails startup indisponibles: "
                                + throwable.getMessage()
                );
            }

        } catch (Throwable throwable) {
            restoreLifecycleInfoGate();

            KfactionLogger.error(
                    this,
                    "Démarrage impossible: "
                            + throwable.getClass()
                                    .getSimpleName()
                            + (throwable.getMessage() != null
                                    ? " — "
                                            + throwable.getMessage()
                                    : "")
            );

            getLogger().log(
                    Level.SEVERE,
                    "Stacktrace du bootstrap Kfaction",
                    throwable
            );

            /*
             * onDisable() est volontairement null-safe afin de pouvoir
             * nettoyer un bootstrap partiel.
             */
            Bukkit.getPluginManager()
                    .disablePlugin(
                            this
                    );
        }
    }

    @Override
    public void onDisable() {
        long startedAt =
                System.nanoTime();

        installLifecycleInfoGateIfNeeded();

        try {
            safeShutdown(
                    "API",
                    new ShutdownStep() {
                        @Override
                        public void run() {
                            if (api != null) {
                                api.shutdown();
                            }
                        }
                    }
            );

            /*
             * Empêcher d'abord de nouvelles actions runtime qui pourraient
             * modifier de l'état pendant le flush final.
             */
            safeShutdown(
                    "Fly",
                    new ShutdownStep() {
                        @Override
                        public void run() {
                            if (flyListener != null) {
                                flyListener.disableAll();
                            }
                        }
                    }
            );

            safeShutdown(
                    "Power",
                    new ShutdownStep() {
                        @Override
                        public void run() {
                            if (powerManager != null) {
                                powerManager.shutdown();
                            }
                        }
                    }
            );

            /*
             * IMPORTANT:
             * les inventaires doivent être capturés AVANT StorageManager,
             * sinon leur dernière mutation ne peut pas entrer dans le snapshot
             * final.
             */
            safeShutdown(
                    "Faction chests",
                    new ShutdownStep() {
                        @Override
                        public void run() {
                            if (factionChestManager != null) {
                                factionChestManager.saveAll();
                            }
                        }
                    }
            );

            safeShutdown(
                    "Progression runtime",
                    new ShutdownStep() {
                        @Override
                        public void run() {
                            if (questManager != null) {
                                questManager.shutdown();
                            }
                        }
                    }
            );

            safeShutdown(
                    "Placed block tracker",
                    new ShutdownStep() {
                        @Override
                        public void run() {
                            if (placedBlockTracker != null) {
                                placedBlockTracker.shutdown();
                            }
                        }
                    }
            );

            safeShutdown(
                    "Map runtime",
                    new ShutdownStep() {
                        @Override
                        public void run() {
                            if (mapManager != null) {
                                mapManager.shutdown();
                            }
                        }
                    }
            );

            safeShutdown(
                    "Hooks",
                    new ShutdownStep() {
                        @Override
                        public void run() {
                            if (hookManager != null) {
                                hookManager.shutdown();
                            }
                        }
                    }
            );

            /*
             * Snapshot/flush final pendant que:
             * - factions;
             * - FPlayers;
             * - claims/zones;
             * - Grace
             * existent encore en RAM.
             */
            safeShutdown(
                    "Storage final flush",
                    new ShutdownStep() {
                        @Override
                        public void run() {
                            if (storageManager != null) {
                                storageManager.shutdown();
                            }
                        }
                    }
            );

            /*
             * Une fois la persistance terminée, les domaines peuvent être
             * libérés sans risque de produire un snapshot vide.
             */
            safeShutdown(
                    "Permission/Grace",
                    new ShutdownStep() {
                        @Override
                        public void run() {
                            if (permissionManager != null) {
                                permissionManager.shutdown();
                            }
                        }
                    }
            );

            safeShutdown(
                    "Claims/Zones",
                    new ShutdownStep() {
                        @Override
                        public void run() {
                            if (claimManager != null) {
                                claimManager.shutdown();
                            }
                        }
                    }
            );

            safeShutdown(
                    "Factions",
                    new ShutdownStep() {
                        @Override
                        public void run() {
                            if (factionManager != null) {
                                factionManager.shutdown();
                            }
                        }
                    }
            );

            safeShutdown(
                    "FPlayers",
                    new ShutdownStep() {
                        @Override
                        public void run() {
                            if (fPlayerManager != null) {
                                fPlayerManager.shutdown();
                            }
                        }
                    }
            );

            safeShutdown(
                    "Economy",
                    new ShutdownStep() {
                        @Override
                        public void run() {
                            if (economyManager != null) {
                                economyManager.shutdown();
                            }
                        }
                    }
            );

            safeShutdown(
                    "Bukkit services",
                    new ShutdownStep() {
                        @Override
                        public void run() {
                            Bukkit.getServicesManager()
                                    .unregisterAll(
                                            Kfaction.this
                                    );
                        }
                    }
            );

            /*
             * Audit/logs en dernier pour conserver les erreurs des autres
             * composants jusqu'à la fin du shutdown.
             */
            safeShutdown(
                    "Logs/Audit",
                    new ShutdownStep() {
                        @Override
                        public void run() {
                            if (logManager != null) {
                                logManager.shutdown();
                            }
                        }
                    }
            );

        } finally {
            restoreLifecycleInfoGate();

            long elapsedMillis =
                    elapsedMillis(
                            startedAt
                    );

            if (enableCompleted) {
                KfactionLogger.success(
                        this,
                        "Arrêt propre terminé en "
                                + elapsedMillis
                                + " ms"
                );
            } else {
                KfactionLogger.warn(
                        this,
                        "Nettoyage d'un démarrage partiel terminé en "
                                + elapsedMillis
                                + " ms"
                );
            }

            instance = null;
        }
    }

    // ============================================================
    // Initialization
    // ============================================================

    private void initConfig() {
        saveDefaultConfig();

        configManager =
                new ConfigManager(
                        this
                );

        /*
         * debug doit être connu AVANT d'installer le filtre compact.
         */
        debugMode =
                configManager.getBoolean(
                        "debug",
                        false
                );

        KfactionLogger.banner(
                this,
                getDescription()
                        .getVersion()
        );

        installLifecycleInfoGateIfNeeded();

        messageManager =
                new MessageManager(
                        this
                );

        messageManager.initialize();

        KfactionLogger.debug(
                this,
                "Configuration principale + messages chargés."
        );
    }

    private void initHooks() {
        hookManager =
                new HookManager(
                        this
                );

        hookManager.initialize();

        KfactionLogger.debug(
                this,
                "HookManager initialisé."
        );
    }

    private void initStorage() {
        storageManager =
                new StorageManager(
                        this
                );

        storageManager.initialize();

        KfactionLogger.debug(
                this,
                "StorageManager initialisé."
        );
    }

    private void initManagers() {
        /*
         * Construction d'abord:
         * plusieurs services utilisent les getters plugin pendant initialize().
         */
        factionManager =
                new FactionManager(
                        this
                );

        fPlayerManager =
                new FPlayerManager(
                        this
                );

        claimManager =
                new ClaimManager(
                        this
                );

        powerManager =
                new PowerManager(
                        this
                );

        relationManager =
                new RelationManager(
                        this
                );

        permissionManager =
                new PermissionManager(
                        this
                );

        territoryManager =
                new TerritoryManager(
                        this
                );

        economyManager =
                new EconomyManager(
                        this
                );

        mapManager =
                new MapManager(
                        this
                );

        logManager =
                new LogManager(
                        this
                );

        levelManager =
                new LevelManager(
                        this
                );

        questManager =
                new QuestManager(
                        this
                );

        rewardManager =
                new RewardManager(
                        this
                );

        factionChestManager =
                new FactionChestManager(
                        this
                );

        /*
         * Audit en premier:
         * les services initialisés ensuite peuvent produire des événements
         * audités sans tomber sur un LogManager non prêt.
         */
        logManager.initialize();

        factionManager.initialize();
        fPlayerManager.initialize();
        claimManager.initialize();

        powerManager.initialize();

        /*
         * Ces deux appels avaient disparu du Kfaction.java transmis.
         * Ils sont nécessaires avant TerritoryManager.
         */
        relationManager.initialize();
        permissionManager.initialize();

        territoryManager.initialize();

        /*
         * EconomyService V2 n'était plus initialisé dans le bootstrap transmis.
         */
        economyManager.initialize();

        mapManager.initialize();

        levelManager.initialize();

        /*
         * QuestManager.initialize() initialise réellement ProgressionService.
         * Ne plus appeler seulement loadConfig().
         */
        questManager.initialize();

        rewardManager.initialize();

        factionChestManager.initialize();

        placedBlockTracker =
                new PlacedBlockTracker(
                        this
                );

        placedBlockTracker.initialize();

        KfactionLogger.debug(
                this,
                "Managers/services V2 initialisés dans l'ordre canonique."
        );
    }

    private void loadData() {
        storageManager.loadAll();

        /*
         * La migration progression ne commence qu'une fois toutes les
         * factions restaurées.
         */
        if (questManager != null) {
            questManager.migrateLoadedFactions();
        }

        /*
         * L'index claim runtime doit être reconstruit après le chargement
         * des factions.
         */
        claimManager.rebuildClaimIndex();

        KfactionLogger.debug(
                this,
                "Données restaurées et index claims reconstruit."
        );
    }

    private void registerListeners() {
        Bukkit.getPluginManager()
                .registerEvents(
                        new PlayerConnectionListener(
                                this
                        ),
                        this
                );

        Bukkit.getPluginManager()
                .registerEvents(
                        new ProtectionListener(
                                this
                        ),
                        this
                );

        Bukkit.getPluginManager()
                .registerEvents(
                        new CombatListener(
                                this
                        ),
                        this
                );

        territoryListener =
                new TerritoryListener(
                        this
                );

        territoryListener.loadConfig();

        Bukkit.getPluginManager()
                .registerEvents(
                        territoryListener,
                        this
                );

        exploitProtectionListener =
                new ExploitProtectionListener(
                        this
                );

        exploitProtectionListener.loadConfig();

        Bukkit.getPluginManager()
                .registerEvents(
                        exploitProtectionListener,
                        this
                );

        /*
         * Interception du chat avant Kchat.
         */
        factionChatListener =
                new FactionChatListener(
                        this
                );

        factionChatListener.loadConfig();

        Bukkit.getPluginManager()
                .registerEvents(
                        factionChatListener,
                        this
                );

        // --------------------------------------------------------
        // Progression listeners / bridges
        // --------------------------------------------------------

        Bukkit.getPluginManager()
                .registerEvents(
                        new QuestListener(
                                this
                        ),
                        this
                );

        if (new me.krunsh.kfaction.listeners.VanillaCraftQuestBridge(
                this
        ).register()) {
            KfactionLogger.debug(
                    this,
                    "Progression bridge CRAFT vanilla: ACTIVE"
            );
        }

        if (new me.krunsh.kfaction.listeners.BreedQuestBridge(
                this
        ).register()) {
            KfactionLogger.debug(
                    this,
                    "Progression bridge BREED: ACTIVE"
            );
        }

        if (new KcraftQuestBridge(
                this
        ).register()) {
            KfactionLogger.debug(
                    this,
                    "Progression bridge KCraft CUSTOM_CRAFT: ACTIVE"
            );
        }

        if (new KSpawnerQuestBridge(
                this
        ).register()) {
            KfactionLogger.debug(
                    this,
                    "Progression bridge KSpawner: ACTIVE"
            );
        }

        if (new KMineraiQuestBridge(
                this
        ).register()) {
            KfactionLogger.debug(
                    this,
                    "Progression bridge Kminerai CUSTOM_ORE_MINE: ACTIVE"
            );
        }

        if (Bukkit.getPluginManager()
                .isPluginEnabled(
                        "ShopGUIPlus"
                )) {
            if (new ShopGuiPlusQuestBridge(this).register()) {
                KfactionLogger.debug(
                        this,
                        "Progression bridge ShopGUIPlus SELL: ACTIVE"
                );
            }
        } else {
            /*
             * Plugin optionnel absent = état normal, pas un warning.
             */
            KfactionLogger.debug(
                    this,
                    "Progression bridge ShopGUIPlus SELL: MISSING (optionnel)"
            );
        }

        flyListener =
                new FlyListener(
                        this
                );

        flyListener.loadConfig();

        Bukkit.getPluginManager()
                .registerEvents(
                        flyListener,
                        this
                );

        antiSethomeListener =
                new AntiSethomeListener(
                        this
                );

        antiSethomeListener.loadConfig();

        Bukkit.getPluginManager()
                .registerEvents(
                        antiSethomeListener,
                        this
                );

        KfactionLogger.debug(
                this,
                "Listeners Bukkit enregistrés."
        );
    }

    private void registerCommands() {
        KfactionCommand playerCommand =
                new KfactionCommand(
                        this
                );

        if (getCommand(
                "f"
        ) == null) {
            throw new IllegalStateException(
                    "Commande 'f' absente de plugin.yml"
            );
        }

        getCommand(
                "f"
        ).setExecutor(
                playerCommand
        );

        getCommand(
                "f"
        ).setTabCompleter(
                playerCommand
        );

        me.krunsh.kfaction.commands.KfactionAdminCommand
                adminCommand =
                new me.krunsh.kfaction.commands.KfactionAdminCommand(
                        this
                );

        if (getCommand(
                "kfaction"
        ) == null) {
            throw new IllegalStateException(
                    "Commande 'kfaction' absente de plugin.yml"
            );
        }

        getCommand(
                "kfaction"
        ).setExecutor(
                adminCommand
        );

        getCommand(
                "kfaction"
        ).setTabCompleter(
                adminCommand
        );

        KfactionLogger.debug(
                this,
                "Commandes /f et /kf enregistrées."
        );
    }

    /**
     * PlaceholderAPI est désormais possédé par HookManager /
     * PlaceholderAPIHook.
     *
     * L'ancien registerPlaceholders() direct a été supprimé afin d'éviter le
     * double enregistrement de l'expansion "kfaction".
     */
    private void startTasks() {
        /*
         * PowerManager gère sa propre régénération.
         *
         * StorageManager V2 possède également l'UNIQUE autosave:
         * StorageManager.reloadSettings() -> restartAutoSave().
         *
         * Il ne faut donc surtout plus lancer ici un second scheduler async
         * storageManager.saveAsync().
         */
        new me.krunsh.kfaction.tasks.InactivityTask(
                this
        ).start();

        KfactionLogger.debug(
                this,
                "Tasks externes au StorageManager démarrées."
        );
    }

    // ============================================================
    // Startup / shutdown presentation
    // ============================================================

    private void logStartupSummary(
            long elapsedMillis
    ) {
        Storage storage =
                storageManager != null
                        ? storageManager.getStorage()
                        : null;

        String storageType =
                storage != null
                        ? storage.getType()
                        : "NONE";

        boolean storageConnected =
                storage != null
                        && storage.isConnected();

        int factions =
                factionManager != null
                        ? factionManager.getFactionCount()
                        : 0;

        int claims =
                claimManager != null
                        ? claimManager.getTotalClaims()
                        : 0;

        int zones =
                claimManager != null
                && claimManager.getZoneService() != null
                        ? claimManager.getZoneService()
                                .getTotalZoneChunks()
                        : 0;

        int zoneDefinitions =
                claimManager != null
                && claimManager.getZoneService() != null
                        ? claimManager.getZoneService()
                                .getDefinitions()
                                .size()
                        : 0;

        String apiVersion =
                api != null
                && api.getV2() != null
                        ? api.getV2()
                                .getApiVersion()
                        : KfactionApiV2.API_VERSION;

        KfactionLogger.success(
                this,
                "Core prêt — API "
                        + apiVersion
                        + " | storage="
                        + storageType
                        + (storageConnected
                                ? ""
                                : " (déconnecté)")
                        + " | payload="
                        + StorageSnapshot.CURRENT_SCHEMA_VERSION
        );

        KfactionLogger.info(
                this,
                "Données — "
                        + factions
                        + " factions | "
                        + claims
                        + " claims | "
                        + zones
                        + " chunks de zone | "
                        + zoneDefinitions
                        + " définitions"
        );

        logProgressionSummary();

        logIntegrationSummary();

        KfactionLogger.success(
                this,
                "Démarrage terminé en "
                        + elapsedMillis
                        + " ms"
                        + (debugMode
                                ? " — debug actif"
                                : "")
        );
    }

    private void logProgressionSummary() {
        if (questManager == null
                || questManager.getActiveConfig() == null) {
            KfactionLogger.warn(
                    this,
                    "Progression — aucune configuration active valide."
            );
            return;
        }

        if (!questManager.isEnabled()) {
            KfactionLogger.info(
                    this,
                    "Progression — désactivée par configuration."
            );
            return;
        }

        int levels =
                questManager.getActiveConfig()
                        .getLevels()
                        .size();

        int quests =
                questManager.getActiveQuestsCount();

        int issues =
                questManager.getLastValidationIssues() != null
                        ? questManager.getLastValidationIssues()
                                .size()
                        : 0;

        if (issues == 0) {
            KfactionLogger.info(
                    this,
                    "Progression — "
                            + levels
                            + " niveaux | "
                            + quests
                            + " quêtes | validation OK"
            );
        } else {
            KfactionLogger.warn(
                    this,
                    "Progression — "
                            + levels
                            + " niveaux | "
                            + quests
                            + " quêtes | "
                            + issues
                            + " problème(s) de validation"
            );
        }
    }

    private void logIntegrationSummary() {
        if (hookManager == null) {
            KfactionLogger.warn(
                    this,
                    "Intégrations — HookManager indisponible."
            );
            return;
        }

        Map<String, IntegrationState> states =
                hookManager.getIntegrationStates();

        StringBuilder present =
                new StringBuilder();

        int missing = 0;
        int failed = 0;

        for (IntegrationState state
                : states.values()) {
            if (state == null) {
                continue;
            }

            if (state.getStatus()
                    == Status.ACTIVE
                    || state.getStatus()
                            == Status.STARTING) {
                if (present.length() > 0) {
                    present.append(
                            " · "
                    );
                }

                if (state.getStatus()
                        == Status.STARTING) {
                    present.append(
                            "~"
                    );
                }

                present.append(
                        state.getPluginName()
                );

            } else if (state.getStatus()
                    == Status.FAILED) {
                failed++;

            } else if (state.getStatus()
                    == Status.MISSING) {
                missing++;
            }
        }

        String summary =
                present.length() > 0
                        ? present.toString()
                        : "aucune";

        if (failed > 0) {
            KfactionLogger.warn(
                    this,
                    "Intégrations — "
                            + summary
                            + " | failed="
                            + failed
                            + " | absentes="
                            + missing
            );
        } else {
            KfactionLogger.info(
                    this,
                    "Intégrations — "
                            + summary
                            + (missing > 0
                                    ? " | "
                                            + missing
                                            + " optionnelle(s) absente(s)"
                                    : "")
            );
        }
    }

    private void logDebugStartupDetails(
            long elapsedMillis
    ) {
        if (!debugMode) {
            return;
        }

        if (!configManager.getBoolean(
                "console.startup.show-timings-in-debug",
                true
        )) {
            return;
        }

        KfactionLogger.section(
                this,
                "Startup timings"
        );

        for (Map.Entry<String, Long> entry
                : startupTimings.entrySet()) {
            KfactionLogger.debug(
                    this,
                    padRight(
                            entry.getKey(),
                            20
                    )
                            + " "
                            + entry.getValue()
                            + " ms"
            );
        }

        KfactionLogger.debug(
                this,
                padRight(
                        "TOTAL",
                        20
                )
                        + " "
                        + elapsedMillis
                        + " ms"
        );

        if (storageManager != null) {
            KfactionLogger.section(
                    this,
                    "Storage"
            );

            Storage storage =
                    storageManager.getStorage();

            KfactionLogger.debug(
                    this,
                    "backend="
                            + (storage != null
                                    ? storage.getType()
                                    : "NONE")
                            + ", connected="
                            + (storage != null
                                    && storage.isConnected())
                            + ", writer="
                            + storageManager.getPendingWriteCount()
                            + "/"
                            + storageManager.getWriterQueueCapacity()
                            + ", rejected="
                            + storageManager.getRejectedWriteTaskCount()
                            + ", pendingDeletes="
                            + storageManager.getPendingDeleteCount()
            );
        }

        if (claimManager != null
                && claimManager.getZoneService() != null) {
            KfactionLogger.section(
                    this,
                    "Dynamic Zones"
            );

            KfactionLogger.debug(
                    this,
                    "definitions="
                            + claimManager.getZoneService()
                                    .getDefinitions()
                                    .size()
                            + ", chunks="
                            + claimManager.getZoneService()
                                    .getTotalZoneChunks()
                            + ", configIssues="
                            + claimManager.getZoneService()
                                    .getConfigurationIssues()
                                    .size()
                            + ", orphans="
                            + claimManager.getZoneService()
                                    .getOrphanZoneIds()
                                    .size()
            );
        }

        if (questManager != null) {
            KfactionLogger.section(
                    this,
                    "Progression"
            );

            KfactionLogger.debug(
                    this,
                    "enabled="
                            + questManager.isEnabled()
                            + ", activeQuests="
                            + questManager.getActiveQuestsCount()
                            + ", validationIssues="
                            + (questManager.getLastValidationIssues() != null
                                    ? questManager.getLastValidationIssues()
                                            .size()
                                    : 0)
            );
        }

        if (hookManager != null) {
            KfactionLogger.section(
                    this,
                    "Integrations"
            );

            for (IntegrationState state
                    : hookManager.getIntegrationStates()
                            .values()) {
                if (state == null) {
                    continue;
                }

                KfactionLogger.debug(
                        this,
                        padRight(
                                state.getPluginName(),
                                18
                        )
                                + " "
                                + state.getStatus()
                                        .name()
                                + (state.getDetail() != null
                                        ? " — "
                                                + state.getDetail()
                                        : "")
                );
            }
        }
    }

    // ============================================================
    // Quiet lifecycle gate
    // ============================================================

    /**
     * En production compacte, les anciens managers peuvent encore émettre
     * leurs propres INFO de bootstrap.
     *
     * Ce filtre n'est actif QUE pendant startup/shutdown:
     * - INFO et inférieur sont masqués;
     * - WARNING et SEVERE passent toujours;
     * - debug=true désactive complètement le filtre.
     *
     * Une fois le lifecycle terminé, le filtre précédent est restauré.
     */
    private void installLifecycleInfoGateIfNeeded() {
        if (lifecycleFilterInstalled
                || debugMode
                || configManager == null
                || !configManager.getBoolean(
                        "console.startup.compact",
                        true
                )) {
            return;
        }

        previousLifecycleFilter =
                getLogger()
                        .getFilter();

        final Filter previous =
                previousLifecycleFilter;

        getLogger()
                .setFilter(
                        new Filter() {
                            @Override
                            public boolean isLoggable(
                                    LogRecord record
                            ) {
                                if (record == null) {
                                    return true;
                                }

                                if (record.getLevel()
                                        .intValue()
                                        < Level.WARNING
                                                .intValue()) {
                                    return false;
                                }

                                return previous == null
                                        || previous.isLoggable(
                                                record
                                        );
                            }
                        }
                );

        lifecycleFilterInstalled = true;
    }

    private void restoreLifecycleInfoGate() {
        if (!lifecycleFilterInstalled) {
            return;
        }

        getLogger()
                .setFilter(
                        previousLifecycleFilter
                );

        previousLifecycleFilter = null;
        lifecycleFilterInstalled = false;
    }

    // ============================================================
    // Timing helpers
    // ============================================================

    private void timed(
            String name,
            StartupStep step
    ) throws Exception {
        long startedAt =
                System.nanoTime();

        step.run();

        startupTimings.put(
                name,
                elapsedMillis(
                        startedAt
                )
        );
    }

    private static long elapsedMillis(
            long startedAtNanos
    ) {
        long nanos =
                System.nanoTime()
                        - startedAtNanos;

        return TimeUnit.NANOSECONDS
                .toMillis(
                        Math.max(
                                0L,
                                nanos
                        )
                );
    }

    private static String padRight(
            String value,
            int width
    ) {
        String safe =
                value != null
                        ? value
                        : "";

        if (safe.length()
                >= width) {
            return safe;
        }

        StringBuilder builder =
                new StringBuilder(
                        safe
                );

        while (builder.length()
                < width) {
            builder.append(
                    '.'
            );
        }

        return builder.toString();
    }

    private interface StartupStep {
        void run() throws Exception;
    }

    private interface ShutdownStep {
        void run() throws Exception;
    }

    private void safeShutdown(
            String component,
            ShutdownStep step
    ) {
        try {
            step.run();

            KfactionLogger.debug(
                    this,
                    "Shutdown "
                            + component
                            + ": OK"
            );

        } catch (Throwable throwable) {
            KfactionLogger.error(
                    this,
                    "Shutdown "
                            + component
                            + ": "
                            + throwable.getClass()
                                    .getSimpleName()
                            + (throwable.getMessage() != null
                                    ? " — "
                                            + throwable.getMessage()
                                    : "")
            );

            getLogger().log(
                    Level.SEVERE,
                    "Stacktrace shutdown "
                            + component,
                    throwable
            );
        }
    }

    // ============================================================
    // Logging compatibility
    // ============================================================

    /**
     * Compatibilité avec les anciennes classes appelant plugin.logInfo().
     *
     * Les codes Minecraft &x/§x sont nettoyés avant d'envoyer le texte au
     * logger ANSI V2.
     */
    public void logInfo(
            String message
    ) {
        KfactionLogger.info(
                this,
                sanitizeConsoleText(
                        message
                )
        );
    }

    public void logError(
            String message
    ) {
        KfactionLogger.error(
                this,
                sanitizeConsoleText(
                        message
                )
        );
    }

    public void debug(
            String message
    ) {
        KfactionLogger.debug(
                this,
                sanitizeConsoleText(
                        message
                )
        );
    }

    private static String sanitizeConsoleText(
            String message
    ) {
        if (message == null) {
            return "";
        }

        return message.replaceAll(
                "(?i)[&§][0-9A-FK-OR]",
                ""
        );
    }

    // ============================================================
    // Getters
    // ============================================================

    public static Kfaction getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public StorageManager getStorageManager() {
        return storageManager;
    }

    public FactionManager getFactionManager() {
        return factionManager;
    }

    public FPlayerManager getFPlayerManager() {
        return fPlayerManager;
    }

    public ClaimManager getClaimManager() {
        return claimManager;
    }

    public PowerManager getPowerManager() {
        return powerManager;
    }

    public RelationManager getRelationManager() {
        return relationManager;
    }

    public PermissionManager getPermissionManager() {
        return permissionManager;
    }

    public TerritoryManager getTerritoryManager() {
        return territoryManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public MapManager getMapManager() {
        return mapManager;
    }

    public LogManager getLogManager() {
        return logManager;
    }

    public LevelManager getLevelManager() {
        return levelManager;
    }

    public QuestManager getQuestManager() {
        return questManager;
    }

    public RewardManager getRewardManager() {
        return rewardManager;
    }

    public FactionChestManager getFactionChestManager() {
        return factionChestManager;
    }

    public PlacedBlockTracker getPlacedBlockTracker() {
        return placedBlockTracker;
    }

    public FlyListener getFlyListener() {
        return flyListener;
    }

    public HookManager getHookManager() {
        return hookManager;
    }

    public KfactionAPI getAPI() {
        return api;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public Map<String, Long> getStartupTimingsSnapshot() {
        return Collections.unmodifiableMap(
                new LinkedHashMap<String, Long>(
                        startupTimings
                )
        );
    }

    // ============================================================
    // Admin bypass
    // ============================================================

    private final java.util.Set<java.util.UUID>
            bypassingPlayers =
            new java.util.HashSet<java.util.UUID>();

    /**
     * Bascule le mode bypass admin pour un joueur.
     */
    public void toggleBypass(
            java.util.UUID playerId
    ) {
        if (playerId == null) {
            return;
        }

        if (bypassingPlayers.contains(
                playerId
        )) {
            bypassingPlayers.remove(
                    playerId
            );
        } else {
            bypassingPlayers.add(
                    playerId
            );
        }

        /*
         * Compatibilité V1:
         * getFPlayer() reste l'ancien alias get-or-create.
         */
        if (fPlayerManager != null) {
            me.krunsh.kfaction.data.FPlayer fPlayer =
                    fPlayerManager.getFPlayer(
                            playerId
                    );

            if (fPlayer != null) {
                fPlayer.setBypassing(
                        bypassingPlayers.contains(
                                playerId
                        )
                );
            }
        }
    }

    public boolean isBypassing(
            java.util.UUID playerId
    ) {
        return playerId != null
                && bypassingPlayers.contains(
                        playerId
                );
    }

    // ============================================================
    // Reload
    // ============================================================

    public void reload() {
        long startedAt =
                System.nanoTime();

        configManager.reload();

        debugMode =
                configManager.getBoolean(
                        "debug",
                        false
                );

        messageManager.reload();

        relationManager.loadConfig();

        if (powerManager != null) {
            powerManager.loadConfig();
        }

        if (fPlayerManager != null) {
            fPlayerManager.reloadPowerSettings();
        }

        if (permissionManager != null) {
            permissionManager.reload();
        }

        if (territoryManager != null) {
            territoryManager.reload();
        }

        if (storageManager != null) {
            storageManager.reloadSettings();
        }

        if (levelManager != null) {
            levelManager.loadConfig();
        }

        if (questManager != null) {
            questManager.loadConfig();
        }

        if (mapManager != null) {
            mapManager.reload();
        }

        if (territoryListener != null) {
            territoryListener.loadConfig();
        }

        if (flyListener != null) {
            flyListener.loadConfig();
        }

        if (exploitProtectionListener != null) {
            exploitProtectionListener.loadConfig();
        }

        if (factionChatListener != null) {
            factionChatListener.loadConfig();
        }

        if (antiSethomeListener != null) {
            antiSethomeListener.loadConfig();
        }

        /*
         * ZoneService.reload() relit directement zones.* depuis config.yml
         * disque dans le Lot25B.
         */
        if (claimManager != null
                && claimManager.getZoneService() != null) {
            claimManager.getZoneService()
                    .reload();
        }

        long elapsedMillis =
                elapsedMillis(
                        startedAt
                );

        KfactionLogger.reload(
                this,
                "Configuration rechargée en "
                        + elapsedMillis
                        + " ms"
        );

        if (debugMode) {
            KfactionLogger.debug(
                    this,
                    "debug=true après reload."
            );
        }
    }
}
