package me.krunsh.kfaction.managers;

import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.Relation;

/**
 * Gestionnaire des relations entre factions
 * Gère les demandes de relation et leur validation
 */
public class RelationManager {
    
    private final Kfaction plugin;
    
    // Configuration des limites
    private int maxAllies;
    private int maxEnemies;
    private int maxTruces;
    private boolean requireMutualAlliance;
    private boolean requireMutualTruce;
    private long requestExpirationMs;
    
    public RelationManager(Kfaction plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Initialise le manager
     */
    public void initialize() {
        loadConfig();
        plugin.getLogger().info("RelationManager initialisé");
    }
    
    /**
     * Charge la configuration
     */
    public void loadConfig() {
        maxAllies = plugin.getConfigManager().getInt("relations.max-allies", 5);
        maxEnemies = plugin.getConfigManager().getInt("relations.max-enemies", 10);
        maxTruces = plugin.getConfigManager().getInt("relations.max-truces", 3);
        requireMutualAlliance = plugin.getConfigManager().getBoolean("relations.require-mutual-alliance", true);
        requireMutualTruce = plugin.getConfigManager().getBoolean("relations.require-mutual-truce", true);
        requestExpirationMs = plugin.getConfigManager().getLong("relations.request-expiration-seconds", 300) * 1000;
    }
    
    // === Demandes de relation ===
    
    /**
     * Résultat d'une demande de relation
     */
    public enum RelationResult {
        SUCCESS("Relation établie"),
        REQUEST_SENT("Demande envoyée"),
        REQUEST_PENDING("Une demande est déjà en attente"),
        ALREADY_SET("Cette relation existe déjà"),
        LIMIT_REACHED("Limite de relations atteinte"),
        CANNOT_SELF("Vous ne pouvez pas établir de relation avec votre propre faction"),
        NO_PERMISSION("Vous n'avez pas la permission"),
        NOT_FOUND("Faction non trouvée");
        
        private final String message;
        
        RelationResult(String message) {
            this.message = message;
        }
        
        public String getMessage() {
            return message;
        }
        
        public boolean isSuccess() {
            return this == SUCCESS || this == REQUEST_SENT;
        }
    }
    
    /**
     * Demande ou établit une relation d'alliance
     * @param requesting La faction qui demande
     * @param target La faction cible
     * @return Le résultat de l'opération
     */
    public RelationResult requestAlly(Faction requesting, Faction target) {
        return requestRelation(requesting, target, Relation.ALLY);
    }
    
    /**
     * Demande ou établit une relation de trêve
     * @param requesting La faction qui demande
     * @param target La faction cible
     * @return Le résultat de l'opération
     */
    public RelationResult requestTruce(Faction requesting, Faction target) {
        return requestRelation(requesting, target, Relation.TRUCE);
    }
    
    /**
     * Déclare une faction comme ennemi (unilatéral)
     * @param requesting La faction qui déclare
     * @param target La faction cible
     * @return Le résultat de l'opération
     */
    public RelationResult declareEnemy(Faction requesting, Faction target) {
        // Vérification de base
        if (requesting == null || target == null) {
            return RelationResult.NOT_FOUND;
        }
        if (requesting.getId().equals(target.getId())) {
            return RelationResult.CANNOT_SELF;
        }
        
        // Vérifier la limite
        if (requesting.getEnemies().size() >= maxEnemies) {
            return RelationResult.LIMIT_REACHED;
        }
        
        // Vérifier si déjà ennemi
        if (requesting.getRelationTo(target) == Relation.ENEMY) {
            return RelationResult.ALREADY_SET;
        }
        
        // Établir la relation (unilatérale)
        requesting.setRelation(target.getId(), Relation.ENEMY);
        plugin.getStorageManager().markDirty(requesting);
        
        // Notifier les deux factions
        notifyRelationChange(requesting, target, Relation.ENEMY, true);
        
        return RelationResult.SUCCESS;
    }
    
    /**
     * Revient à la neutralité avec une faction
     * @param requesting La faction qui demande
     * @param target La faction cible
     * @return Le résultat de l'opération
     */
    public RelationResult setNeutral(Faction requesting, Faction target) {
        if (requesting == null || target == null) {
            return RelationResult.NOT_FOUND;
        }
        if (requesting.getId().equals(target.getId())) {
            return RelationResult.CANNOT_SELF;
        }
        
        Relation currentRelation = requesting.getRelationTo(target);
        if (currentRelation == Relation.NEUTRAL) {
            return RelationResult.ALREADY_SET;
        }
        
        // Retirer la relation
        requesting.setRelation(target.getId(), Relation.NEUTRAL);
        target.setRelation(requesting.getId(), Relation.NEUTRAL);
        
        plugin.getStorageManager().markDirty(requesting);
        plugin.getStorageManager().markDirty(target);
        
        // Notifier
        notifyRelationChange(requesting, target, Relation.NEUTRAL, false);
        
        return RelationResult.SUCCESS;
    }
    
    /**
     * Gère une demande de relation bilatérale
     */
    private RelationResult requestRelation(Faction requesting, Faction target, Relation relation) {
        if (requesting == null || target == null) {
            return RelationResult.NOT_FOUND;
        }
        if (requesting.getId().equals(target.getId())) {
            return RelationResult.CANNOT_SELF;
        }
        
        // Vérifier les limites
        if (relation == Relation.ALLY && requesting.getAllies().size() >= maxAllies) {
            return RelationResult.LIMIT_REACHED;
        }
        
        // Vérifier si déjà établi
        if (requesting.getRelationTo(target) == relation) {
            return RelationResult.ALREADY_SET;
        }
        
        // Vérifier si une demande existe de l'autre côté
        boolean requireMutual = (relation == Relation.ALLY && requireMutualAlliance) ||
                               (relation == Relation.TRUCE && requireMutualTruce);
        
        if (requireMutual) {
            if (target.hasRelationRequest(requesting.getId())) {
                // L'autre faction a aussi demandé = acceptation mutuelle
                target.removeRelationRequest(requesting.getId());
                
                // Établir la relation pour les deux
                requesting.setRelation(target.getId(), relation);
                target.setRelation(requesting.getId(), relation);
                
                plugin.getStorageManager().markDirty(requesting);
                plugin.getStorageManager().markDirty(target);
                
                notifyRelationChange(requesting, target, relation, true);
                return RelationResult.SUCCESS;
            } else {
                // Envoyer une demande
                if (requesting.hasRelationRequest(target.getId())) {
                    return RelationResult.REQUEST_PENDING;
                }
                
                requesting.addRelationRequest(target.getId());
                plugin.getStorageManager().markDirty(requesting);
                
                notifyRelationRequest(requesting, target, relation);
                return RelationResult.REQUEST_SENT;
            }
        } else {
            // Pas besoin de réciprocité
            requesting.setRelation(target.getId(), relation);
            plugin.getStorageManager().markDirty(requesting);
            
            notifyRelationChange(requesting, target, relation, true);
            return RelationResult.SUCCESS;
        }
    }
    
    // === Notifications ===
    
    /**
     * Notifie un changement de relation
     */
    private void notifyRelationChange(Faction faction1, Faction faction2, Relation relation, boolean mutual) {
        String msgKey;
        switch (relation) {
            case ALLY:
                msgKey = "relations.now-allies";
                break;
            case ENEMY:
                msgKey = mutual ? "relations.now-enemies" : "relations.declared-enemy";
                break;
            case TRUCE:
                msgKey = "relations.now-truce";
                break;
            case NEUTRAL:
                msgKey = "relations.now-neutral";
                break;
            default:
                return;
        }
        
        String msg1 = plugin.getMessageManager().get(msgKey, "{faction}", faction2.getName());
        String msg2 = plugin.getMessageManager().get(msgKey, "{faction}", faction1.getName());
        
        faction1.broadcast(msg1);
        if (mutual) {
            faction2.broadcast(msg2);
        }
    }
    
    /**
     * Notifie une demande de relation
     */
    private void notifyRelationRequest(Faction requesting, Faction target, Relation relation) {
        String msgKey = relation == Relation.ALLY ? "relations.ally-request" : "relations.truce-request";
        
        String requestingMsg = plugin.getMessageManager().get(msgKey + "-sent", "{faction}", target.getName());
        String targetMsg = plugin.getMessageManager().get(msgKey + "-received", "{faction}", requesting.getName());
        
        requesting.broadcast(requestingMsg);
        target.broadcast(targetMsg);
    }
    
    // === Utilitaires ===
    
    /**
     * Obtient la relation entre deux joueurs
     * @param player1 Premier joueur
     * @param player2 Deuxième joueur
     * @return La relation
     */
    public Relation getRelation(Player player1, Player player2) {
        Faction faction1 = plugin.getFactionManager().getPlayerFaction(player1);
        Faction faction2 = plugin.getFactionManager().getPlayerFaction(player2);
        
        if (faction1 == null || faction2 == null) {
            return Relation.NEUTRAL;
        }
        
        return faction1.getRelationTo(faction2);
    }
    
    /**
     * Vérifie si deux joueurs sont alliés
     */
    public boolean areAllies(Player player1, Player player2) {
        Relation rel = getRelation(player1, player2);
        return rel == Relation.ALLY || rel == Relation.MEMBER;
    }
    
    /**
     * Vérifie si deux joueurs sont ennemis
     */
    public boolean areEnemies(Player player1, Player player2) {
        return getRelation(player1, player2) == Relation.ENEMY;
    }
    
    /**
     * Vérifie si deux joueurs sont dans la même faction
     */
    public boolean areSameFaction(Player player1, Player player2) {
        Faction faction1 = plugin.getFactionManager().getPlayerFaction(player1);
        Faction faction2 = plugin.getFactionManager().getPlayerFaction(player2);
        
        if (faction1 == null || faction2 == null) {
            return false;
        }
        
        return faction1.getId().equals(faction2.getId());
    }
    
    // === Getters config ===
    
    public int getMaxAllies() {
        return maxAllies;
    }
    
    public int getMaxEnemies() {
        return maxEnemies;
    }
    
    public int getMaxTruces() {
        return maxTruces;
    }
    
    /**
     * Définit deux factions comme ennemies mutuellement
     * @param faction1 Première faction
     * @param faction2 Deuxième faction
     */
    public void setEnemy(Faction faction1, Faction faction2) {
        if (faction1 == null || faction2 == null) return;
        if (faction1.getId().equals(faction2.getId())) return;
        
        faction1.setRelation(faction2.getId(), Relation.ENEMY);
        faction2.setRelation(faction1.getId(), Relation.ENEMY);
        
        plugin.getStorageManager().markDirty(faction1);
        plugin.getStorageManager().markDirty(faction2);
    }
}
