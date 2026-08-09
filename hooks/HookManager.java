package me.krunsh.kfaction.hooks;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.hooks.IntegrationState.Status;

/**
 * Gestionnaire central des intégrations optionnelles.
 *
 * Lot 21:
 * - état explicite MISSING/STARTING/ACTIVE/FAILED/DISABLED;
 * - aucun crash global si un hook optionnel est cassé;
 * - LuckPerms contexts thread-safe;
 * - façade conservée pour les anciens getXHook()/hasX().
 */
public final class HookManager
        implements Listener {

    private final Kfaction plugin;

    private final ConcurrentMap<String, IntegrationState> states =
            new ConcurrentHashMap<String, IntegrationState>();

    private VaultHook vaultHook;
    private PlaceholderAPIHook placeholderHook;
    private KcoreHook kcoreHook;
    private KchatHook kchatHook;
    private KguiHook kguiHook;
    private KclassementHook kclassementHook;
    private LuckPermsHook luckPermsHook;

    public HookManager(
            Kfaction plugin
    ) {
        if (plugin == null) {
            throw new IllegalArgumentException(
                    "plugin cannot be null"
            );
        }

        this.plugin = plugin;
    }

    public void initialize() {
        Bukkit.getPluginManager()
                .registerEvents(
                        this,
                        plugin
                );

        initializeVault();
        initializePlaceholderApi();
        initializeKcore();
        initializeKchat();
        initializeKgui();
        initializeKclassement();
        initializeLuckPerms();
    }

    @EventHandler
    public void onPluginDisable(
            PluginDisableEvent event
    ) {
        if (event != null
                && event.getPlugin() == plugin) {
            shutdown();
        }
    }

    public void shutdown() {
        if (placeholderHook != null) {
            safeShutdown(
                    "placeholderapi",
                    "PlaceholderAPI",
                    new Runnable() {
                        @Override
                        public void run() {
                            placeholderHook.shutdown();
                        }
                    }
            );
        }

        if (luckPermsHook != null) {
            safeShutdown(
                    "luckperms",
                    "LuckPerms",
                    new Runnable() {
                        @Override
                        public void run() {
                            luckPermsHook.shutdown();
                        }
                    }
            );
        }
    }

    // ============================================================
    // Initialization
    // ============================================================

    private void initializeVault() {
        final String id = "vault";

        if (!isPluginEnabled("Vault")) {
            missing(id, "Vault");
            return;
        }

        try {
            vaultHook = new VaultHook(plugin);

            if (vaultHook.initialize()) {
                active(id, "Vault", "economy-provider");
                plugin.getLogger().info(
                        "Hook Vault activé"
                );
            } else {
                vaultHook = null;
                failed(
                        id,
                        "Vault",
                        "aucun provider Economy"
                );
            }

        } catch (Throwable throwable) {
            vaultHook = null;
            failed(
                    id,
                    "Vault",
                    throwable
            );
        }
    }

    private void initializePlaceholderApi() {
        final String id = "placeholderapi";

        if (!isPluginEnabled("PlaceholderAPI")) {
            missing(
                    id,
                    "PlaceholderAPI"
            );
            return;
        }

        try {
            placeholderHook =
                    new PlaceholderAPIHook(
                            plugin
                    );

            placeholderHook.initialize();

            if (placeholderHook.isRegistered()) {
                active(
                        id,
                        "PlaceholderAPI",
                        "kfaction expansion"
                );

            } else if (placeholderHook.isActivationScheduled()) {
                states.put(
                        id,
                        new IntegrationState(
                                id,
                                "PlaceholderAPI",
                                Status.STARTING,
                                "activation différée"
                        )
                );

            } else {
                failed(
                        id,
                        "PlaceholderAPI",
                        "expansion non enregistrée"
                );
            }

        } catch (Throwable throwable) {
            placeholderHook = null;
            failed(
                    id,
                    "PlaceholderAPI",
                    throwable
            );
        }
    }

    private void initializeKcore() {
        final String id = "kcore";

        if (!isPluginEnabled("Kcore")) {
            missing(id, "Kcore");
            return;
        }

        try {
            kcoreHook =
                    new KcoreHook(plugin);

            kcoreHook.initialize();

            active(
                    id,
                    "Kcore",
                    "territory/pvp adapter"
            );

        } catch (Throwable throwable) {
            kcoreHook = null;
            failed(
                    id,
                    "Kcore",
                    throwable
            );
        }
    }

    private void initializeKchat() {
        final String id = "kchat";

        if (!isPluginEnabled("Kchat")) {
            missing(id, "Kchat");
            return;
        }

        try {
            kchatHook =
                    new KchatHook(plugin);

            kchatHook.initialize();

            if (kchatHook.isInitialized()) {
                active(
                        id,
                        "Kchat",
                        "nametag adapter"
                );
            } else {
                failed(
                        id,
                        "Kchat",
                        "NametagManager incompatible/indisponible"
                );
            }

        } catch (Throwable throwable) {
            kchatHook = null;
            failed(
                    id,
                    "Kchat",
                    throwable
            );
        }
    }

    private void initializeKgui() {
        final String id = "kgui";

        if (!isPluginEnabled("Kgui")) {
            missing(id, "Kgui");
            return;
        }

        try {
            /*
             * KguiHook reste le bridge V1 de menus jusqu'au vrai Kgui V2.
             * Les ContentProviders sont migrés API V2 dans ce lot.
             */
            kguiHook =
                    new KguiHook(plugin);

            kguiHook.initialize();

            active(
                    id,
                    "Kgui",
                    "legacy menu bridge + API V2 content"
            );

        } catch (Throwable throwable) {
            kguiHook = null;
            failed(
                    id,
                    "Kgui",
                    throwable
            );
        }
    }

    private void initializeKclassement() {
        final String id = "kclassement";

        if (!isPluginEnabled("Kclassement")) {
            missing(
                    id,
                    "Kclassement"
            );
            return;
        }

        try {
            kclassementHook =
                    new KclassementHook(
                            plugin
                    );

            kclassementHook.initialize();

            active(
                    id,
                    "Kclassement",
                    "read-only FTop adapter"
            );

        } catch (Throwable throwable) {
            kclassementHook = null;
            failed(
                    id,
                    "Kclassement",
                    throwable
            );
        }
    }

    private void initializeLuckPerms() {
        final String id = "luckperms";

        if (!plugin.getConfigManager()
                .getBoolean(
                        "integrations.luckperms-contexts.enabled",
                        true
                )) {
            states.put(
                    id,
                    new IntegrationState(
                            id,
                            "LuckPerms",
                            Status.DISABLED,
                            "config"
                    )
            );
            return;
        }

        if (!isPluginEnabled("LuckPerms")) {
            missing(
                    id,
                    "LuckPerms"
            );
            return;
        }

        try {
            luckPermsHook =
                    new LuckPermsHook(
                            plugin
                    );

            if (luckPermsHook.initialize()) {
                states.put(
                        id,
                        new IntegrationState(
                                id,
                                "LuckPerms",
                                Status.STARTING,
                                "activation différée"
                        )
                );
            } else {
                failed(
                        id,
                        "LuckPerms",
                        "initialization refused"
                );
            }

        } catch (Throwable throwable) {
            luckPermsHook = null;
            failed(
                    id,
                    "LuckPerms",
                    throwable
            );
        }
    }

    // ============================================================
    // Status
    // ============================================================

    public Map<String, IntegrationState>
            getIntegrationStates() {
        refreshDynamicStates();

        LinkedHashMap<String, IntegrationState> copy =
                new LinkedHashMap<String, IntegrationState>();

        for (String id : new String[] {
                "vault",
                "placeholderapi",
                "kcore",
                "kchat",
                "kgui",
                "kclassement",
                "luckperms"
        }) {
            IntegrationState state =
                    states.get(id);

            if (state != null) {
                copy.put(
                        id,
                        state
                );
            }
        }

        return Collections.unmodifiableMap(
                copy
        );
    }

    public IntegrationState getIntegrationState(
            String id
    ) {
        refreshDynamicStates();

        return id != null
                ? states.get(
                        id.toLowerCase()
                )
                : null;
    }

    private void refreshDynamicStates() {
        if (placeholderHook != null
                && placeholderHook.isRegistered()) {
            active(
                    "placeholderapi",
                    "PlaceholderAPI",
                    placeholderHook.ownsRegistration()
                            ? "kfaction expansion owned"
                            : "kfaction expansion existing"
            );
        }

        if (luckPermsHook != null
                && luckPermsHook.isInitialized()) {
            active(
                    "luckperms",
                    "LuckPerms",
                    "4 cached faction contexts"
            );
        }
    }

    // ============================================================
    // LuckPerms context synchronization
    // ============================================================

    public void refreshPermissionContexts(
            UUID playerId
    ) {
        if (luckPermsHook != null) {
            luckPermsHook.refreshPlayer(
                    playerId
            );
        }
    }

    public void removePermissionContexts(
            UUID playerId
    ) {
        if (luckPermsHook != null) {
            luckPermsHook.removePlayer(
                    playerId
            );
        }
    }

    // ============================================================
    // Compatibility getters
    // ============================================================

    public boolean hasVault() {
        return vaultHook != null
                && vaultHook.isEnabled();
    }

    public VaultHook getVaultHook() {
        return vaultHook;
    }

    public boolean hasPlaceholderAPI() {
        return placeholderHook != null
                && placeholderHook.isRegistered();
    }

    public PlaceholderAPIHook getPlaceholderHook() {
        return placeholderHook;
    }

    public boolean hasKcore() {
        return kcoreHook != null;
    }

    public KcoreHook getKcoreHook() {
        return kcoreHook;
    }

    public boolean hasKchat() {
        return kchatHook != null
                && kchatHook.isInitialized();
    }

    public KchatHook getKchatHook() {
        return kchatHook;
    }

    public boolean hasKgui() {
        return kguiHook != null;
    }

    public KguiHook getKguiHook() {
        return kguiHook;
    }

    public boolean hasKclassement() {
        return kclassementHook != null;
    }

    public KclassementHook getKclassementHook() {
        return kclassementHook;
    }

    public boolean hasLuckPerms() {
        return luckPermsHook != null
                && luckPermsHook.isInitialized();
    }

    public LuckPermsHook getLuckPermsHook() {
        return luckPermsHook;
    }

    // ============================================================
    // Helpers
    // ============================================================

    private boolean isPluginEnabled(
            String name
    ) {
        Plugin external =
                Bukkit.getPluginManager()
                        .getPlugin(name);

        return external != null
                && external.isEnabled();
    }

    private void missing(
            String id,
            String pluginName
    ) {
        states.put(
                id,
                new IntegrationState(
                        id,
                        pluginName,
                        Status.MISSING,
                        null
                )
        );
    }

    private void active(
            String id,
            String pluginName,
            String detail
    ) {
        states.put(
                id,
                new IntegrationState(
                        id,
                        pluginName,
                        Status.ACTIVE,
                        detail
                )
        );
    }

    private void failed(
            String id,
            String pluginName,
            Throwable throwable
    ) {
        failed(
                id,
                pluginName,
                throwable != null
                        ? throwable.getClass()
                                .getSimpleName()
                                + ": "
                                + throwable.getMessage()
                        : "unknown"
        );
    }

    private void failed(
            String id,
            String pluginName,
            String detail
    ) {
        states.put(
                id,
                new IntegrationState(
                        id,
                        pluginName,
                        Status.FAILED,
                        detail
                )
        );

        plugin.getLogger().warning(
                "Intégration "
                        + pluginName
                        + " désactivée: "
                        + detail
        );
    }

    private void safeShutdown(
            String id,
            String pluginName,
            Runnable shutdown
    ) {
        try {
            shutdown.run();

            states.put(
                    id,
                    new IntegrationState(
                            id,
                            pluginName,
                            Status.DISABLED,
                            "shutdown"
                    )
            );

        } catch (Throwable throwable) {
            failed(
                    id,
                    pluginName,
                    throwable
            );
        }
    }
}
