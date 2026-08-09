package me.krunsh.kfaction.hooks;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.api.v2.FactionView;
import me.krunsh.kfaction.api.v2.KfactionApiV2;
import me.krunsh.kfaction.api.v2.KfactionApis;
import me.krunsh.kfaction.api.v2.PlayerView;

/**
 * Hook Kchat V2.
 *
 * Les lectures faction/joueur passent exclusivement par KfactionApiV2.
 * La reflection est limitée à l'API externe de Kchat.
 */
public final class KchatHook {

    private final Kfaction plugin;

    private Plugin kchatPlugin;
    private Object nametagManager;

    private Method updateNametagMethod;
    private Method refreshAllNametagsMethod;

    private boolean initialized;

    private volatile KfactionApiV2 cachedApi;

    public KchatHook(
            Kfaction plugin
    ) {
        this.plugin = plugin;
    }

    public void initialize() {
        initialized = false;

        try {
            kchatPlugin =
                    Bukkit.getPluginManager()
                            .getPlugin("Kchat");

            if (kchatPlugin == null
                    || !kchatPlugin.isEnabled()) {
                return;
            }

            Method getter =
                    findAccessibleMethod(
                            kchatPlugin.getClass(),
                            "getNametagManager"
                    );

            if (getter == null) {
                throw new NoSuchMethodException(
                        "Kchat#getNametagManager"
                );
            }

            nametagManager =
                    invoke(
                            getter,
                            kchatPlugin
                    );

            if (nametagManager == null) {
                return;
            }

            /*
             * updateNametag(Player) est la seule méthode réellement requise
             * pour déclarer l'intégration Kchat opérationnelle.
             *
             * On ne dépend plus de la visibilité de la classe concrète:
             * findAccessibleMethod() sait traverser interfaces/superclasses
             * et rend le Method accessible sous Java 8.
             */
            updateNametagMethod =
                    findAccessibleMethod(
                            nametagManager.getClass(),
                            "updateNametag",
                            Player.class
                    );

            if (updateNametagMethod == null) {
                throw new NoSuchMethodException(
                        "NametagManager#updateNametag(Player)"
                );
            }

            /*
             * Méthode OPTIONNELLE:
             * certaines versions Kchat ne possèdent pas
             * refreshAllNametags().
             *
             * Dans ce cas updateAllNametags() fera simplement un fallback
             * joueur par joueur avec updateNametag(Player).
             */
            refreshAllNametagsMethod =
                    findAccessibleMethod(
                            nametagManager.getClass(),
                            "refreshAllNametags"
                    );

            initialized = true;

        } catch (Throwable throwable) {
            initialized = false;

            plugin.getLogger().warning(
                    "Kchat hook incompatible: "
                            + throwable.getMessage()
            );
        }
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void updatePlayerNametag(
            Player player
    ) {
        if (!initialized
                || updateNametagMethod == null
                || player == null) {
            return;
        }

        try {
            invoke(
                    updateNametagMethod,
                    nametagManager,
                    player
            );

        } catch (Throwable throwable) {
            debug(
                    "updatePlayerNametag",
                    throwable
            );
        }
    }

    public void updateAllNametags() {
        if (!initialized) {
            return;
        }

        /*
         * Chemin natif si la version de Kchat expose
         * refreshAllNametags().
         */
        if (refreshAllNametagsMethod != null) {
            try {
                invoke(
                        refreshAllNametagsMethod,
                        nametagManager
                );

                return;

            } catch (Throwable throwable) {
                debug(
                        "refreshAllNametags",
                        throwable
                );
            }
        }

        /*
         * Fallback compatible anciennes/nouvelles variantes Kchat:
         * on rafraîchit seulement les joueurs actuellement connectés.
         */
        for (Player player
                : Bukkit.getOnlinePlayers()) {
            updatePlayerNametag(player);
        }
    }

    public String getFactionTag(
            Player player
    ) {
        FactionView faction =
                factionOf(player);

        return faction != null
                && faction.getTag() != null
                ? faction.getTag()
                : "";
    }

    public String getFactionName(
            Player player
    ) {
        FactionView faction =
                factionOf(player);

        return faction != null
                && faction.getName() != null
                ? faction.getName()
                : "";
    }

    public String getRolePrefix(
            Player player
    ) {
        PlayerView view =
                playerView(player);

        return view != null
                && view.getRolePrefix() != null
                ? view.getRolePrefix()
                : "";
    }

    public String getColoredTag(
            Player player,
            Player viewer
    ) {
        KfactionApiV2 api =
                api();

        if (api == null
                || player == null) {
            return "";
        }

        FactionView target =
                api.getPlayerFaction(
                        player.getUniqueId()
                );

        if (target == null) {
            return "";
        }

        FactionView observer =
                viewer != null
                        ? api.getPlayerFaction(
                                viewer.getUniqueId()
                        )
                        : null;

        if (observer == null) {
            return "&f"
                    + safe(target.getTag());
        }

        String relation =
                api.getRelation(
                        observer.getId(),
                        target.getId()
                );

        return relationColor(relation)
                + safe(target.getTag());
    }

    public String formatChat(
            Player player,
            String format
    ) {
        if (format == null) {
            return null;
        }

        return format
                .replace(
                        "{faction}",
                        getFactionTag(player)
                )
                .replace(
                        "{faction_role}",
                        getRolePrefix(player)
                );
    }

    private FactionView factionOf(
            Player player
    ) {
        KfactionApiV2 api =
                api();

        return api != null
                && player != null
                ? api.getPlayerFaction(
                        player.getUniqueId()
                )
                : null;
    }

    private PlayerView playerView(
            Player player
    ) {
        KfactionApiV2 api =
                api();

        return api != null
                && player != null
                ? api.getPlayer(
                        player.getUniqueId()
                )
                : null;
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

    /**
     * Cherche une méthode publique ou déclarée sans dépendre de la visibilité
     * de la classe d'implémentation.
     *
     * C'est important avec les bridges de plugins: une méthode peut être
     * publique alors que la classe concrète/proxy qui la porte ne l'est pas.
     */
    private static Method findAccessibleMethod(
            Class<?> type,
            String name,
            Class<?>... parameterTypes
    ) {
        if (type == null
                || name == null) {
            return null;
        }

        /*
         * 1) API publique normale.
         */
        try {
            Method method =
                    type.getMethod(
                            name,
                            parameterTypes
                    );

            makeAccessible(method);
            return method;

        } catch (NoSuchMethodException ignored) {
            // Fallbacks ci-dessous.
        }

        /*
         * 2) Interfaces publiques/privées implémentées.
         */
        Method interfaceMethod =
                findOnInterfaces(
                        type,
                        name,
                        parameterTypes
                );

        if (interfaceMethod != null) {
            return interfaceMethod;
        }

        /*
         * 3) Classe concrète + superclasses.
         */
        Class<?> current = type;

        while (current != null) {
            try {
                Method method =
                        current.getDeclaredMethod(
                                name,
                                parameterTypes
                        );

                makeAccessible(method);
                return method;

            } catch (NoSuchMethodException ignored) {
                current =
                        current.getSuperclass();
            }
        }

        return null;
    }

    private static Method findOnInterfaces(
            Class<?> type,
            String name,
            Class<?>... parameterTypes
    ) {
        if (type == null) {
            return null;
        }

        for (Class<?> interfaceType
                : type.getInterfaces()) {
            try {
                Method method =
                        interfaceType.getMethod(
                                name,
                                parameterTypes
                        );

                makeAccessible(method);
                return method;

            } catch (NoSuchMethodException ignored) {
                Method nested =
                        findOnInterfaces(
                                interfaceType,
                                name,
                                parameterTypes
                        );

                if (nested != null) {
                    return nested;
                }
            }
        }

        return findOnInterfaces(
                type.getSuperclass(),
                name,
                parameterTypes
        );
    }

    private static void makeAccessible(
            Method method
    ) {
        if (method == null) {
            return;
        }

        try {
            if (!method.isAccessible()) {
                method.setAccessible(true);
            }
        } catch (SecurityException ignored) {
            /*
             * Si un SecurityManager interdit setAccessible, on conserve le
             * Method. invoke() réussira quand même si la déclaration publique
             * est naturellement accessible.
             */
        }
    }

    private static Object invoke(
            Method method,
            Object target,
            Object... arguments
    ) throws Throwable {
        try {
            return method.invoke(
                    target,
                    arguments
            );

        } catch (InvocationTargetException exception) {
            Throwable cause =
                    exception.getCause();

            throw cause != null
                    ? cause
                    : exception;
        }
    }

    private static String relationColor(
            String relation
    ) {
        String value =
                relation != null
                        ? relation.toUpperCase(
                                Locale.ROOT
                        )
                        : "NEUTRAL";

        if ("MEMBER".equals(value)) {
            return "&2";
        }

        if ("ALLY".equals(value)) {
            return "&d";
        }

        if ("TRUCE".equals(value)) {
            return "&e";
        }

        if ("ENEMY".equals(value)) {
            return "&c";
        }

        return "&f";
    }

    private static String safe(
            String value
    ) {
        return value != null
                ? value
                : "";
    }

    private void debug(
            String operation,
            Throwable throwable
    ) {
        if (plugin.isDebugMode()) {
            plugin.getLogger().warning(
                    "[Debug] Kchat "
                            + operation
                            + ": "
                            + throwable.getMessage()
            );
        }
    }
}
