package me.krunsh.kfaction.audit;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.bukkit.Bukkit;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.core.concurrent.BoundedSerialExecutor;
import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.core.operation.OperationSource;
import me.krunsh.kfaction.data.FactionLog;
import me.krunsh.kfaction.data.FactionLog.LogType;

/**
 * Writer d'audit borné et non bloquant pour le main thread.
 */
public final class AuditService {

    private final Kfaction plugin;
    private final AuditStore store;

    private final ArrayBlockingQueue<AuditEntry> queue;
    private final ScheduledExecutorService writer;
    private final BoundedSerialExecutor queryExecutor;

    private final AtomicBoolean accepting;
    private final AtomicLong droppedEntries;
    private final AtomicLong failedQueries;

    private final int batchSize;
    private final int queryLimitMax;
    private final long retentionMillis;

    private volatile boolean initialized;

    public AuditService(
            Kfaction plugin
    ) {
        if (plugin == null) {
            throw new IllegalArgumentException(
                    "plugin cannot be null"
            );
        }

        this.plugin = plugin;
        this.store =
                new AuditStore(plugin);

        int queueCapacity =
                clamp(
                        plugin.getConfigManager()
                                .getInt(
                                        "audit.queue-capacity",
                                        10000
                                ),
                        256,
                        100000
                );

        this.queue =
                new ArrayBlockingQueue<AuditEntry>(
                        queueCapacity
                );

        this.batchSize =
                clamp(
                        plugin.getConfigManager()
                                .getInt(
                                        "audit.batch-size",
                                        250
                                ),
                        1,
                        5000
                );

        this.queryLimitMax =
                clamp(
                        plugin.getConfigManager()
                                .getInt(
                                        "audit.max-query-limit",
                                        200
                                ),
                        10,
                        1000
                );

        int retentionDays =
                clamp(
                        plugin.getConfigManager()
                                .getInt(
                                        "audit.retention-days",
                                        90
                                ),
                        1,
                        3650
                );

        this.retentionMillis =
                retentionDays
                        * 24L
                        * 60L
                        * 60L
                        * 1000L;

        this.writer =
                Executors.newSingleThreadScheduledExecutor(
                        namedFactory(
                                "Kfaction-AuditWriter"
                        )
                );

        int queryQueueCapacity =
                clamp(
                        plugin.getConfigManager()
                                .getInt(
                                        "audit.query-queue-capacity",
                                        64
                                ),
                        8,
                        1024
                );

        this.queryExecutor =
                new BoundedSerialExecutor(
                        "Kfaction-AuditQuery",
                        queryQueueCapacity,
                        true
                );

        this.accepting =
                new AtomicBoolean(false);

        this.droppedEntries =
                new AtomicLong(0L);

        this.failedQueries =
                new AtomicLong(0L);

        this.initialized = false;
    }

    public void initialize() {
        if (initialized) {
            return;
        }

        try {
            store.initialize();
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Impossible d'initialiser audit.db",
                    exception
            );
        }

        accepting.set(true);
        initialized = true;

        long flushMillis =
                clamp(
                        plugin.getConfigManager()
                                .getInt(
                                        "audit.flush-interval-ms",
                                        1000
                                ),
                        100,
                        60000
                );

        writer.scheduleWithFixedDelay(
                new Runnable() {
                    @Override
                    public void run() {
                        flushOneBatchSafely();
                    }
                },
                flushMillis,
                flushMillis,
                TimeUnit.MILLISECONDS
        );

        writer.scheduleWithFixedDelay(
                new Runnable() {
                    @Override
                    public void run() {
                        cleanupSafely();
                    }
                },
                5L,
                60L,
                TimeUnit.MINUTES
        );

