package me.krunsh.kfaction.zones;

import java.util.Locale;

/**
 * Types de zones globales V2.
 *
 * SAFEZONE/WARZONE gardent leurs IDs historiques uniquement comme façade de
 * compatibilité pour les anciennes APIs qui attendent encore une Faction.
 */
public enum GlobalZoneType {

    SAFEZONE(
            "safezone",
            "safezone"
    ),

    WARZONE(
            "warzone",
            "warzone"
    );

    private final String configKey;
    private final String legacyFactionId;

    GlobalZoneType(
            String configKey,
            String legacyFactionId
    ) {
        this.configKey = configKey;
        this.legacyFactionId = legacyFactionId;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getLegacyFactionId() {
        return legacyFactionId;
    }

    public static GlobalZoneType parse(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String normalized =
                value.trim()
                        .toLowerCase(Locale.ROOT);

        if ("safe".equals(normalized)
                || "safezone".equals(normalized)
                || "sz".equals(normalized)) {
            return SAFEZONE;
        }

        if ("war".equals(normalized)
                || "warzone".equals(normalized)
                || "wz".equals(normalized)) {
            return WARZONE;
        }

        for (GlobalZoneType type : values()) {
            if (type.name().equalsIgnoreCase(value)
                    || type.configKey.equalsIgnoreCase(value)
                    || type.legacyFactionId.equalsIgnoreCase(value)) {
                return type;
            }
        }

        return null;
    }
}
