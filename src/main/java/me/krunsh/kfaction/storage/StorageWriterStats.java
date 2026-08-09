package me.krunsh.kfaction.storage;

/**
 * Snapshot immutable des métriques du writer principal.
 */
public final class StorageWriterStats {

    private final int queueSize;
    private final int queueCapacity;

    private final long acceptedTasks;
    private final long rejectedTasks;
    private final long completedTasks;
    private final long failedTasks;

    private final int pendingFactionDeletes;
    private final int pendingPlayerDeletes;

    public StorageWriterStats(
            int queueSize,
            int queueCapacity,
            long acceptedTasks,
            long rejectedTasks,
            long completedTasks,
            long failedTasks,
            int pendingFactionDeletes,
            int pendingPlayerDeletes
    ) {
        this.queueSize =
                Math.max(
                        0,
                        queueSize
                );

        this.queueCapacity =
                Math.max(
                        0,
                        queueCapacity
                );

        this.acceptedTasks =
                Math.max(
                        0L,
                        acceptedTasks
                );

        this.rejectedTasks =
                Math.max(
                        0L,
                        rejectedTasks
                );

        this.completedTasks =
                Math.max(
                        0L,
                        completedTasks
                );

        this.failedTasks =
                Math.max(
                        0L,
                        failedTasks
                );

        this.pendingFactionDeletes =
                Math.max(
                        0,
                        pendingFactionDeletes
                );

        this.pendingPlayerDeletes =
                Math.max(
                        0,
                        pendingPlayerDeletes
                );
    }

    public int getQueueSize() {
        return queueSize;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public long getAcceptedTasks() {
        return acceptedTasks;
    }

    public long getRejectedTasks() {
        return rejectedTasks;
    }

    public long getCompletedTasks() {
        return completedTasks;
    }

    public long getFailedTasks() {
        return failedTasks;
    }

    public int getPendingFactionDeletes() {
        return pendingFactionDeletes;
    }

    public int getPendingPlayerDeletes() {
        return pendingPlayerDeletes;
    }

    public int getPendingDeleteCount() {
        return pendingFactionDeletes
                + pendingPlayerDeletes;
    }

    public int getQueuePercent() {
        if (queueCapacity <= 0) {
            return 0;
        }

        return Math.min(
                100,
                queueSize * 100
                        / queueCapacity
        );
    }

    public boolean hasBackpressure() {
        return rejectedTasks > 0L;
    }
}
