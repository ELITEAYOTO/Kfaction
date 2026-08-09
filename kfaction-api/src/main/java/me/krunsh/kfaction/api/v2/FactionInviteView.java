package me.krunsh.kfaction.api.v2;

import java.util.UUID;

/** Invitation persistée avec expiration explicite. */
public final class FactionInviteView {

    public enum Status { PENDING, EXPIRED }

    private final String factionId;
    private final UUID playerId;
    private final long createdAt;
    private final long expiresAt;
    private final Status status;

    public FactionInviteView(String factionId, UUID playerId, long createdAt, long expiresAt, Status status) {
        if (factionId == null || playerId == null || status == null) {
            throw new IllegalArgumentException("factionId, playerId and status are required");
        }
        this.factionId = factionId;
        this.playerId = playerId;
        this.createdAt = Math.max(0L, createdAt);
        this.expiresAt = Math.max(0L, expiresAt);
        this.status = status;
    }

    public String getFactionId() { return factionId; }
    public UUID getPlayerId() { return playerId; }
    public long getCreatedAt() { return createdAt; }
    public long getExpiresAt() { return expiresAt; }
    public Status getStatus() { return status; }
}
