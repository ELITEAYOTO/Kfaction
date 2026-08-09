package me.krunsh.kfaction.zones;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import me.krunsh.kfaction.permissions.TerritoryAction;

/**
 * Définition immutable d'une zone globale dynamique.
 *
 * Une zone est identifiée par un ID stable stocké en persistence.
 * Les champs d'affichage/règles peuvent être rechargés depuis config.yml
 * sans réécrire les chunks déjà assignés.
 */
public final class ZoneDefinition {

    public enum DefaultPolicy {
        ALLOW,
        DENY;

        public static DefaultPolicy parse(
                String value,
                DefaultPolicy fallback
        ) {
            if (value == null) {
                return fallback;
            }

            for (DefaultPolicy policy : values()) {
                if (policy.name().equalsIgnoreCase(
                        value.trim()
                )) {
                    return policy;
                }
            }

            return fallback;
        }
    }

    private static final Pattern VALID_ID =
            Pattern.compile(
                    "^[a-z0-9_-]{1,32}$"
            );

    private final String id;
    private final String displayName;
    private final String color;
    private final String mapSymbol;
    private final String title;
    private final String subtitle;
    private final String enterMessage;
    private final boolean pvpAllowed;
    private final DefaultPolicy defaultPolicy;
    private final EnumSet<TerritoryAction> allowedActions;
    private final EnumSet<TerritoryAction> deniedActions;
    private final boolean configured;

    public ZoneDefinition(
            String id,
            String displayName,
            String color,
            String mapSymbol,
            String title,
            String subtitle,
            String enterMessage,
            boolean pvpAllowed,
            DefaultPolicy defaultPolicy,
            Set<TerritoryAction> allowedActions,
            Set<TerritoryAction> deniedActions,
            boolean configured
    ) {
        String normalized =
                normalizeId(id);

        if (normalized == null) {
            throw new IllegalArgumentException(
                    "Zone id invalide: "
                            + id
            );
        }

        this.id = normalized;
        this.displayName =
                clean(
                        displayName,
                        normalized
                );
        this.color =
                clean(
                        color,
                        "&f"
                );
        this.mapSymbol =
                clean(
                        mapSymbol,
                        "?"
                );
        this.title =
                clean(
                        title,
                        this.color
                                + this.displayName
                );
        this.subtitle =
                clean(
                        subtitle,
                        ""
                );
        this.enterMessage =
                clean(
                        enterMessage,
                        this.color
                                + "~ "
                                + this.displayName
                );
        this.pvpAllowed =
                pvpAllowed;
        this.defaultPolicy =
                defaultPolicy != null
                        ? defaultPolicy
                        : DefaultPolicy.DENY;
        this.allowedActions =
                copy(
                        allowedActions
                );
        this.deniedActions =
                copy(
                        deniedActions
                );
        this.configured =
                configured;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColor() {
        return color;
    }

    public String getMapSymbol() {
        return mapSymbol;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getEnterMessage() {
        return enterMessage;
    }

    public boolean isPvpAllowed() {
        return pvpAllowed;
    }

    public DefaultPolicy getDefaultPolicy() {
        return defaultPolicy;
    }

    public Set<TerritoryAction> getAllowedActions() {
        return Collections.unmodifiableSet(
                allowedActions
        );
    }

    public Set<TerritoryAction> getDeniedActions() {
        return Collections.unmodifiableSet(
                deniedActions
        );
    }

    public boolean isConfigured() {
        return configured;
    }

    public boolean isLegacySafezone() {
        return GlobalZoneType.SAFEZONE
                .getConfigKey()
                .equals(id);
    }

    public boolean isLegacyWarzone() {
        return GlobalZoneType.WARZONE
                .getConfigKey()
                .equals(id);
    }

    public boolean isActionAllowed(
            TerritoryAction action
    ) {
        if (action == null) {
            return false;
        }

        /*
         * DENY est volontairement prioritaire si une action apparaît dans
         * les deux listes par erreur de configuration.
         */
        if (deniedActions.contains(
                action
        )) {
            return false;
        }

        if (allowedActions.contains(
                action
        )) {
            return true;
        }

        return defaultPolicy
                == DefaultPolicy.ALLOW;
    }

    public static boolean isValidId(
            String id
    ) {
        return normalizeId(id) != null;
    }

    public static String normalizeId(
            String id
    ) {
        if (id == null) {
            return null;
        }

        String normalized =
                id.trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        if (!VALID_ID.matcher(
                normalized
        ).matches()) {
            return null;
        }

        return normalized;
    }

    /**
     * Définition fail-closed pour un zoneId persisté dont la section config
     * a été retirée.
     *
     * ENTER reste autorisé afin de ne pas piéger physiquement les joueurs;
     * les autres actions sont refusées et le PvP est désactivé.
     */
    public static ZoneDefinition orphan(
            String zoneId
    ) {
        String normalized =
                normalizeId(zoneId);

        if (normalized == null) {
            throw new IllegalArgumentException(
                    "Orphan zone id invalide"
            );
        }

        EnumSet<TerritoryAction> allowed =
                EnumSet.of(
                        TerritoryAction.ENTER
                );

        return new ZoneDefinition(
                normalized,
                normalized,
                "&c",
                "!",
                "&cZone inconnue",
                "&7Configuration manquante: "
                        + normalized,
                "&c~ Zone inconnue &7("
                        + normalized
                        + ")",
                false,
                DefaultPolicy.DENY,
                allowed,
                EnumSet.noneOf(
                        TerritoryAction.class
                ),
                false
        );
    }

    private static EnumSet<TerritoryAction> copy(
            Set<TerritoryAction> source
    ) {
        EnumSet<TerritoryAction> result =
                EnumSet.noneOf(
                        TerritoryAction.class
                );

        if (source != null) {
            result.addAll(source);
        }

        return result;
    }

    private static String clean(
            String value,
            String fallback
    ) {
        if (value == null) {
            return fallback;
        }

        String trimmed =
                value.trim();

        return trimmed.isEmpty()
                ? fallback
                : trimmed;
    }

    @Override
    public String toString() {
        return "ZoneDefinition{"
                + "id='"
                + id
                + '\''
                + ", displayName='"
                + displayName
                + '\''
                + ", pvpAllowed="
                + pvpAllowed
                + ", defaultPolicy="
                + defaultPolicy
                + ", configured="
                + configured
                + '}';
    }
}
