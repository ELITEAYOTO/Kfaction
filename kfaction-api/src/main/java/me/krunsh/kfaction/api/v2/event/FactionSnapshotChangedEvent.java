package me.krunsh.kfaction.api.v2.event;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import me.krunsh.kfaction.core.operation.OperationContext;

/**
 * Événement public post-commit. Il ne transporte que des identifiants, une
 * révision et les familles invalidées.
 */
public final class FactionSnapshotChangedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String factionId;
    private final long revision;
    private final Set<FactionField> fields;
    private final Set<UUID> affectedPlayers;
    private final OperationContext context;

    public FactionSnapshotChangedEvent(String factionId, long revision,
                                       Set<FactionField> fields,
                                       Set<UUID> affectedPlayers,
                                       OperationContext context) {
        if (factionId == null || factionId.trim().isEmpty()) {
            throw new IllegalArgumentException("factionId cannot be empty");
        }
        this.factionId = factionId.trim();
        this.revision = Math.max(0L, revision);
        EnumSet<FactionField> safeFields = fields == null || fields.isEmpty()
                ? EnumSet.of(FactionField.SNAPSHOT)
                : EnumSet.copyOf(fields);
        this.fields = Collections.unmodifiableSet(safeFields);
        this.affectedPlayers = Collections.unmodifiableSet(
                new LinkedHashSet<UUID>(affectedPlayers != null
                        ? affectedPlayers : Collections.<UUID>emptySet()));
        this.context = context;
    }

    public String getFactionId() { return factionId; }
    public long getRevision() { return revision; }
    public Set<FactionField> getFields() { return fields; }
    public Set<UUID> getAffectedPlayers() { return affectedPlayers; }
    public OperationContext getContext() { return context; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
