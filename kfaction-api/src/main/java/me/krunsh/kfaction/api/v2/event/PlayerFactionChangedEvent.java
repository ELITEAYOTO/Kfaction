package me.krunsh.kfaction.api.v2.event;

import java.util.UUID;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import me.krunsh.kfaction.core.operation.OperationContext;

/** Changement post-commit d'appartenance d'un joueur. */
public final class PlayerFactionChangedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerId;
    private final String previousFactionId;
    private final String currentFactionId;
    private final long revision;
    private final OperationContext context;

    public PlayerFactionChangedEvent(UUID playerId, String previousFactionId,
                                     String currentFactionId, long revision,
                                     OperationContext context) {
        if (playerId == null) throw new IllegalArgumentException("playerId cannot be null");
        this.playerId = playerId;
        this.previousFactionId = previousFactionId;
        this.currentFactionId = currentFactionId;
        this.revision = Math.max(0L, revision);
        this.context = context;
    }

    public UUID getPlayerId() { return playerId; }
    public String getPreviousFactionId() { return previousFactionId; }
    public String getCurrentFactionId() { return currentFactionId; }
    public long getRevision() { return revision; }
    public OperationContext getContext() { return context; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
