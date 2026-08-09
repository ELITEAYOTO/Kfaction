package me.krunsh.kfaction.diagnostics;

import java.util.Locale;

/**
 * Sous-ensembles disponibles pour /kf doctor.
 */
public enum DiagnosticScope {

    ALL,
    RUNTIME,
    STORAGE,
    AUDIT,
    INTEGRATIONS,
    INDEXES,
    PROGRESSION,
    ZONES;

    public static DiagnosticScope parse(
            String value
    ) {
        if (value == null
                || value.trim().isEmpty()) {
            return ALL;
        }

        String normalized =
                value.trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        if ("all".equals(normalized)
                || "full".equals(normalized)
                || "tout".equals(normalized)) {
            return ALL;
        }

        if ("runtime".equals(normalized)
                || "core".equals(normalized)
                || "system".equals(normalized)) {
            return RUNTIME;
        }

        if ("storage".equals(normalized)
                || "db".equals(normalized)
                || "database".equals(normalized)) {
            return STORAGE;
        }

        if ("audit".equals(normalized)
                || "logs".equals(normalized)) {
            return AUDIT;
        }

        if ("integrations".equals(normalized)
                || "hooks".equals(normalized)
                || "plugins".equals(normalized)) {
            return INTEGRATIONS;
        }

        if ("indexes".equals(normalized)
                || "index".equals(normalized)
                || "integrity".equals(normalized)) {
            return INDEXES;
        }

        if ("progression".equals(normalized)
                || "quests".equals(normalized)
                || "levels".equals(normalized)) {
            return PROGRESSION;
        }

        if ("zones".equals(normalized)
                || "zone".equals(normalized)
                || "global-zones".equals(normalized)) {
            return ZONES;
        }

        return null;
    }
}
