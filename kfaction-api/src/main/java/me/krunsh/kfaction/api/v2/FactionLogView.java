package me.krunsh.kfaction.api.v2;

import java.util.UUID;

/** Entrée d'historique légère ; aucun objet AuditStore ou FactionLog n'est exposé. */
public final class FactionLogView {

    private final String id;
    private final String factionId;
    private final String type;
    private final UUID actorId;
    private final String actorName;
    private final UUID targetId;
    private final String targetName;
    private final String details;
    private final long timestamp;

    public FactionLogView(String id, String factionId, String type,
                          UUID actorId, String actorName, UUID targetId,
                          String targetName, String details, long timestamp) {
        this.id = id;
        this.factionId = factionId;
        this.type = type;
        this.actorId = actorId;
        this.actorName = actorName;
        this.targetId = targetId;
        this.targetName = targetName;
        this.details = details;
        this.timestamp = Math.max(0L, timestamp);
    }

    public String getId() { return id; }
    public String getFactionId() { return factionId; }
    public String getType() { return type; }
    public UUID getActorId() { return actorId; }
    public String getActorName() { return actorName; }
    public UUID getTargetId() { return targetId; }
    public String getTargetName() { return targetName; }
    public String getDetails() { return details; }
    public long getTimestamp() { return timestamp; }
}
