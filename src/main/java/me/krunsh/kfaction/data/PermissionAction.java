package me.krunsh.kfaction.data;

/**
 * Actions qui peuvent être restreintes par les permissions de faction
 * Système complet de permissions granulaires
 */
public enum PermissionAction {
    
    // =========================================================================
    // CATÉGORIE: BLOCS - Actions de construction/destruction
    // =========================================================================
    BUILD("build", "Construire", "Placer des blocs", "blocs"),
    DESTROY("destroy", "Détruire", "Casser des blocs", "blocs"),
    SWITCH("switch", "Portes/Leviers", "Utiliser portes, leviers, boutons", "blocs"),
    CONTAINER("container", "Coffres", "Ouvrir coffres, fours, hoppers", "blocs"),
    FROST_WALK("frostwalk", "Givre", "Utiliser l'enchant Frost Walker", "blocs"),
    ITEM_FRAME("item_frame", "Cadres", "Interagir avec les cadres", "blocs"),
    ARMOR_STAND("armor_stand", "Armures", "Interagir avec les porte-armures", "blocs"),
    
    // =========================================================================
    // CATÉGORIE: SPAWNERS - Actions sur les spawners
    // =========================================================================
    SPAWNER_PLACE("spawner_place", "Poser spawner", "Poser des spawners", "spawners"),
    SPAWNER_BREAK("spawner_break", "Casser spawner", "Casser des spawners", "spawners"),
    SPAWNER_INTERACT("spawner_interact", "Ouvrir spawner", "Ouvrir le menu des spawners", "spawners"),
    SPAWNER_UPGRADE("spawner_upgrade", "Améliorer spawner", "Améliorer les spawners", "spawners"),
    
    // =========================================================================
    // CATÉGORIE: TNT - Actions avec les explosifs
    // =========================================================================
    TNT_PLACE("tnt_place", "Poser TNT", "Poser de la TNT", "tnt"),
    TNT_IGNITE("tnt_ignite", "Activer TNT", "Allumer la TNT", "tnt"),
    TNT_DEPOSIT("tnt_deposit", "Déposer TNT", "Déposer dans la banque TNT", "tnt"),
    TNT_WITHDRAW("tnt_withdraw", "Retirer TNT", "Retirer de la banque TNT", "tnt"),
    TNT_FILL("tnt_fill", "Remplir cannons", "Utiliser /f tntfill", "tnt"),
    
    // =========================================================================
    // CATÉGORIE: MEMBRES - Gestion des membres
    // =========================================================================
    INVITE("invite", "Inviter", "Inviter de nouveaux membres", "membres"),
    KICK("kick", "Exclure", "Exclure des membres", "membres"),
    PROMOTE("promote", "Promouvoir", "Promouvoir des membres", "membres"),
    DEMOTE("demote", "Rétrograder", "Rétrograder des membres", "membres"),
    BAN("ban", "Bannir", "Bannir des joueurs de la faction", "membres"),
    UNBAN("unban", "Débannir", "Retirer un ban de faction", "membres"),
    
    // =========================================================================
    // CATÉGORIE: TERRITOIRE - Gestion du claim
    // =========================================================================
    CLAIM("claim", "Claim", "Revendiquer du territoire", "territoire"),
    UNCLAIM("unclaim", "Unclaim", "Abandonner du territoire", "territoire"),
    AUTOCLAIM("autoclaim", "Auto-Claim", "Utiliser le claim automatique", "territoire"),
    SETHOME("sethome", "Définir Home", "Définir le home de faction", "territoire"),
    HOME("home", "Téléport Home", "Se téléporter au home", "territoire"),
    SETWARP("setwarp", "Créer Warp", "Créer des warps de faction", "territoire"),
    DELWARP("delwarp", "Suppr. Warp", "Supprimer des warps", "territoire"),
    WARP("warp", "Téléport Warp", "Utiliser les warps de faction", "territoire"),
    
    // =========================================================================
    // CATÉGORIE: ÉCONOMIE - Gestion de l'argent
    // =========================================================================
    DEPOSIT("deposit", "Déposer", "Déposer de l'argent", "economie"),
    WITHDRAW("withdraw", "Retirer", "Retirer de l'argent", "economie"),
    PAY("pay", "Payer", "Payer une autre faction", "economie"),
    
    // =========================================================================
    // CATÉGORIE: DIPLOMATIE - Relations entre factions
    // =========================================================================
    RELATION_ALLY("relation_ally", "Alliances", "Proposer/accepter des alliances", "diplomatie"),
    RELATION_ENEMY("relation_enemy", "Ennemis", "Déclarer des ennemis", "diplomatie"),
    RELATION_NEUTRAL("relation_neutral", "Neutralité", "Revenir à la neutralité", "diplomatie"),
    RELATION_TRUCE("relation_truce", "Trêve", "Proposer/accepter des trêves", "diplomatie"),
    
