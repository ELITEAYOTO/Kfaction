package me.krunsh.kfaction.api.v2;

/** Demande de relation typée et expirée explicitement. */
public final class RelationRequestView {

    public enum Status { PENDING, EXPIRED }

    private final String sourceFactionId;
    private final String targetFactionId;
    private final String relation;
    private final long createdAt;
    private final long expiresAt;
    private final Status status;

    public RelationRequestView(String sourceFactionId, String targetFactionId, String relation,
                               long createdAt, long expiresAt, Status status) {
        if (sourceFactionId == null || targetFactionId == null || relation == null || status == null) {
            throw new IllegalArgumentException("relation request fields are required");
        }
        this.sourceFactionId = sourceFactionId;
        this.targetFactionId = targetFactionId;
        this.relation = relation;
        this.createdAt = Math.max(0L, createdAt);
        this.expiresAt = Math.max(0L, expiresAt);
        this.status = status;
    }

    public String getSourceFactionId() { return sourceFactionId; }
    public String getTargetFactionId() { return targetFactionId; }
    public String getRelation() { return relation; }
    public long getCreatedAt() { return createdAt; }
    public long getExpiresAt() { return expiresAt; }
    public Status getStatus() { return status; }
}
