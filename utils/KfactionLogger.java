package me.krunsh.kfaction.utils;

import me.krunsh.kfaction.Kfaction;

/**
 * Logger console léger pour Kfaction.
 *
 * Objectifs:
 * - rendu lisible sous PandaSpigot / Log4j2;
 * - INFO compact en production;
 * - détails uniquement lorsque debug=true;
 * - aucune fausse alerte WARN pour un état normal.
 *
 * Cette classe ne remplace pas java.util.logging.Logger: elle normalise
 * seulement la présentation des messages Kfaction.
 */
public final class KfactionLogger {

    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";

    private static final String GOLD = "\u001B[0;33m";
    private static final String YELLOW = "\u001B[1;33m";
    private static final String GREEN = "\u001B[0;32m";
    private static final String AQUA = "\u001B[0;36m";
    private static final String WHITE = "\u001B[0;37m";
    private static final String GRAY = "\u001B[0;90m";
    private static final String RED = "\u001B[0;31m";
    private static final String PURPLE = "\u001B[0;35m";

    private KfactionLogger() {
    }

    public static void info(
            Kfaction plugin,
            String message
    ) {
        if (plugin == null) {
            return;
        }

        plugin.getLogger().info(
                color(
                        plugin,
                        WHITE,
                        message
                )
        );
    }

    public static void success(
            Kfaction plugin,
            String message
    ) {
        if (plugin == null) {
            return;
        }

        plugin.getLogger().info(
                color(
                        plugin,
                        GREEN,
                        symbol(plugin, "✔ ")
                                + message
                )
        );
    }

    public static void warn(
            Kfaction plugin,
            String message
    ) {
        if (plugin == null) {
            return;
        }

        plugin.getLogger().warning(
                color(
                        plugin,
                        YELLOW,
                        symbol(plugin, "⚠ ")
                                + message
                )
        );
    }

    public static void error(
            Kfaction plugin,
            String message
    ) {
        if (plugin == null) {
            return;
        }

        plugin.getLogger().severe(
                color(
                        plugin,
                        RED,
                        symbol(plugin, "✖ ")
                                + message
                )
        );
    }

    public static void reload(
            Kfaction plugin,
            String message
    ) {
        if (plugin == null) {
            return;
        }

        plugin.getLogger().info(
                color(
                        plugin,
                        PURPLE,
                        symbol(plugin, "↻ ")
                                + message
                )
        );
    }

    public static void debug(
            Kfaction plugin,
            String message
    ) {
        if (plugin == null
                || !plugin.isDebugMode()) {
            return;
        }

        plugin.getLogger().info(
                color(
                        plugin,
                        GRAY,
                        "[DEBUG] "
                                + message
                )
        );
    }

    /**
     * Résumé compact prévu pour Kfaction#onEnable.
     *
     * L'intégration globale du startup se fera dans le lot console/bootstrap,
     * mais la primitive est disponible dès maintenant.
     */
    public static void startupComplete(
            Kfaction plugin,
            long elapsedMillis,
            int factions,
            int claims,
            int zones
    ) {
        success(
                plugin,
                "Prêt en "
                        + elapsedMillis
                        + " ms"
                        + " | factions="
                        + factions
                        + " | claims="
                        + claims
                        + " | zones="
                        + zones
        );
    }

    public static void section(
            Kfaction plugin,
            String title
    ) {
        if (plugin == null
                || !plugin.isDebugMode()) {
            return;
        }

        plugin.getLogger().info(
                color(
                        plugin,
                        AQUA,
                        "── "
                                + title
                                + " ──"
                )
        );
    }

    public static void banner(
            Kfaction plugin,
            String version
    ) {
        if (plugin == null) {
            return;
        }

        String value =
                "Kfaction "
                        + (version != null
                                ? "v" + version
                                : "");

        plugin.getLogger().info(
                color(
                        plugin,
                        GOLD + BOLD,
                        "━━━━━━━━━━ "
                                + value
                                + " ━━━━━━━━━━"
                )
        );
    }

    private static String symbol(
            Kfaction plugin,
            String value
    ) {
        if (plugin == null) {
            return "";
        }

        try {
            if (plugin.getConfigManager() != null
                    && !plugin.getConfigManager()
                            .getBoolean(
                                    "console.symbols.enabled",
                                    true
                            )) {
                return "";
            }
        } catch (Throwable ignored) {
            // Le logger doit rester utilisable pendant le bootstrap config.
        }

        return value;
    }

    private static String color(
            Kfaction plugin,
            String ansi,
            String message
    ) {
        String safe =
                message != null
                        ? message
                        : "";

        if (plugin == null) {
            return safe;
        }

        try {
            if (plugin.getConfigManager() != null
                    && !plugin.getConfigManager()
                            .getBoolean(
                                    "console.colors.enabled",
                                    true
                            )) {
                return safe;
            }
        } catch (Throwable ignored) {
            // Pendant le tout début du bootstrap, ANSI reste activé par défaut.
        }

        return ansi
                + safe
                + RESET;
    }
}