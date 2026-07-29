package me.krunsh.kfaction.data;

/**
 * Rôles/Rangs au sein d'une faction
 * Ordonnés par priorité croissante
 */
public enum FactionRole {
    
    RECRUIT(0, "Recrue", "recruit", ""),
    MEMBER(100, "Membre", "member", ""),
    MODERATOR(200, "Modérateur", "moderator", "&e✦ "),
    COLEADER(300, "Sous-Chef", "coleader", "&6★ "),
    LEADER(400, "Chef", "leader", "&c✮ ");
    
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
     * @return Priorité du rang (plus haut = plus de pouvoir)
     */
    public int getPriority() {
        return priority;
    }
    
    /**
     * @return Nom d'affichage traduit
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * @return Clé de configuration YAML
     */
    public String getConfigKey() {
        return configKey;
    }
    
    /**
     * @return Préfixe à afficher devant le nom
     */
    public String getPrefix() {
        return prefix;
    }
    
    /**
     * Vérifie si ce rang est au moins égal à un autre
     * @param other L'autre rang à comparer
     * @return true si ce rang >= other
     */
    public boolean isAtLeast(FactionRole other) {
        return this.priority >= other.priority;
    }
    
    /**
     * Vérifie si ce rang est supérieur à un autre
     * @param other L'autre rang à comparer
     * @return true si ce rang > other
     */
    public boolean isHigherThan(FactionRole other) {
        return this.priority > other.priority;
    }
    
    /**
     * Obtient le rang suivant (promotion)
     * @return Le rang supérieur ou null si déjà LEADER
     */
    public FactionRole getNextRole() {
        FactionRole[] roles = values();
        for (int i = 0; i < roles.length - 1; i++) {
            if (roles[i] == this) {
                return roles[i + 1];
            }
        }
        return null; // Déjà LEADER
    }
    
    /**
     * Obtient le rang précédent (rétrogradation)
     * @return Le rang inférieur ou null si déjà RECRUIT
     */
    public FactionRole getPreviousRole() {
        FactionRole[] roles = values();
        for (int i = 1; i < roles.length; i++) {
            if (roles[i] == this) {
                return roles[i - 1];
            }
        }
        return null; // Déjà RECRUIT
    }
    
    /**
     * Obtient un rôle par sa clé de configuration
     * @param key La clé (ex: "recruit", "member")
     * @return Le rôle ou RECRUIT par défaut
     */
    public static FactionRole fromConfigKey(String key) {
        for (FactionRole role : values()) {
            if (role.configKey.equalsIgnoreCase(key)) {
                return role;
            }
        }
        return RECRUIT;
    }
}
