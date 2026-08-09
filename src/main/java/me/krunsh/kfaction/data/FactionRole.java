package me.krunsh.kfaction.data;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Rôles internes d'une faction.
 *
 * IMPORTANT :
 * - l'ordre des constantes définit la hiérarchie ;
 * - les noms Java restent stables pour le stockage et l'API ;
 * - les noms d'affichage pourront être déplacés vers le futur système de locale.
 *
 * Hiérarchie V2 :
 * RECRUIT < MEMBER < OFFICER < MODERATOR < COLEADER < LEADER
 */
public enum FactionRole {

    RECRUIT(0, "Recrue", "recruit", ""),
    MEMBER(100, "Membre", "member", ""),
    OFFICER(200, "Officier", "officer", "&a✦ "),
    MODERATOR(300, "Modérateur", "moderator", "&e✦ "),
    COLEADER(400, "Co-Leader", "coleader", "&6★ "),
    LEADER(500, "Leader", "leader", "&c✮ ");

    private final int priority;
    private final String displayName;
    private final String configKey;
    private final String prefix;

    FactionRole(int priority, String displayName, String configKey, String prefix) {
        this.priority = priority;
        this.displayName = displayName;
        this.configKey = configKey;
        this.prefix = prefix;
    }

    /**
     * @return priorité du rang. Plus la valeur est haute, plus le rang est élevé.
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Nom d'affichage par défaut.
     *
     * Sera remplacé plus tard par le système de locale/config V2.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Clé stable utilisée dans les configurations.
     */
    public String getConfigKey() {
        return configKey;
    }

    /**
     * Préfixe d'affichage par défaut.
     *
     * Sera lui aussi rendu configurable dans la V2.
     */
    public String getPrefix() {
        return prefix;
    }

    public boolean isAtLeast(FactionRole other) {
        return other != null && this.priority >= other.priority;
    }

    public boolean isHigherThan(FactionRole other) {
        return other != null && this.priority > other.priority;
    }

    public boolean isLowerThan(FactionRole other) {
        return other != null && this.priority < other.priority;
    }

    /**
     * @return rang immédiatement supérieur, ou null pour LEADER.
     */
    public FactionRole getNextRole() {
        int nextOrdinal = ordinal() + 1;
        FactionRole[] roles = values();
        return nextOrdinal < roles.length ? roles[nextOrdinal] : null;
    }

    /**
     * @return rang immédiatement inférieur, ou null pour RECRUIT.
     */
    public FactionRole getPreviousRole() {
        int previousOrdinal = ordinal() - 1;
        return previousOrdinal >= 0 ? values()[previousOrdinal] : null;
    }

    /**
     * Promotion classique de faction.
     *
     * Le passage COLEADER -> LEADER doit rester une vraie opération de transfert
     * de leadership et ne doit pas être réalisé par une simple promotion.
     */
    public boolean canBePromotedNormally() {
        return this != COLEADER && this != LEADER;
    }

    /**
     * Le LEADER ne peut pas être rétrogradé par la commande standard.
     */
    public boolean canBeDemotedNormally() {
        return this != RECRUIT && this != LEADER;
    }

    /**
     * Résout une clé de configuration.
     *
     * Compatibilité conservée : RECRUIT est retourné si la valeur est inconnue.
     */
    public static FactionRole fromConfigKey(String key) {
        FactionRole role = parse(key);
        return role != null ? role : RECRUIT;
    }

    /**
     * Parse un rôle depuis une entrée utilisateur/config.
     *
     * Les commandes officielles resteront en anglais, mais quelques alias
     * français sont acceptés gratuitement pour la compatibilité/confort.
     *
     * @return rôle correspondant, ou null si inconnu.
     */
    public static FactionRole parse(String input) {
        if (input == null) {
            return null;
        }

        String value = normalize(input);
        if (value.isEmpty()) {
            return null;
        }

        switch (value) {
            case "recruit":
            case "recrue":
            case "r":
                return RECRUIT;

            case "member":
            case "membre":
            case "m":
                return MEMBER;

            case "officer":
            case "officier":
            case "off":
            case "o":
                return OFFICER;

            case "moderator":
            case "moderateur":
            case "mod":
                return MODERATOR;

            case "coleader":
            case "co-leader":
            case "co_leader":
            case "colead":
            case "co":
            case "cl":
                return COLEADER;

            case "leader":
            case "lead":
            case "l":
            case "chef":
                return LEADER;

            default:
                return null;
        }
    }

    private static String normalize(String input) {
        String lower = input.trim().toLowerCase(Locale.ROOT);
        String normalized = Normalizer.normalize(lower, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "");
    }
}
