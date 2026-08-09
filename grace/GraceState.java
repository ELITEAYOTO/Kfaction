package me.krunsh.kfaction.grace;

/**
 * Snapshot immuable de l'état global de Grace Period.
 */
public final class GraceState {

    private final boolean active;
    private final long startedAt;
    private final long endsAt;
    private final String startedBy;
    private final String reason;
    private final long revision;

    public GraceState(
            boolean active,
            long startedAt,
            long endsAt,
            String startedBy,
            String reason,
            long revision
    ) {
        this.active = active;
        this.startedAt = Math.max(0L, startedAt);
        this.endsAt = Math.max(0L, endsAt);
        this.startedBy = normalize(startedBy);
        this.reason = normalize(reason);
        this.revision = Math.max(0L, revision);
    }

    public static GraceState inactive() {
        return new GraceState(
                false,
                0L,
                0L,
                null,
                null,
                0L
        );
    }

    public boolean isActive() {
        return active;
    }

    public boolean isActiveAt(long now) {
        return active
                && endsAt > now;
    }

    public boolean isExpiredAt(long now) {
        return active
                && endsAt > 0L
                && endsAt <= now;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public long getEndsAt() {
        return endsAt;
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

    public long getRemainingMillis(long now) {
        if (!isActiveAt(now)) {
            return 0L;
        }

        return Math.max(
                0L,
                endsAt - now
        );
    }

    public GraceState stopped(long newRevision) {
        return new GraceState(
                false,
                startedAt,
                endsAt,
                startedBy,
                reason,
                newRevision
        );
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();

        return trimmed.isEmpty()
                ? null
                : trimmed;
    }

    @Override
    public String toString() {
        return "GraceState{" +
                "active=" + active +
                ", startedAt=" + startedAt +
                ", endsAt=" + endsAt +
                ", startedBy='" + startedBy + '\'' +
                ", reason='" + reason + '\'' +
                ", revision=" + revision +
                '}';
    }
}