    // =========================================================================
    // CATÉGORIE: ADMIN - Actions administratives
    // =========================================================================
    RENAME("rename", "Renommer", "Changer le nom de la faction", "admin"),
    DESCRIPTION("description", "Description", "Changer la description", "admin"),
    TAG("tag", "Tag", "Changer le tag de la faction", "admin"),
    PERMS("perms", "Permissions", "Modifier les permissions", "admin"),
    VIEW_LOGS("view_logs", "Voir logs", "Consulter les logs de faction", "admin"),
    DISBAND("disband", "Dissoudre", "Dissoudre la faction", "admin"),
    TRANSFER("transfer", "Transférer", "Transférer le leadership", "admin"),
    FLY("fly", "Vol", "Utiliser le fly en territoire", "admin"),
    CHEST("chest", "Coffre Faction", "Accéder au coffre de faction", "admin"),
    
    // =========================================================================
    // CATÉGORIE: RELATIONS - Permissions accordées aux relations
    // =========================================================================
    ALLY("ally", "Allié", "Actions pour les alliés", "relations"),
    ENEMY("enemy", "Ennemi", "Actions pour les ennemis", "relations"),
    NEUTRAL("neutral", "Neutre", "Actions pour les neutres", "relations"),
    TRUCE("truce", "Trêve", "Actions pour les factions en trêve", "relations"),
    
    // =========================================================================
    // CATÉGORIE: LEGACY - Rétrocompatibilité (ne pas utiliser dans les menus)
    // =========================================================================
    @Deprecated
    SPAWNER("spawner", "Spawners", "Interagir avec les spawners (legacy)", "legacy"),
    @Deprecated
    TNT("tnt", "TNT", "Placer et activer la TNT (legacy)", "legacy");
    
    private final String configKey;
    private final String displayName;
    private final String description;
    private final String category;
    
    PermissionAction(String configKey, String displayName, String description, String category) {
        this.configKey = configKey;
        this.displayName = displayName;
        this.description = description;
        this.category = category;
    }
    
    /**
     * @return Clé utilisée dans la configuration YAML
     */
    public String getConfigKey() {
        return configKey;
    }
    
    /**
     * @return Nom d'affichage traduit
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * @return Description de l'action
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * @return Catégorie de la permission (blocs, spawners, tnt, membres, territoire, economie, diplomatie, admin, relations)
     */
    public String getCategory() {
        return category;
    }
    
    /**
     * Vérifie si c'est une action territoriale (blocs)
     * @return true si c'est une action de territoire
     */
    public boolean isTerritory() {
        return "blocs".equals(category);
    }
    
    /**
     * Vérifie si c'est une action de gestion de membres
     * @return true si c'est une action de gestion
     */
    public boolean isMemberManagement() {
        return "membres".equals(category);
    }
    
    /**
     * Vérifie si c'est une action économique
     * @return true si c'est une action économique
     */
    public boolean isEconomic() {
        return "economie".equals(category);
    }
    
    /**
     * Vérifie si c'est une action diplomatique
     * @return true si c'est une action diplomatique
     */
    public boolean isDiplomatic() {
        return "diplomatie".equals(category);
    }
    
    /**
     * Vérifie si c'est une permission de spawner
     * @return true si spawner
     */
    public boolean isSpawner() {
        return "spawners".equals(category);
    }
    
    /**
     * Vérifie si c'est une permission TNT
     * @return true si TNT
     */
    public boolean isTnt() {
        return "tnt".equals(category);
    }
    
    /**
     * Vérifie si c'est une permission admin
     * @return true si admin
     */
    public boolean isAdmin() {
        return "admin".equals(category);
    }
    
    /**
     * Vérifie si c'est une permission legacy (deprecated)
     * @return true si legacy
     */
    public boolean isLegacy() {
        return "legacy".equals(category);
    }
    
    /**
     * Vérifie si cette permission doit être affichée dans les menus GUI
     * @return true si affichable
     */
    public boolean isDisplayable() {
        return !isLegacy() && !"relations".equals(category);
    }
    
    /**
     * Obtient une action par sa clé de configuration
     * @param key La clé (ex: "build", "destroy")
     * @return L'action ou null si non trouvée
     */
    public static PermissionAction fromConfigKey(String key) {
        if (key == null) return null;
        for (PermissionAction action : values()) {
            if (action.configKey.equalsIgnoreCase(key)) {
                return action;
            }
        }
        return null;
    }
    
    /**
     * Obtient toutes les permissions d'une catégorie
     * @param category La catégorie
     * @return Liste des permissions
     */
    public static java.util.List<PermissionAction> getByCategory(String category) {
        java.util.List<PermissionAction> result = new java.util.ArrayList<>();
        for (PermissionAction action : values()) {
            if (action.category.equals(category) && action.isDisplayable()) {
                result.add(action);
            }
        }
        return result;
    }
}
