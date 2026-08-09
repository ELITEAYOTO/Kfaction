package me.krunsh.kfaction.hooks;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.api.v2.FactionView;
import me.krunsh.kfaction.api.v2.KfactionApiV2;
import me.krunsh.kfaction.api.v2.KfactionApis;
import me.krunsh.kfaction.api.v2.QuestView;
import me.krunsh.kfaction.api.v2.RewardLevelView;

/**
 * ContentProviders Kgui V2-ready.
 *
 * Toujours reflection-based pour rester compatible avec Kgui V1,
 * mais aucune lecture directe du domaine/managers Kfaction.
 */
public final class KguiContentProviders {

    private static final AtomicBoolean REGISTERED =
            new AtomicBoolean();

    private final Kfaction plugin;

    private boolean registered;

    private volatile KfactionApiV2 cachedApi;

    public KguiContentProviders(
            Kfaction plugin
    ) {
        this.plugin = plugin;
    }

    public void register() {
        if (registered
                || REGISTERED.get()) {
            registered = true;
            return;
        }

        Plugin kguiPlugin =
                Bukkit.getPluginManager()
                        .getPlugin("Kgui");

        if (kguiPlugin == null
                || !kguiPlugin.isEnabled()) {
            return;
        }

        if (!REGISTERED.compareAndSet(
                false,
                true
        )) {
            registered = true;
            return;
        }

        try {
            Method getContentProviderManager =
                    kguiPlugin.getClass()
                            .getMethod(
                                    "getContentProviderManager"
                            );

            Object manager =
                    getContentProviderManager
                            .invoke(
                                    kguiPlugin
                            );

            if (manager == null) {
                throw new IllegalStateException(
                        "ContentProviderManager null"
                );
            }

            ClassLoader loader =
                    kguiPlugin.getClass()
                            .getClassLoader();

            Class<?> providerInterface =
                    loader.loadClass(
                            "me.krunsh.kgui.api.DynamicContentProvider"
                    );

            Class<?> builderClass =
                    loader.loadClass(
                            "me.krunsh.kgui.api.DynamicItem$Builder"
                    );

            Method registerMethod =
                    manager.getClass()
                            .getMethod(
                                    "register",
                                    String.class,
                                    providerInterface
                            );

            registerMethod.invoke(
                    manager,
                    "kfaction_quests",
                    createQuestProvider(
                            providerInterface,
                            builderClass
                    )
            );

            registerMethod.invoke(
                    manager,
                    "kfaction_rewards",
                    createRewardProvider(
                            providerInterface,
                            builderClass
                    )
            );

            registered = true;

            plugin.logInfo(
                    "&7ContentProviders Kgui V2-ready enregistrés "
                            + "(kfaction_quests, kfaction_rewards)"
            );

        } catch (Throwable throwable) {
            REGISTERED.set(false);
            registered = false;

            plugin.getLogger().warning(
                    "Impossible d'enregistrer les ContentProviders Kgui: "
                            + throwable.getClass()
                                    .getSimpleName()
                            + ": "
                            + throwable.getMessage()
            );

            if (plugin.isDebugMode()) {
                plugin.getLogger().log(
                        java.util.logging.Level.WARNING,
                        "Stacktrace ContentProviders Kgui",
                        throwable
                );
            }
        }
    }

    private Object createQuestProvider(
            final Class<?> providerInterface,
            final Class<?> builderClass
    ) {
        return Proxy.newProxyInstance(
                providerInterface.getClassLoader(),
                new Class<?>[] {
                        providerInterface
                },
                (proxy, method, args) -> {
                    String name =
                            method.getName();

                    if ("getContent".equals(name)) {
                        Player player =
                                args != null
                                && args.length > 0
                                        ? (Player) args[0]
                                        : null;

                        @SuppressWarnings("unchecked")
                        Map<String, String> providerArgs =
                                args != null
                                && args.length > 1
                                && args[1] instanceof Map
                                        ? (Map<String, String>) args[1]
                                        : Collections.<String, String>emptyMap();

                        return buildQuestItems(
                                player,
                                providerArgs,
                                builderClass
                        );
                    }

                    if ("getId".equals(name)) {
                        return "kfaction_quests";
                    }

                    if ("onClick".equals(name)) {
                        return null;
                    }

                    return objectMethod(
                            proxy,
                            method,
                            args,
                            "KfactionQuestProvider"
                    );
                }
        );
    }

