package me.krunsh.kfaction.managers;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionRole;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.PermissionAction;
import me.krunsh.kfaction.data.Relation;

import org.bukkit.entity.Player;

/**
 * Gestionnaire des permissions de faction
 * Vérifie si un joueur peut effectuer une action
 */
public class PermissionManager {
    
    private final Kfaction plugin;
    
    public PermissionManager(Kfaction plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Initialise le manager
     */
    public void initialize() {
        plugin.getLogger().info("PermissionManager initialisé");
    }
    
    /**
     * Ferme le manager
     */
    public void shutdown() {
        // Rien à faire
    }
    
    /**
     * Vérifie si un joueur peut effectuer une action dans sa faction
     * @param player Le joueur
     * @param action L'action à vérifier
     * @return true si autorisé
     */
    public boolean canDo(Player player, PermissionAction action) {
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        if (fPlayer == null || !fPlayer.hasFaction()) {
            return false;
        }
        
        Faction faction = plugin.getFactionManager().getFaction(fPlayer.getFactionId());
        if (faction == null) {
            return false;
        }
        
        return canDo(faction, fPlayer.getRole(), action);
    }
    
    /**
     * Vérifie si un rôle peut effectuer une action dans une faction
     * @param faction La faction
     * @param role Le rôle du membre
     * @param action L'action à vérifier
     * @return true si autorisé
     */
    public boolean canDo(Faction faction, FactionRole role, PermissionAction action) {
        if (faction == null || role == null || action == null) {
            return false;
        }
        
        // Leader peut tout faire
        if (role == FactionRole.LEADER) {
            return true;
        }
        
        // Vérifier les permissions de la faction
        return faction.hasPermission(role, action);
    }
    
    /**
     * Vérifie si une relation peut effectuer une action territoriale
     * @param faction La faction propriétaire
     * @param relation La relation avec le joueur
     * @param action L'action à vérifier
     * @return true si autorisé
     */
    public boolean canDoRelation(Faction faction, Relation relation, PermissionAction action) {
        if (faction == null || action == null) {
            return false;
        }
        
        // Vérifier les permissions de relation
        return faction.hasPermission(relation, action);
    }
    
    /**
     * Vérifie si un joueur peut effectuer une action sur le territoire d'une autre faction
     * @param player Le joueur
     * @param targetFaction La faction propriétaire du territoire
     * @param action L'action à vérifier
     * @return true si autorisé
     */
    public boolean canDoAt(Player player, Faction targetFaction, PermissionAction action) {
        // Admin bypass
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        if (fPlayer != null && fPlayer.isBypassing()) {
            return true;
        }
        
        // Wilderness = tout le monde peut
        if (targetFaction == null || targetFaction.isWilderness()) {
            return true;
        }
        
        // Safezone = personne ne peut (sauf build par admin)
        if (targetFaction.isSafezone()) {
            return false;
        }
        
        // Warzone = dépend de l'action
        if (targetFaction.isWarzone()) {
            // TODO: Configurable
            return false;
        }
        
        // Vérifier si membre de la faction
        if (fPlayer != null && fPlayer.hasFaction()) {
            Faction playerFaction = plugin.getFactionManager().getFaction(fPlayer.getFactionId());
            
            if (playerFaction != null && playerFaction.getId().equals(targetFaction.getId())) {
                // Membre de la faction - vérifier les permissions de rôle
                return canDo(targetFaction, fPlayer.getRole(), action);
            }
            
            // Vérifier les permissions de relation
            Relation relation = targetFaction.getRelationTo(playerFaction);
            return canDoRelation(targetFaction, relation, action);
        }
        
        // Joueur sans faction = relation neutre
        return canDoRelation(targetFaction, Relation.NEUTRAL, action);
    }
}
