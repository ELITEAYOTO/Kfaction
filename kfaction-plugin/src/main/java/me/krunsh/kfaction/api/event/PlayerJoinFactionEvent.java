package me.krunsh.kfaction.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionRole;

/**
 * Event déclenché quand un joueur rejoint une faction
 */
public class PlayerJoinFactionEvent extends Event implements Cancellable {
    
    private static final HandlerList HANDLERS = new HandlerList();
    
    private final Player player;
    private final Faction faction;
    private final JoinReason reason;
    private FactionRole initialRole;
    private boolean cancelled = false;
    
    public enum JoinReason {
        INVITED,    // Rejoint après invitation
        OPEN,       // Rejoint faction ouverte
        ADMIN,      // Forcé par un admin
        CREATE      // Créateur de la faction
    }
    
    public PlayerJoinFactionEvent(Player player, Faction faction, JoinReason reason) {
        this.player = player;
        this.faction = faction;
        this.reason = reason;
        this.initialRole = reason == JoinReason.CREATE ? FactionRole.LEADER : FactionRole.RECRUIT;
    }
    
    /**
     * @return Le joueur qui rejoint
     */
    public Player getPlayer() {
        return player;
    }
    
    /**
     * @return La faction rejointe
     */
    public Faction getFaction() {
        return faction;
    }
    
    /**
     * @return La raison du join
     */
    public JoinReason getReason() {
        return reason;
    }
    
    /**
     * @return Le rôle initial du joueur
     */
    public FactionRole getInitialRole() {
        return initialRole;
    }
    
    /**
     * Modifie le rôle initial du joueur
     */
    public void setInitialRole(FactionRole role) {
        this.initialRole = role;
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