    private Object createRewardProvider(
            final Class<?> providerInterface,
            final Class<?> builderClass
    ) {
        return Proxy.newProxyInstance(
                providerInterface.getClassLoader(),
                new Class<?>[] {
                        providerInterface
                },
                (proxy, method, args) -> {
                    String name =
                            method.getName();

                    if ("getContent".equals(name)) {
                        Player player =
                                args != null
                                && args.length > 0
                                        ? (Player) args[0]
                                        : null;

                        @SuppressWarnings("unchecked")
                        Map<String, String> providerArgs =
                                args != null
                                && args.length > 1
                                && args[1] instanceof Map
                                        ? (Map<String, String>) args[1]
                                        : Collections.<String, String>emptyMap();

                        return buildRewardItems(
                                player,
                                providerArgs,
                                builderClass
                        );
                    }

                    if ("getId".equals(name)) {
                        return "kfaction_rewards";
                    }

                    if ("onClick".equals(name)) {
                        return null;
                    }

                    return objectMethod(
                            proxy,
                            method,
                            args,
                            "KfactionRewardProvider"
                    );
                }
        );
    }

    private List<Object> buildQuestItems(
            Player player,
            Map<String, String> ignoredArgs,
            Class<?> builderClass
    ) throws Exception {
        KfactionApiV2 api =
                api();

        if (api == null
                || player == null) {
            return Collections.emptyList();
        }

        FactionView faction =
                api.getPlayerFaction(
                        player.getUniqueId()
                );

        if (faction == null) {
            return Collections.emptyList();
        }

        List<QuestView> quests =
                api.getProgressionQuests(
                        faction.getId()
                );

        if (quests.isEmpty()) {
            return Collections.emptyList();
        }

        List<Object> items =
                new ArrayList<Object>(
                        quests.size()
                );

        BuilderMethods methods =
                new BuilderMethods(
                        builderClass
                );

        for (QuestView quest : quests) {
            Object builder =
                    builderClass.newInstance();

            methods.material.invoke(
                    builder,
                    quest.getIconMaterial()
            );

            methods.data.invoke(
                    builder,
                    Integer.valueOf(
                            quest.getIconData()
                    )
            );

            methods.name.invoke(
                    builder,
                    (quest.isCompleted()
                            ? "§a✔ "
                            : "")
                            + "§f"
                            + render(
                                    quest.getDisplayName(),
                                    quest
                            )
            );

            methods.glow.invoke(
                    builder,
                    Boolean.valueOf(
                            quest.isCompleted()
                    )
            );

            List<String> lore =
                    new ArrayList<String>();

            for (String line : quest.getLore()) {
                lore.add(
                        render(
                                line,
                                quest
                        )
                );
            }

            if (!lore.isEmpty()) {
                lore.add("");
            }

            if (quest.isCompleted()) {
                lore.add(
                        "§a§l✔ Quête obligatoire complétée"
                );
            } else {
                lore.add(
                        "§7Progression:"
                );

                lore.add(
                        progressBar(
                                quest.getPercent(),
                                20
                        )
                );

                lore.add(
                        "§e"
                                + quest.getProgress()
                                + "§7/§e"
                                + quest.getRequired()
                                + " §7("
                                + quest.getPercent()
                                + "%)"
                );
            }

            methods.lore.invoke(
                    builder,
                    (Object) lore.toArray(
                            new String[
                                    lore.size()
                            ]
                    )
            );

            items.add(
                    methods.build.invoke(
                            builder
                    )
            );
        }

        return items;
    }

