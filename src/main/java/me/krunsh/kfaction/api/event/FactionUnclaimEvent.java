package me.krunsh.kfaction.api.event;

import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import me.krunsh.kfaction.data.Faction;

/**
 * Event PRE déclenché avant qu'un chunk soit unclaim.
 *
 * Pour une opération batch, tous les events PRE sont appelés avant la
 * première mutation. Si un seul event est annulé, le batch complet est
 * refusé.
 */
public final class FactionUnclaimEvent
        extends Event
        implements Cancellable {

    public enum UnclaimType {
        SINGLE,
        RADIUS,
        ALL,
        ADMIN
    }

    private static final HandlerList HANDLERS =
            new HandlerList();

    private final Player player;
    private final Faction faction;
    private final Chunk chunk;
    private final UnclaimType type;

    private boolean cancelled;
    private String cancelReason;

    public FactionUnclaimEvent(
            Player player,
            Faction faction,
            Chunk chunk,
            UnclaimType type
    ) {
        this.player = player;
        this.faction = faction;
        this.chunk = chunk;
        this.type = type;
        this.cancelled = false;
        this.cancelReason = null;
    }

    public Player getPlayer() {
        return player;
    }

    public Faction getFaction() {
        return faction;
    }

    public Chunk getChunk() {
        return chunk;
    }

    public UnclaimType getUnclaimType() {
        return type;
    }

    public boolean isBatch() {
        return type == UnclaimType.RADIUS
                || type == UnclaimType.ALL;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public void setCancelled(
            boolean cancelled,
            String reason
    ) {
        this.cancelled = cancelled;
        this.cancelReason =
                reason != null
                        && !reason.trim().isEmpty()
                        ? reason.trim()
                        : null;
    }

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
