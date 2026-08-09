package me.krunsh.kfaction.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import me.krunsh.kfaction.data.Faction;

/**
 * Event déclenché lors de la dissolution d'une faction
 */
public class FactionDisbandEvent extends Event implements Cancellable {
    
    private static final HandlerList HANDLERS = new HandlerList();
    
    private final Faction faction;
    private final Player disbander;
    private final DisbandReason reason;
    private boolean cancelled = false;
    
    public enum DisbandReason {
        COMMAND,        // /f disband par le leader
        ADMIN,          // /f admin disband par un admin
        INACTIVITY,     // Dissolution automatique pour inactivité
        BANKRUPT,       // Plus de power (optionnel)
        OTHER
    }
    
    public FactionDisbandEvent(Faction faction, Player disbander, DisbandReason reason) {
        this.faction = faction;
        this.disbander = disbander;
        this.reason = reason;
    }
    
    /**
     * @return La faction dissoute
     */
    public Faction getFaction() {
        return faction;
    }
    
    /**
     * @return Le joueur qui dissout (peut être null si automatique)
     */
    public Player getDisbander() {
        return disbander;
    }
    
    /**
     * @return La raison de la dissolution
     */
    public DisbandReason getReason() {
        return reason;
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
