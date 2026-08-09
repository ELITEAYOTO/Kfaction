package me.krunsh.kfaction.api.v2;

/**
 * Snapshot public de la Grace Period.
 */
public final class GraceView {

    private final boolean active;
    private final long startedAt;
    private final long endsAt;
    private final long remainingMillis;

    private final String startedBy;
    private final String reason;

    private final long revision;

    public GraceView(
            boolean active,
            long startedAt,
            long endsAt,
            long remainingMillis,
            String startedBy,
            String reason,
            long revision
    ) {
        this.active = active;
        this.startedAt = Math.max(0L, startedAt);
        this.endsAt = Math.max(0L, endsAt);
        this.remainingMillis = Math.max(0L, remainingMillis);
        this.startedBy = startedBy;
        this.reason = reason;
        this.revision = Math.max(0L, revision);
    }

    public boolean isActive() {
        return active;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public long getEndsAt() {
        return endsAt;
    }

    public long getRemainingMillis() {
        return remainingMillis;
    }

    public String getStartedBy() {
        return startedBy;
    }

    public String getReason() {
        return reason;
    }

    public long getRevision() {
        return revision;
    }
}
