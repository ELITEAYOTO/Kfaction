package me.krunsh.kfaction.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import me.krunsh.kfaction.data.Faction;

/**
 * Event déclenché lors de la création d'une faction
 */
public class FactionCreateEvent extends Event implements Cancellable {
    
    private static final HandlerList HANDLERS = new HandlerList();
    
    private final Player player;
    private final String factionName;
    private Faction faction;
    private boolean cancelled = false;
    private String cancelReason = "";
    
    public FactionCreateEvent(Player player, String factionName) {
        this.player = player;
        this.factionName = factionName;
    }
    
    /**
     * @return Le joueur créant la faction
     */
    public Player getPlayer() {
        return player;
    }
    
    /**
     * @return Le nom de la faction à créer
     */
    public String getFactionName() {
        return factionName;
    }
    
    /**
     * @return La faction créée (null avant création effective)
     */
    public Faction getFaction() {
        return faction;
    }
    
    /**
     * Définit la faction créée (appelé par le système)
     */
    public void setFaction(Faction faction) {
        this.faction = faction;
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
     * @return La raison de l'annulation
     */
    public String getCancelReason() {
        return cancelReason;
    }
    
    /**
     * Annule avec une raison
     */
    public void setCancelled(boolean cancel, String reason) {
        this.cancelled = cancel;
        this.cancelReason = reason;
    }
    
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
    
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
