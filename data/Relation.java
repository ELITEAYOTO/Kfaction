package me.krunsh.kfaction.data;

import org.bukkit.ChatColor;

/**
 * Types de relations entre factions
 */
public enum Relation {
    
    /**
     * Relation neutre par défaut
     */
    NEUTRAL("Neutre", ChatColor.WHITE, 'f', 1.0, false, false),
    
    /**
     * Relation d'alliance - pas de PvP, partage de territoire limité
     */
    ALLY("Allié", ChatColor.LIGHT_PURPLE, 'd', 0.0, true, true),
    
    /**
     * Relation de trève temporaire - pas de PvP
     */
    TRUCE("Trève", ChatColor.YELLOW, 'e', 0.5, false, false),
    
    /**
     * Relation ennemie - PvP encouragé, bonus de dégâts
     */
    ENEMY("Ennemi", ChatColor.RED, 'c', 1.5, false, false),
    
    /**
     * Faction de soi-même (membres de la même faction)
     */
    MEMBER("Membre", ChatColor.GREEN, 'a', 0.0, true, true);
    
    private final String displayName;
    private final ChatColor color;
    private final char colorCode;
    private final double damageMultiplier;
    private final boolean friendlyFire;
    private final boolean canAccessTerritory;
    
    Relation(String displayName, ChatColor color, char colorCode, double damageMultiplier, 
             boolean friendlyFire, boolean canAccessTerritory) {
        this.displayName = displayName;
        this.color = color;
        this.colorCode = colorCode;
        this.damageMultiplier = damageMultiplier;
        this.friendlyFire = friendlyFire;
        this.canAccessTerritory = canAccessTerritory;
    }
    
    /**
     * @return Nom d'affichage de la relation
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * @return Couleur Bukkit associée
     */
    public ChatColor getColor() {
        return color;
    }
    
    /**
     * @return Code couleur Minecraft (a-f, 0-9)
     */
    public char getColorCode() {
        return colorCode;
    }
    
    /**
     * @return Préfixe Minecraft avec le code couleur
     */
    public String getColorPrefix() {
        return "&" + colorCode;
    }
    
    /**
     * @return Multiplicateur de dégâts pour cette relation
     */
    public double getDamageMultiplier() {
        return damageMultiplier;
    }
    
    /**
     * @return true si le friendly fire est désactivé
     */
    public boolean isFriendlyFireOff() {
        return !friendlyFire;
    }
    
    /**
     * @return true si l'accès au territoire est autorisé
     */
    public boolean canAccessTerritory() {
        return canAccessTerritory;
    }
    
    /**
     * Vérifie si c'est une relation positive
     * @return true pour ALLY, TRUCE, MEMBER
     */
    public boolean isPositive() {
        return this == ALLY || this == TRUCE || this == MEMBER;
    }
    
    /**
     * Vérifie si c'est une relation négative
     * @return true pour ENEMY
     */
    public boolean isNegative() {
        return this == ENEMY;
    }
    
    /**
     * @return true si le PvP est autorisé dans cette relation
     */
    public boolean isPvPAllowed() {
        return this == NEUTRAL || this == ENEMY;
    }
    
    /**
     * Obtient la relation par son nom de configuration
     * @param name Le nom (insensible à la casse)
     * @return La relation ou NEUTRAL par défaut
     */
    public static Relation fromString(String name) {
        if (name == null) return NEUTRAL;
        for (Relation relation : values()) {
            if (relation.name().equalsIgnoreCase(name)) {
                return relation;
            }
        }
        return NEUTRAL;
    }
    
    /**
     * Obtient la relation inverse pour les demandes
     * ALLY avec ALLY = ALLY confirmé
     * ALLY sans réciprocité = NEUTRAL
     */
    public Relation getReciprocal() {
        // Les relations sont symétriques
        return this;
    }
}
