package me.krunsh.kfaction.api.event;

import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import me.krunsh.kfaction.data.Faction;

/**
 * Event déclenché quand une faction claim un chunk
 */
public class FactionClaimEvent extends Event implements Cancellable {
    
    private static final HandlerList HANDLERS = new HandlerList();
    
    private final Player player;
    private final Faction faction;
    private final Chunk chunk;
    private final Faction previousOwner; // null si wilderness
    private final ClaimType type;
    private boolean cancelled = false;
    private String cancelReason;
    
    public enum ClaimType {
        CLAIM,      // Claim normal
        OVERCLAIM,  // Overclaim d'une faction raidable
        ADMIN       // Claim forcé par admin
    }
    
    public FactionClaimEvent(Player player, Faction faction, Chunk chunk, Faction previousOwner, ClaimType type) {
        this.player = player;
        this.faction = faction;
        this.chunk = chunk;
        this.previousOwner = previousOwner;
        this.type = type;
    }
    
    /**
     * @return Le joueur qui claim
     */
    public Player getPlayer() {
        return player;
    }
    
    /**
     * @return La faction qui claim
     */
    public Faction getFaction() {
        return faction;
    }
    
    /**
     * @return Le chunk claimé
     */
    public Chunk getChunk() {
        return chunk;
    }
    
    /**
     * @return L'ancien propriétaire, null si wilderness
     */
    public Faction getPreviousOwner() {
        return previousOwner;
    }
    
    /**
     * @return true si overclaim
     */
    public boolean isOverclaim() {
        return type == ClaimType.OVERCLAIM;
    }
    
    /**
     * @return Le type de claim
     */
    public ClaimType getClaimType() {
        return type;
    }
    
    @Override
    public boolean isCancelled() {
        return cancelled;
    }
    
    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
    
    /**
     * Annule avec une raison
     */
    public void setCancelled(boolean cancel, String reason) {
        this.cancelled = cancel;
        this.cancelReason = reason;
    }
    
    /**
     * @return La raison d'annulation
     */
    public String getCancelReason() {
        return cancelReason;
    }
    
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
    
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
