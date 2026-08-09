package me.krunsh.kfaction.hooks;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.RegisteredServiceProvider;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.api.v2.FactionView;
import me.krunsh.kfaction.api.v2.KfactionApiV2;
import me.krunsh.kfaction.api.v2.KfactionApis;
import me.krunsh.kfaction.api.v2.PlayerView;

/**
 * Hook LuckPerms V2 sans dépendance compile-time.
 *
 * IMPORTANT:
 * LuckPerms peut appeler ContextCalculator depuis un thread async.
 * calculate(...) ne touche donc JAMAIS Bukkit/Kfaction live:
 * il lit seulement un snapshot ConcurrentMap préparé sur le main thread.
 */
public final class LuckPermsHook
        implements Listener {

    public static final String CONTEXT_HAS_FACTION =
            "kfaction:has-faction";

    public static final String CONTEXT_FACTION =
            "kfaction:faction";

    public static final String CONTEXT_FACTION_TAG =
            "kfaction:faction-tag";

    public static final String CONTEXT_ROLE =
            "kfaction:role";

    private final Kfaction plugin;

    private final ConcurrentMap<UUID, ContextSnapshot> cache =
            new ConcurrentHashMap<UUID, ContextSnapshot>();

    private volatile boolean initialized;
    private volatile boolean activationScheduled;

    private volatile KfactionApiV2 cachedApi;

    private Object contextManager;
    private Object calculator;

    public LuckPermsHook(
            Kfaction plugin
    ) {
        if (plugin == null) {
            throw new IllegalArgumentException(
                    "plugin cannot be null"
            );
        }

        this.plugin = plugin;
    }

    /**
     * HookManager est créé très tôt dans le bootstrap.
     * On active donc LuckPerms au tick suivant, une fois KfactionAPI V2
     * enregistré et les données chargées.
     */
    public boolean initialize() {
        if (activationScheduled || initialized) {
            return true;
        }

        activationScheduled = true;

        Bukkit.getScheduler().runTask(
                plugin,
                new Runnable() {
                    @Override
                    public void run() {
                        activate();
                    }
                }
        );

        return true;
    }

    private void activate() {
        activationScheduled = false;

        try {
            Class<?> luckPermsClass =
                    Class.forName(
                            "net.luckperms.api.LuckPerms"
                    );

            @SuppressWarnings({"unchecked", "rawtypes"})
            RegisteredServiceProvider<?> registration =
                    Bukkit.getServicesManager()
                            .getRegistration(
                                    (Class) luckPermsClass
                            );

            if (registration == null
                    || registration.getProvider() == null) {
                plugin.getLogger().warning(
                        "LuckPerms détecté mais API service indisponible."
                );
                return;
            }

            Object luckPerms =
                    registration.getProvider();

            contextManager =
                    luckPerms.getClass()
                            .getMethod("getContextManager")
                            .invoke(luckPerms);

            if (contextManager == null) {
                plugin.getLogger().warning(
                        "LuckPerms ContextManager indisponible."
                );
                return;
            }

            ClassLoader loader =
                    luckPerms.getClass()
                            .getClassLoader();

            final Class<?> calculatorInterface =
                    loader.loadClass(
                            "net.luckperms.api.context.ContextCalculator"
                    );

            calculator =
                    Proxy.newProxyInstance(
                            calculatorInterface.getClassLoader(),
                            new Class<?>[] {
                                    calculatorInterface
                            },
                            new CalculatorHandler(loader)
                    );

            Method register =
                    contextManager.getClass()
                            .getMethod(
                                    "registerCalculator",
                                    calculatorInterface
                            );

            register.invoke(
                    contextManager,
                    calculator
            );

            Bukkit.getPluginManager()
                    .registerEvents(
                            this,
                            plugin
                    );

            initialized = true;

            for (Player player
                    : Bukkit.getOnlinePlayers()) {
                refreshPlayer(
                        player.getUniqueId()
                );
            }

            me.krunsh.kfaction.utils.KfactionLogger.debug(
                    plugin,
                    "LuckPerms contexts Kfaction: ACTIVE (cache thread-safe)."
            );

        } catch (Throwable throwable) {
            initialized = false;
            contextManager = null;
            calculator = null;

            plugin.getLogger().warning(
                    "Hook LuckPerms désactivé: "
                            + throwable.getClass().getSimpleName()
                            + ": "
                            + throwable.getMessage()
            );
        }
    }

    public void shutdown() {
        initialized = false;
        cache.clear();

        if (contextManager == null
                || calculator == null) {
            return;
        }

        try {
            Method unregister = null;

            for (Method method
                    : contextManager.getClass()
                            .getMethods()) {
                if ("unregisterCalculator"
                        .equals(method.getName())
                        && method.getParameterTypes()
                                .length == 1) {
                    unregister = method;
                    break;
                }
            }

            if (unregister != null) {
                unregister.invoke(
                        contextManager,
                        calculator
                );
            }

        } catch (Throwable throwable) {
            if (plugin.isDebugMode()) {
                plugin.getLogger().warning(
                        "[Debug] LuckPerms unregister: "
                                + throwable.getMessage()
                );
            }
        } finally {
            contextManager = null;
            calculator = null;
        }
    }

    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Peut être appelé par MembershipService/RoleService.
     */
    public void refreshPlayer(
            final UUID playerId
    ) {
        if (playerId == null) {
            return;
        }

        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            new Runnable() {
                                @Override
                                public void run() {
                                    refreshPlayer(
                                            playerId
                                    );
                                }
                            }
                    );

            return;
        }

        if (!initialized) {
            return;
        }

        Player player =
                Bukkit.getPlayer(
                        playerId
                );

        if (player == null
                || !player.isOnline()) {
            cache.remove(playerId);
            return;
        }

        KfactionApiV2 api =
                api();

        if (api == null) {
            return;
        }

        PlayerView playerView =
                api.getPlayer(
                        playerId
                );

        FactionView factionView =
                api.getPlayerFaction(
                        playerId
                );

        LinkedHashMap<String, String> contexts =
                new LinkedHashMap<String, String>();

        boolean hasFaction =
                playerView != null
                        && playerView.hasFaction()
                        && factionView != null;

        contexts.put(
                CONTEXT_HAS_FACTION,
                hasFaction
                        ? "true"
                        : "false"
        );

        if (hasFaction) {
            putContext(
                    contexts,
                    CONTEXT_FACTION,
                    factionView.getId()
            );

            putContext(
                    contexts,
                    CONTEXT_FACTION_TAG,
                    factionView.getTag()
            );

            putContext(
                    contexts,
                    CONTEXT_ROLE,
                    playerView.getRole()
            );
        }

        cache.put(
                playerId,
                new ContextSnapshot(
                        contexts
                )
        );

        signalContextUpdate(player);
    }

    public void removePlayer(
            UUID playerId
    ) {
        if (playerId != null) {
            cache.remove(playerId);
        }
    }

    public Map<String, String> getCachedContexts(
            UUID playerId
    ) {
        ContextSnapshot snapshot =
                playerId != null
                        ? cache.get(playerId)
                        : null;

        return snapshot != null
                ? snapshot.values
                : Collections.<String, String>emptyMap();
    }

    @EventHandler
    public void onJoin(
            final PlayerJoinEvent event
    ) {
        /*
         * PlayerConnectionListener peut avoir encore son chargement à finir
         * dans le même event. Le refresh est donc fait au tick suivant.
         */
        Bukkit.getScheduler()
                .runTask(
                        plugin,
                        new Runnable() {
                            @Override
                            public void run() {
                                refreshPlayer(
                                        event.getPlayer()
                                                .getUniqueId()
                                );
                            }
                        }
                );
    }

    @EventHandler
    public void onQuit(
            PlayerQuitEvent event
    ) {
        removePlayer(
                event.getPlayer()
                        .getUniqueId()
        );
    }

    private KfactionApiV2 api() {
        KfactionApiV2 current = cachedApi;

        if (current == null) {
            current = KfactionApis.get();

            if (current != null) {
                cachedApi = current;
            }
        }

        return current;
    }

    private void signalContextUpdate(
            Player player
    ) {
        if (contextManager == null
                || player == null) {
            return;
        }

        try {
            Method signal = null;

            for (Method method
                    : contextManager.getClass()
                            .getMethods()) {
                if ("signalContextUpdate"
                        .equals(method.getName())
                        && method.getParameterTypes()
                                .length == 1) {
                    signal = method;
                    break;
                }
            }

            if (signal != null) {
                signal.invoke(
                        contextManager,
                        player
                );
            }

        } catch (Throwable throwable) {
            if (plugin.isDebugMode()) {
                plugin.getLogger().warning(
                        "[Debug] LuckPerms signalContextUpdate: "
                                + throwable.getMessage()
                );
            }
        }
    }

    private static void putContext(
            Map<String, String> target,
            String key,
            String value
    ) {
        String normalized =
                normalizeContextValue(
                        value
                );

        if (normalized != null) {
            target.put(
                    key,
                    normalized
            );
        }
    }

    private static String normalizeContextValue(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String normalized =
                value.trim()
                        .toLowerCase(
                                Locale.ROOT
                        )
                        .replace(' ', '_');

        return normalized.isEmpty()
                ? null
                : normalized;
    }

    private final class CalculatorHandler
            implements InvocationHandler {

        private final ClassLoader loader;

        private CalculatorHandler(
                ClassLoader loader
        ) {
            this.loader = loader;
        }

        @Override
        public Object invoke(
                Object proxy,
                Method method,
                Object[] args
        ) throws Throwable {
            String name =
                    method.getName();

            if ("calculate".equals(name)) {
                calculate(args);
                return null;
            }

            if ("estimatePotentialContexts"
                    .equals(name)) {
                return buildPotentialContexts();
            }

            if ("toString".equals(name)) {
                return "KfactionLuckPermsContextCalculator";
            }

            if ("hashCode".equals(name)) {
                return Integer.valueOf(
                        System.identityHashCode(proxy)
                );
            }

            if ("equals".equals(name)) {
                return Boolean.valueOf(
                        args != null
                                && args.length == 1
                                && proxy == args[0]
                );
            }

            return null;
        }

        private void calculate(
                Object[] args
        ) throws Exception {
            if (args == null
                    || args.length < 2
                    || !(args[0] instanceof Player)
                    || args[1] == null) {
                return;
            }

            Player player =
                    (Player) args[0];

            ContextSnapshot snapshot =
                    cache.get(
                            player.getUniqueId()
                    );

            if (snapshot == null) {
                return;
            }

            /*
             * IMPORTANT:
             * args[1] est souvent une lambda interne créée par LuckPerms
             * (ContextManager$$Lambda$...). Sa méthode accept(...) est
             * publique, mais sa CLASSE d'implémentation ne l'est pas
             * forcément.
             *
             * Java 8 peut donc refuser:
             * args[1].getClass().getMethod(...).invoke(...)
             *
             * On invoque volontairement la méthode depuis l'interface
             * publique LuckPerms ContextConsumer.
             */
            Class<?> contextConsumer =
                    loader.loadClass(
                            "net.luckperms.api.context.ContextConsumer"
                    );

            Method accept =
                    contextConsumer.getMethod(
                            "accept",
                            String.class,
                            String.class
                    );

            for (Map.Entry<String, String> entry
                    : snapshot.values
                            .entrySet()) {
                accept.invoke(
                        args[1],
                        entry.getKey(),
                        entry.getValue()
                );
            }
        }

        private Object buildPotentialContexts()
                throws Exception {
            Class<?> immutableContextSet =
                    loader.loadClass(
                            "net.luckperms.api.context.ImmutableContextSet"
                    );

            Object builder =
                    immutableContextSet
                            .getMethod("builder")
                            .invoke(null);

            /*
             * Même principe que ContextConsumer:
             * ne jamais invoquer une méthode via la classe interne réelle
             * du builder LuckPerms. On utilise le contrat public
             * ImmutableContextSet.Builder.
             */
            Class<?> builderInterface =
                    loader.loadClass(
                            "net.luckperms.api.context.ImmutableContextSet$Builder"
                    );

            Method add =
                    builderInterface.getMethod(
                            "add",
                            String.class,
                            String.class
                    );

            add.invoke(
                    builder,
                    CONTEXT_HAS_FACTION,
                    "true"
            );

            add.invoke(
                    builder,
                    CONTEXT_HAS_FACTION,
                    "false"
            );

            for (String role : new String[] {
                    "recruit",
                    "member",
                    "officer",
                    "moderator",
                    "coleader",
                    "leader"
            }) {
                add.invoke(
                        builder,
                        CONTEXT_ROLE,
                        role
                );
            }

            return builderInterface
                    .getMethod("build")
                    .invoke(builder);
        }
    }

    private static final class ContextSnapshot {

        private final Map<String, String> values;

        private ContextSnapshot(
                Map<String, String> values
        ) {
            this.values =
                    Collections.unmodifiableMap(
                            new LinkedHashMap<String, String>(
                                    values
                            )
                    );
        }
    }
}
