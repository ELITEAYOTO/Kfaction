package me.krunsh.kfaction.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.Relation;

/**
 * Event déclenché quand la relation entre deux factions change
 */
public class FactionRelationChangeEvent extends Event implements Cancellable {
    
    private static final HandlerList HANDLERS = new HandlerList();
    
    private final Player initiator;
    private final Faction factionFrom;
    private final Faction factionTo;
    private final Relation oldRelation;
    private final Relation newRelation;
    private boolean cancelled = false;
    
    public FactionRelationChangeEvent(Player initiator, Faction factionFrom, Faction factionTo, 
                                       Relation oldRelation, Relation newRelation) {
        this.initiator = initiator;
        this.factionFrom = factionFrom;
        this.factionTo = factionTo;
        this.oldRelation = oldRelation;
        this.newRelation = newRelation;
    }
    
    /**
     * @return Le joueur qui initie le changement
     */
    public Player getInitiator() {
        return initiator;
    }
    
    /**
     * @return La faction qui initie
     */
    public Faction getFactionFrom() {
        return factionFrom;
    }
    
    /**
     * @return La faction cible
     */
    public Faction getFactionTo() {
        return factionTo;
    }
    
    /**
     * @return L'ancienne relation
     */
    public Relation getOldRelation() {
        return oldRelation;
    }
    
    /**
     * @return La nouvelle relation
     */
    public Relation getNewRelation() {
        return newRelation;
    }
    
    /**
     * @return true si c'est une demande d'alliance
     */
    public boolean isAllyRequest() {
        return newRelation == Relation.ALLY;
    }
    
    /**
     * @return true si c'est une trêve
     */
    public boolean isTruce() {
        return newRelation == Relation.TRUCE;
    }
    
    /**
     * @return true si c'est une déclaration de guerre
     */
    public boolean isEnemy() {
        return newRelation == Relation.ENEMY;
    }
    
    /**
     * @return true si c'est un retour neutre
     */
    public boolean isNeutral() {
        return newRelation == Relation.NEUTRAL;
    }
    
    @Override
    public boolean isCancelled() {
        return cancelled;
    }
    
    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
    
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
    
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
