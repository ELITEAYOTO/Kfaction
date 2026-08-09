package me.krunsh.kfaction.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import me.krunsh.kfaction.data.Faction;

/**
 * Event déclenché quand un joueur quitte une faction
 */
public class PlayerLeaveFactionEvent extends Event implements Cancellable {
    
    private static final HandlerList HANDLERS = new HandlerList();
    
    private final Player player;
    private final Faction faction;
    private final LeaveReason reason;
    private boolean cancelled = false;
    
    public enum LeaveReason {
        LEAVE,      // /f leave
        KICK,       // Exclus par un membre
        DISBAND,    // Faction dissoute
        ADMIN,      // Forcé par un admin
        OTHER
    }
    
    public PlayerLeaveFactionEvent(Player player, Faction faction, LeaveReason reason) {
        this.player = player;
        this.faction = faction;
        this.reason = reason;
    }
    
    /**
     * @return Le joueur qui quitte
     */
    public Player getPlayer() {
        return player;
    }
    
    /**
     * @return La faction quittée
     */
    public Faction getFaction() {
        return faction;
    }
    
    /**
     * @return La raison du départ
     */
    public LeaveReason getReason() {
        return reason;
    }
    
    @Override
    public boolean isCancelled() {
        // Ne peut pas être annulé si disband
        if (reason == LeaveReason.DISBAND) return false;
        return cancelled;
    }
    
    @Override
    public void setCancelled(boolean cancel) {
        if (reason != LeaveReason.DISBAND) {
            this.cancelled = cancel;
        }
    }
    
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
    
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