    private List<Object> buildRewardItems(
            Player player,
            Map<String, String> ignoredArgs,
            Class<?> builderClass
    ) throws Exception {
        KfactionApiV2 api =
                api();

        if (api == null
                || player == null) {
            return Collections.emptyList();
        }

        FactionView faction =
                api.getPlayerFaction(
                        player.getUniqueId()
                );

        if (faction == null) {
            return Collections.emptyList();
        }

        List<RewardLevelView> levels =
                api.getRewardLevels(
                        faction.getId()
                );

        BuilderMethods methods =
                new BuilderMethods(
                        builderClass
                );

        List<Object> items =
                new ArrayList<Object>(
                        levels.size()
                );

        for (RewardLevelView level : levels) {
            Object builder =
                    builderClass.newInstance();

            methods.material.invoke(
                    builder,
                    "STAINED_GLASS_PANE"
            );

            if (level.isUnlocked()) {
                methods.data.invoke(
                        builder,
                        Integer.valueOf(5)
                );

                methods.name.invoke(
                        builder,
                        "§a§l✔ Niveau "
                                + level.getLevel()
                );

                methods.glow.invoke(
                        builder,
                        Boolean.TRUE
                );

            } else if (level.isCurrent()) {
                methods.data.invoke(
                        builder,
                        Integer.valueOf(1)
                );

                methods.name.invoke(
                        builder,
                        "§6§l⚡ Niveau "
                                + level.getLevel()
                                + " §7(En cours)"
                );

                methods.glow.invoke(
                        builder,
                        Boolean.FALSE
                );

            } else {
                methods.data.invoke(
                        builder,
                        Integer.valueOf(14)
                );

                methods.name.invoke(
                        builder,
                        "§c§l✖ Niveau "
                                + level.getLevel()
                );

                methods.glow.invoke(
                        builder,
                        Boolean.FALSE
                );
            }

            List<String> lore =
                    new ArrayList<String>();

            lore.add("");
            lore.add(
                    "§7Récompenses à l'entrée:"
            );

            if (level.getRewards()
                    .isEmpty()) {
                lore.add(
                        "§8▸ §7Aucune récompense définie"
                );
            } else {
                for (String reward
                        : level.getRewards()) {
                    lore.add(
                            "§8▸ "
                                    + (level.isLocked()
                                            ? "§c✖ "
                                            : "§a✔ ")
                                    + safe(reward)
                                            .replace(
                                                    "&",
                                                    "§"
                                            )
                    );
                }
            }

            lore.add("");

            if (level.isUnlocked()) {
                lore.add(
                        "§a✔ Débloqué!"
                );
            } else if (level.isCurrent()) {
                lore.add(
                        "§6⚡ Quêtes en cours"
                );
            } else {
                lore.add(
                        "§c✖ Verrouillé"
                );
            }

            methods.lore.invoke(
                    builder,
                    (Object) lore.toArray(
                            new String[
                                    lore.size()
                            ]
                    )
            );

            items.add(
                    methods.build.invoke(
                            builder
                    )
            );
        }

        return items;
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

    private static Object objectMethod(
            Object proxy,
            Method method,
            Object[] args,
            String name
    ) {
        if ("toString".equals(
                method.getName()
        )) {
            return name;
        }

        if ("hashCode".equals(
                method.getName()
        )) {
            return Integer.valueOf(
                    System.identityHashCode(
                            proxy
                    )
            );
        }

        if ("equals".equals(
                method.getName()
        )) {
            return Boolean.valueOf(
                    args != null
                            && args.length == 1
                            && proxy == args[0]
            );
        }

        return null;
    }

    private static String render(
            String value,
            QuestView quest
    ) {
        return safe(value)
                .replace(
                        "&",
                        "§"
                )
                .replace(
                        "{progress}",
                        String.valueOf(
                                quest.getProgress()
                        )
                )
                .replace(
                        "{amount}",
                        String.valueOf(
                                quest.getRequired()
                        )
                )
                .replace(
                        "{remaining}",
                        String.valueOf(
                                quest.getRemaining()
                        )
                )
                .replace(
                        "{percent}",
                        String.valueOf(
                                quest.getPercent()
                        )
                );
    }

    private static String progressBar(
            int percent,
            int length
    ) {
        int filled =
                Math.max(
                        0,
                        Math.min(
                                length,
                                percent * length / 100
                        )
                );

        StringBuilder value =
                new StringBuilder(
                        "§a"
                );

        for (int index = 0;
                index < length;
                index++) {
            if (index == filled) {
                value.append(
                        "§7"
                );
            }

            value.append("▌");
        }

        return value.toString();
    }

    private static String safe(
            String value
    ) {
        return value != null
                ? value
                : "";
    }

    public boolean isRegistered() {
        return registered;
    }

    private static final class BuilderMethods {

        private final Method material;
        private final Method name;
        private final Method data;
        private final Method lore;
        private final Method glow;
        private final Method build;

        private BuilderMethods(
                Class<?> builderClass
        ) throws NoSuchMethodException {
            material =
                    builderClass.getMethod(
                            "material",
                            String.class
                    );

            name =
                    builderClass.getMethod(
                            "name",
                            String.class
                    );

            data =
                    builderClass.getMethod(
                            "data",
                            int.class
                    );

            lore =
                    builderClass.getMethod(
                            "lore",
                            String[].class
                    );

            glow =
                    builderClass.getMethod(
                            "glow",
                            boolean.class
                    );

            build =
                    builderClass.getMethod(
                            "build"
                    );
        }
    }
}