        /*
         * Une première purge est exécutée sur le writer, jamais sur le thread
         * Bukkit.
         */
        writer.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        cleanupSafely();
                    }
                }
        );

        plugin.getLogger().info(
                "Audit V2 initialisé: "
                        + store.getDatabaseFile()
                                .getName()
                        + " queue="
                        + queue.remainingCapacity()
                        + " batch="
                        + batchSize
        );
    }

    public void shutdown() {
        if (!initialized) {
            return;
        }

        accepting.set(false);

        Future<?> drain =
                writer.submit(
                        new Runnable() {
                            @Override
                            public void run() {
                                flushAllSafely();
                            }
                        }
                );

        try {
            drain.get(
                    5L,
                    TimeUnit.SECONDS
            );
        } catch (Exception exception) {
            plugin.getLogger().severe(
                    "Audit shutdown flush incomplet: "
                            + exception.getMessage()
            );
        }

        writer.shutdown();

        try {
            writer.awaitTermination(
                    2L,
                    TimeUnit.SECONDS
            );
        } catch (InterruptedException exception) {
            Thread.currentThread()
                    .interrupt();
        }

        queryExecutor.shutdownNow();

        store.close();

        long dropped =
                droppedEntries.get();

        if (dropped > 0L) {
            plugin.getLogger().warning(
                    "Audit V2: "
                            + dropped
                            + " entrées ont été refusées "
                            + "car la queue était pleine."
            );
        }

        initialized = false;
    }

    // ============================================================
    // Record
    // ============================================================

    public boolean record(
            AuditEntry entry
    ) {
        if (entry == null
                || !initialized
                || !accepting.get()) {
            return false;
        }

        boolean accepted =
                queue.offer(entry);

        if (!accepted) {
            long dropped =
                    droppedEntries.incrementAndGet();

            if (dropped == 1L
                    || dropped % 100L == 0L) {
                plugin.getLogger().severe(
                        "[AUDIT] queue pleine, entrées perdues="
                                + dropped
                );
            }
        }

        return accepted;
    }

    public boolean record(
            AuditCategory category,
            String action,
            AuditOutcome outcome,
            String factionId,
            OperationContext context,
            UUID targetId,
            String targetName,
            String details
    ) {
        return record(
                AuditEntry.create(
                        category,
                        action,
                        outcome,
                        factionId,
                        context,
                        targetId,
                        targetName,
                        redactDetails(details)
                )
        );
    }

    public boolean recordSystem(
            AuditCategory category,
            String action,
            AuditOutcome outcome,
            String factionId,
            String details
    ) {
        return record(
                category,
                action,
                outcome,
                factionId,
                OperationContext.system(),
                null,
                null,
                details
        );
    }

    /**
     * Pont de compatibilité depuis les anciens FactionLog.
     */
    public boolean recordLegacy(
            FactionLog log
    ) {
        if (log == null) {
            return false;
        }

        OperationContext context =
                OperationContext.of(
                        log.getPlayerUuid(),
                        log.getPlayerName(),
                        inferSource(
                                log.getDetails()
                        ),
                        log.getTimestamp(),
                        extractCorrelation(
                                log.getDetails(),
                                log.getId()
                        )
                );

        return record(
                categoryFor(
                        log.getType()
                ),
                log.getType().name(),
                AuditOutcome.SUCCESS,
                log.getFactionId(),
                context,
                log.getTargetUuid(),
                log.getTargetName(),
                log.getDetails()
        );
    }

    // ============================================================
    // Query
    // ============================================================

    public void queryAsync(
            final AuditQuery requested,
            final Consumer<List<AuditEntry>> success,
            final Consumer<Throwable> failure
    ) {
        if (requested == null) {
            failQuery(
                    failure,
                    new IllegalArgumentException(
                            "query cannot be null"
                    )
            );
            return;
        }

        final AuditQuery safeQuery =
                AuditQuery.builder()
                        .factionId(
                                requested.getFactionId()
                        )
                        .playerId(
                                requested.getPlayerId()
                        )
                        .category(
                                requested.getCategory()
                        )
                        .action(
                                requested.getAction()
                        )
                        .correlationId(
                                requested.getCorrelationId()
                        )
                        .sinceTimestamp(
                                requested.getSinceTimestamp()
                        )
                        .limit(
                                Math.min(
                                        requested.getLimit(),
                                        queryLimitMax
                                )
                        )
                        .build();

        if (!initialized
                || queryExecutor.isShutdown()) {
            failQuery(
                    failure,
                    new IllegalStateException(
                            "Audit service unavailable"
                    )
            );
            return;
        }

        boolean accepted =
                queryExecutor.tryExecute(
                        new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    final List<AuditEntry> result =
                                            store.query(
                                                    safeQuery
                                            );

                                    runMain(
                                            new Runnable() {
                                                @Override
                                                public void run() {
                                                    if (success != null) {
                                                        success.accept(
                                                                result
                                                        );
                                                    }
                                                }
                                            }
                                    );

                                } catch (final Throwable throwable) {
                                    failedQueries.incrementAndGet();

                                    failQuery(
                                            failure,
                                            throwable
                                    );
                                }
                            }
                        }
                );

        if (!accepted) {
            failQuery(
                    failure,
                    new IllegalStateException(
                            "Audit query queue saturated"
                    )
            );
        }
    }

    public long getDroppedEntries() {
        return droppedEntries.get();
    }

    public int getQueueSize() {
        return queue.size();
    }

    public int getQueueCapacity() {
        return queue.size()
                + queue.remainingCapacity();
    }

    public int getQueryQueueSize() {
        return queryExecutor.getQueueSize();
    }

    public int getQueryQueueCapacity() {
        return queryExecutor.getQueueCapacity();
    }

    public long getRejectedQueries() {
        return queryExecutor.getRejectedTasks();
    }

    public long getFailedQueries() {
        return failedQueries.get();
    }

    public AuditStore getStore() {
        return store;
    }

    // ============================================================
    // Writer
    // ============================================================

    private void flushOneBatchSafely() {
        List<AuditEntry> batch =
                drain(
                        batchSize
                );

        if (batch.isEmpty()) {
            return;
        }

        try {
            store.writeBatch(batch);

        } catch (SQLException exception) {
            requeueBatch(batch);

            plugin.getLogger().severe(
                    "Erreur flush audit.db: "
                            + exception.getMessage()
            );
        }
    }

    private void requeueBatch(
            Collection<AuditEntry> batch
    ) {
        if (batch == null) {
            return;
        }

        for (AuditEntry entry : batch) {
            if (entry != null
                    && !queue.offer(entry)) {
                droppedEntries.incrementAndGet();
            }
        }
    }

    private void flushAllSafely() {
        while (!queue.isEmpty()) {
            List<AuditEntry> batch =
                    drain(
                            batchSize
                    );

            if (batch.isEmpty()) {
                break;
            }

            try {
                store.writeBatch(batch);
            } catch (SQLException exception) {
                /*
                 * Remettre au maximum les entrées du batch dans la queue.
                 * Le shutdown ne boucle pas infiniment sur une DB cassée.
                 */
                requeueBatch(batch);

                plugin.getLogger().severe(
                        "Erreur flush final audit.db: "
                                + exception.getMessage()
                );

                break;
            }
        }
    }

    private List<AuditEntry> drain(
            int max
    ) {
        List<AuditEntry> batch =
                new ArrayList<AuditEntry>(
                        Math.min(
                                max,
                                queue.size()
                        )
                );

        queue.drainTo(
                batch,
                max
        );

        return batch;
    }

    private void cleanupSafely() {
        try {
            long cutoff =
                    System.currentTimeMillis()
                            - retentionMillis;

            int removed =
                    store.cleanupBefore(
                            cutoff
                    );

            if (removed > 0) {
                plugin.getLogger().info(
                        "Audit cleanup: "
                                + removed
                                + " entrées supprimées"
                );
            }

        } catch (SQLException exception) {
            plugin.getLogger().warning(
                    "Audit cleanup impossible: "
                            + exception.getMessage()
            );
        }
    }

    // ============================================================
    // Mapping / redaction
    // ============================================================

    private static AuditCategory categoryFor(
            LogType type
    ) {
        if (type == null) {
            return AuditCategory.SYSTEM;
        }

        switch (type) {
            case MEMBER_JOIN:
            case MEMBER_LEAVE:
            case MEMBER_KICK:
                return AuditCategory.MEMBERSHIP;

            case MEMBER_PROMOTE:
            case MEMBER_DEMOTE:
                return AuditCategory.ROLE;

            case TERRITORY_CLAIM:
            case TERRITORY_UNCLAIM:
            case TERRITORY_SETHOME:
            case TERRITORY_SETWARP:
            case TERRITORY_DELWARP:
                return AuditCategory.TERRITORY;

            case ECONOMY_DEPOSIT:
            case ECONOMY_WITHDRAW:
                return AuditCategory.ECONOMY;

            case TP_HOME:
            case TP_WARP:
            case TP_INVITE:
                return AuditCategory.TELEPORT;

            case CHEST_DEPOSIT:
            case CHEST_WITHDRAW:
                return AuditCategory.CHEST;

            case RELATION_CHANGE:
                return AuditCategory.RELATION;

            case CLAIM_GROUP_CHANGE:
                return AuditCategory.CLAIM_GROUP;

            case PERMISSION_CHANGE:
                return AuditCategory.PERMISSION;

            case FACTION_DISBAND:
                return AuditCategory.LIFECYCLE;

            default:
                return AuditCategory.SYSTEM;
        }
    }

    private static OperationSource inferSource(
            String details
    ) {
        if (details == null) {
            return OperationSource.SYSTEM;
        }

        String marker =
                "source=";

        int start =
                details.indexOf(marker);

        if (start < 0) {
            return OperationSource.SYSTEM;
        }

        start += marker.length();

        int end =
                details.indexOf(
                        ';',
                        start
                );

        String value =
                end >= 0
                        ? details.substring(
                                start,
                                end
                        )
                        : details.substring(
                                start
                        );

        try {
            return OperationSource.valueOf(
                    value.trim()
            );
        } catch (IllegalArgumentException exception) {
            return OperationSource.SYSTEM;
        }
    }

    private static String extractCorrelation(
            String details,
            String fallback
    ) {
        if (details != null) {
            String marker =
                    "correlation=";

            int start =
                    details.indexOf(marker);

            if (start >= 0) {
                start += marker.length();

                int end =
                        details.indexOf(
                                ';',
                                start
                        );

                String value =
                        end >= 0
                                ? details.substring(
                                        start,
                                        end
                                )
                                : details.substring(
                                        start
                                );

                value = value.trim();

                if (!value.isEmpty()) {
                    return value;
                }
            }
        }

        return fallback != null
                && !fallback.trim().isEmpty()
                ? fallback
                : UUID.randomUUID().toString();
    }

    /**
     * Défense supplémentaire pour les détails historiques.
     *
     * Kfaction ne doit jamais enregistrer de secret warp connu.
     */
    private static String redactDetails(
            String details
    ) {
        if (details == null) {
            return null;
        }

        String sanitized =
                details.replaceAll(
                        "(?i)(password|passwd|pwd)=([^;\\s]+)",
                        "$1=[REDACTED]"
                );

        return sanitized.length() > 4096
                ? sanitized.substring(
                        0,
                        4096
                )
                : sanitized;
    }

    private void failQuery(
            final Consumer<Throwable> failure,
            final Throwable throwable
    ) {
        if (failure == null) {
            return;
        }

        runMain(
                new Runnable() {
                    @Override
                    public void run() {
                        failure.accept(
                                throwable
                        );
                    }
                }
        );
    }

    private void runMain(
            Runnable runnable
    ) {
        if (runnable == null) {
            return;
        }

        if (!plugin.isEnabled()) {
            return;
        }

        if (Bukkit.isPrimaryThread()) {
            runnable.run();
            return;
        }

        Bukkit.getScheduler()
                .runTask(
                        plugin,
                        runnable
                );
    }

    private static ThreadFactory namedFactory(
            final String name
    ) {
        return new ThreadFactory() {
            @Override
            public Thread newThread(
                    Runnable runnable
            ) {
                Thread thread =
                        new Thread(
                                runnable,
                                name
                        );

                thread.setDaemon(true);

                return thread;
            }
        };
    }

    private static int clamp(
            int value,
            int min,
            int max
    ) {
        return Math.max(
                min,
                Math.min(
                        max,
                        value
                )
        );
    }
}
