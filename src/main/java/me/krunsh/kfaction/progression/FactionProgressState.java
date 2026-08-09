package me.krunsh.kfaction.progression;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * État persistant de progression faction.
 *
 * Schema 3:
 * - progression brute conservée;
 * - timestamps diagnostiques;
 * - révision de transition;
 * - état détaillé des récompenses pending.
 *
 * Les valeurs de quête ne sont jamais tronquées à l'objectif courant afin de
 * survivre aux changements de configuration.
 */
public final class FactionProgressState {

    public static final int CURRENT_SCHEMA_VERSION = 3;

    public enum PendingRewardStatus {
        PREPARED,
        UNAVAILABLE,
        INVALID,
        APPLIED_ACK_PENDING,
        ACK_FAILED
    }

    private volatile int schemaVersion;
    private volatile String lockedTierId;
    private volatile int lockedTierRank;
    private volatile int levelStarted;
    private volatile String pendingTransition;

    private volatile long lastProgressAt;
    private volatile long lastLevelUpAt;
    private volatile long transitionRevision;

    private final Map<String, Long> questProgress =
            new ConcurrentHashMap<String, Long>();

    private final Map<String, Long> archivedProgress =
            new ConcurrentHashMap<String, Long>();

    private final Set<String> pendingRewards =
            Collections.newSetFromMap(
                    new ConcurrentHashMap<String, Boolean>()
            );

    private final Map<String, PendingRewardRecord>
            pendingRewardRecords =
                    new ConcurrentHashMap<String, PendingRewardRecord>();

    private final Map<String, PvpKillRecord> pvpKillLedger =
            new ConcurrentHashMap<String, PvpKillRecord>();

    public FactionProgressState() {
        this.schemaVersion = 0;
        this.lockedTierRank = -1;
        this.levelStarted = 0;
        this.lastProgressAt = 0L;
        this.lastLevelUpAt = 0L;
        this.transitionRevision = 0L;
    }

    // ============================================================
    // Schema / tier / level
    // ============================================================

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(
            int schemaVersion
    ) {
        this.schemaVersion =
                Math.max(
                        0,
                        schemaVersion
                );
    }

    public synchronized void upgradeToCurrentSchema() {
        this.schemaVersion =
                CURRENT_SCHEMA_VERSION;

        /*
         * Les anciens pendingRewards schema 2 n'avaient pas de diagnostic
         * détaillé. Ils deviennent PREPARED par prudence et ne sont jamais
         * rejoués automatiquement.
         */
        for (String key : pendingRewards) {
            if (key != null
                    && !pendingRewardRecords
                            .containsKey(key)) {
                pendingRewardRecords.put(
                        key,
                        new PendingRewardRecord(
                                PendingRewardStatus.PREPARED,
                                0,
                                0L,
                                "migrated-from-schema-2"
                        )
                );
            }
        }
    }

    public String getLockedTierId() {
        return lockedTierId;
    }

    public int getLockedTierRank() {
        return lockedTierRank;
    }

    public int getLevelStarted() {
        return levelStarted;
    }

    public String getPendingTransition() {
        return pendingTransition;
    }

    public void setPendingTransition(
            String value
    ) {
        this.pendingTransition =
                emptyToNull(value);
    }

    public long getLastProgressAt() {
        return lastProgressAt;
    }

    public long getLastLevelUpAt() {
        return lastLevelUpAt;
    }

    public long getTransitionRevision() {
        return transitionRevision;
    }

    public void restoreDiagnostics(
            long lastProgressAt,
            long lastLevelUpAt,
            long transitionRevision
    ) {
        this.lastProgressAt =
                Math.max(
                        0L,
                        lastProgressAt
                );

        this.lastLevelUpAt =
                Math.max(
                        0L,
                        lastLevelUpAt
                );

        this.transitionRevision =
                Math.max(
                        0L,
                        transitionRevision
                );
    }

    public void markProgress(
            long timestamp
    ) {
        this.lastProgressAt =
                Math.max(
                        0L,
                        timestamp
                );
    }

    public void markLevelUp(
            long timestamp
    ) {
        long safe =
                Math.max(
                        0L,
                        timestamp
                );

        this.lastLevelUpAt = safe;
        this.lastProgressAt = safe;
    }

    public long nextTransitionRevision() {
        transitionRevision =
                transitionRevision == Long.MAX_VALUE
                        ? Long.MAX_VALUE
                        : transitionRevision + 1L;

        return transitionRevision;
    }

    public synchronized void lockTier(
            MemberTierDefinition tier
    ) {
        if (tier == null) {
            return;
        }

        if (lockedTierId == null
                || tier.getRank()
                        > lockedTierRank) {
            lockedTierId = tier.getId();
            lockedTierRank = tier.getRank();
        }
    }

    public synchronized void restoreTier(
            String tierId,
            int tierRank
    ) {
        this.lockedTierId =
                emptyToNull(tierId);

        this.lockedTierRank =
                tierRank;
    }

    public void restoreLevelStarted(
            int levelStarted
    ) {
        this.levelStarted =
                Math.max(
                        0,
                        levelStarted
                );
    }

    public synchronized void beginLevel(
            int level,
            MemberTierDefinition tier
    ) {
        archiveCurrent(
                "level." + levelStarted
        );

        questProgress.clear();

        levelStarted =
                Math.max(
                        0,
                        level
                );

        lockedTierId = null;
        lockedTierRank = -1;

        lockTier(tier);

        schemaVersion =
                CURRENT_SCHEMA_VERSION;
    }

    // ============================================================
    // Quest progress
    // ============================================================

    public long getProgress(
            String questId
    ) {
        Long value =
                questProgress.get(
                        questId
                );

        return value == null
                ? 0L
                : Math.max(
                        0L,
                        value.longValue()
                );
    }

    public synchronized long addProgress(
            String questId,
            long amount
    ) {
        if (questId == null
                || amount <= 0L) {
            return getProgress(
                    questId
            );
        }

        long before =
                getProgress(
                        questId
                );

        long after =
                before > Long.MAX_VALUE - amount
                        ? Long.MAX_VALUE
                        : before + amount;

        questProgress.put(
                questId,
                after
        );

        return after;
    }

    public synchronized void setProgress(
            String questId,
            long value
    ) {
        if (questId == null) {
            return;
        }

        questProgress.put(
                questId,
                Math.max(
                        0L,
                        value
                )
        );
    }

    public Map<String, Long> snapshotProgress() {
        return Collections.unmodifiableMap(
                new LinkedHashMap<String, Long>(
                        questProgress
                )
        );
    }

    public Map<String, Long> snapshotArchivedProgress() {
        return Collections.unmodifiableMap(
                new LinkedHashMap<String, Long>(
                        archivedProgress
                )
        );
    }

    public synchronized void restoreProgress(
            Map<String, Long> values
    ) {
        questProgress.clear();

        if (values == null) {
            return;
        }

        for (Map.Entry<String, Long> entry
                : values.entrySet()) {
            if (entry.getKey() != null
                    && entry.getValue() != null) {
                questProgress.put(
                        entry.getKey(),
                        Math.max(
                                0L,
                                entry.getValue()
                                        .longValue()
                        )
                );
            }
        }
    }

    public synchronized void restoreArchivedProgress(
            Map<String, Long> values
    ) {
        archivedProgress.clear();

        if (values != null) {
            archivedProgress.putAll(values);
        }
    }

    public synchronized void archiveLegacy(
            String questId,
            long value
    ) {
        if (questId != null) {
            archivedProgress.put(
                    "legacy." + questId,
                    Math.max(
                            0L,
                            value
                    )
            );
        }
    }

    // ============================================================
    // Reward pending / recovery diagnostics
    // ============================================================

    public Set<String> getPendingRewards() {
        return Collections.unmodifiableSet(
                new LinkedHashSet<String>(
                        pendingRewards
                )
        );
    }

    public Map<String, PendingRewardRecord>
            snapshotPendingRewardRecords() {
        return Collections.unmodifiableMap(
                new LinkedHashMap<String, PendingRewardRecord>(
                        pendingRewardRecords
                )
        );
    }

    public synchronized void addPendingReward(
            String rewardKey
    ) {
        if (rewardKey == null) {
            return;
        }

        pendingRewards.add(rewardKey);

        if (!pendingRewardRecords
                .containsKey(rewardKey)) {
            pendingRewardRecords.put(
                    rewardKey,
                    new PendingRewardRecord(
                            PendingRewardStatus.PREPARED,
                            0,
                            0L,
                            null
                    )
            );
        }
    }

    public synchronized void markPendingReward(
            String rewardKey,
            PendingRewardStatus status,
            String detail,
            long timestamp
    ) {
        if (rewardKey == null
                || status == null) {
            return;
        }

        pendingRewards.add(rewardKey);

        PendingRewardRecord previous =
                pendingRewardRecords.get(
                        rewardKey
                );

        int attempts =
                previous != null
                        ? previous.getAttempts()
                        : 0;

        if (status != PendingRewardStatus.PREPARED) {
            attempts =
                    attempts == Integer.MAX_VALUE
                            ? Integer.MAX_VALUE
                            : attempts + 1;
        }

        pendingRewardRecords.put(
                rewardKey,
                new PendingRewardRecord(
                        status,
                        attempts,
                        Math.max(
                                0L,
                                timestamp
                        ),
                        detail
                )
        );
    }

    public synchronized void removePendingReward(
            String rewardKey
    ) {
        if (rewardKey == null) {
            return;
        }

        pendingRewards.remove(rewardKey);
        pendingRewardRecords.remove(
                rewardKey
        );
    }

    public synchronized void restorePendingRewards(
            Iterable<String> keys
    ) {
        pendingRewards.clear();

        if (keys != null) {
            for (String key : keys) {
                if (key != null) {
                    pendingRewards.add(key);
                }
            }
        }

        /*
         * Les records sont restaurés séparément par le codec. On supprime
         * seulement ceux qui ne correspondent plus à une clé pending.
         */
        pendingRewardRecords.keySet()
                .retainAll(
                        pendingRewards
                );
    }

    public synchronized void restorePendingRewardRecords(
            Map<String, PendingRewardRecord> records
    ) {
        pendingRewardRecords.clear();

        if (records != null) {
            for (Map.Entry<String, PendingRewardRecord> entry
                    : records.entrySet()) {
                if (entry.getKey() != null
                        && entry.getValue() != null
                        && pendingRewards.contains(
                                entry.getKey()
                        )) {
                    pendingRewardRecords.put(
                            entry.getKey(),
                            entry.getValue()
                    );
                }
            }
        }

        for (String key : pendingRewards) {
            if (!pendingRewardRecords
                    .containsKey(key)) {
                pendingRewardRecords.put(
                        key,
                        new PendingRewardRecord(
                                PendingRewardStatus.PREPARED,
                                0,
                                0L,
                                "legacy-pending"
                        )
                );
            }
        }
    }

    // ============================================================
    // PvP anti-farm ledger
    // ============================================================

    /**
     * Vérifie puis réserve atomiquement un kill.
     *
     * La clé ne contient pas le tueur:
     * toute la faction partage la limite pour une victime donnée.
     */
    public synchronized boolean tryRecordPvpKill(
            String questId,
            String victimId,
            long nowMillis,
            int cooldownSeconds,
            int maxPerVictimPerDay
    ) {
        if (questId == null
                || victimId == null
                || nowMillis < 0L) {
            return false;
        }

        String key =
                questId
                        + "|"
                        + victimId;

        long epochDay =
                Math.floorDiv(
                        nowMillis,
                        86_400_000L
                );

        PvpKillRecord previous =
                pvpKillLedger.get(key);

        if (previous != null) {
            long cooldownMillis =
                    Math.max(
                            0L,
                            cooldownSeconds
                    ) * 1000L;

            if (cooldownMillis > 0L
                    && nowMillis
                            >= previous.lastKillMillis
                    && nowMillis
                            - previous.lastKillMillis
                            < cooldownMillis) {
                return false;
            }

            if (maxPerVictimPerDay > 0
                    && previous.epochDay == epochDay
                    && previous.dailyCount
                            >= maxPerVictimPerDay) {
                return false;
            }
        }

        int daily =
                previous != null
                && previous.epochDay == epochDay
                        ? previous.dailyCount + 1
                        : 1;

        pvpKillLedger.put(
                key,
                new PvpKillRecord(
                        nowMillis,
                        epochDay,
                        daily
                )
        );

        return true;
    }

    public Map<String, PvpKillRecord>
            snapshotPvpKillLedger() {
        return Collections.unmodifiableMap(
                new LinkedHashMap<String, PvpKillRecord>(
                        pvpKillLedger
                )
        );
    }

    public synchronized void restorePvpKillLedger(
            Map<String, PvpKillRecord> values
    ) {
        pvpKillLedger.clear();

        if (values != null) {
            pvpKillLedger.putAll(values);
        }
    }

    // ============================================================
    // Snapshot / rollback
    // ============================================================

    public synchronized Snapshot snapshot() {
        return new Snapshot(
                schemaVersion,
                lockedTierId,
                lockedTierRank,
                levelStarted,
                pendingTransition,
                lastProgressAt,
                lastLevelUpAt,
                transitionRevision,
                new LinkedHashMap<String, Long>(
                        questProgress
                ),
                new LinkedHashMap<String, Long>(
                        archivedProgress
                ),
                new LinkedHashSet<String>(
                        pendingRewards
                ),
                new LinkedHashMap<String, PendingRewardRecord>(
                        pendingRewardRecords
                ),
                new LinkedHashMap<String, PvpKillRecord>(
                        pvpKillLedger
                )
        );
    }

    public synchronized void restore(
            Snapshot snapshot
    ) {
        if (snapshot == null) {
            return;
        }

        schemaVersion =
                snapshot.schemaVersion;

        lockedTierId =
                snapshot.lockedTierId;

        lockedTierRank =
                snapshot.lockedTierRank;

        levelStarted =
                snapshot.levelStarted;

        pendingTransition =
                snapshot.pendingTransition;

        lastProgressAt =
                snapshot.lastProgressAt;

        lastLevelUpAt =
                snapshot.lastLevelUpAt;

        transitionRevision =
                snapshot.transitionRevision;

        questProgress.clear();
        questProgress.putAll(
                snapshot.questProgress
        );

        archivedProgress.clear();
        archivedProgress.putAll(
                snapshot.archivedProgress
        );

        pendingRewards.clear();
        pendingRewards.addAll(
                snapshot.pendingRewards
        );

        pendingRewardRecords.clear();
        pendingRewardRecords.putAll(
                snapshot.pendingRewardRecords
        );

        pvpKillLedger.clear();
        pvpKillLedger.putAll(
                snapshot.pvpKillLedger
        );
    }

    private void archiveCurrent(
            String prefix
    ) {
        if (levelStarted <= 0
                || questProgress.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Long> entry
                : questProgress.entrySet()) {
            archivedProgress.put(
                    prefix
                            + "."
                            + entry.getKey(),
                    entry.getValue()
            );
        }
    }

    private static String emptyToNull(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String trimmed =
                value.trim();

        return trimmed.isEmpty()
                ? null
                : trimmed;
    }

    // ============================================================
    // Immutable nested records
    // ============================================================

    public static final class Snapshot {

        private final int schemaVersion;
        private final String lockedTierId;
        private final int lockedTierRank;
        private final int levelStarted;
        private final String pendingTransition;

        private final long lastProgressAt;
        private final long lastLevelUpAt;
        private final long transitionRevision;

        private final Map<String, Long> questProgress;
        private final Map<String, Long> archivedProgress;

        private final Set<String> pendingRewards;

        private final Map<String, PendingRewardRecord>
                pendingRewardRecords;

        private final Map<String, PvpKillRecord>
                pvpKillLedger;

        private Snapshot(
                int schemaVersion,
                String lockedTierId,
                int lockedTierRank,
                int levelStarted,
                String pendingTransition,
                long lastProgressAt,
                long lastLevelUpAt,
                long transitionRevision,
                Map<String, Long> questProgress,
                Map<String, Long> archivedProgress,
                Set<String> pendingRewards,
                Map<String, PendingRewardRecord> pendingRewardRecords,
                Map<String, PvpKillRecord> pvpKillLedger
        ) {
            this.schemaVersion = schemaVersion;
            this.lockedTierId = lockedTierId;
            this.lockedTierRank = lockedTierRank;
            this.levelStarted = levelStarted;
            this.pendingTransition = pendingTransition;
            this.lastProgressAt = lastProgressAt;
            this.lastLevelUpAt = lastLevelUpAt;
            this.transitionRevision = transitionRevision;
            this.questProgress = questProgress;
            this.archivedProgress = archivedProgress;
            this.pendingRewards = pendingRewards;
            this.pendingRewardRecords = pendingRewardRecords;
            this.pvpKillLedger = pvpKillLedger;
        }
    }

    public static final class PendingRewardRecord {

        private final PendingRewardStatus status;
        private final int attempts;
        private final long lastAttemptAt;
        private final String detail;

        public PendingRewardRecord(
                PendingRewardStatus status,
                int attempts,
                long lastAttemptAt,
                String detail
        ) {
            this.status =
                    status != null
                            ? status
                            : PendingRewardStatus.PREPARED;

            this.attempts =
                    Math.max(
                            0,
                            attempts
                    );

            this.lastAttemptAt =
                    Math.max(
                            0L,
                            lastAttemptAt
                    );

            this.detail =
                    emptyToNull(detail);
        }

        public PendingRewardStatus getStatus() {
            return status;
        }

        public int getAttempts() {
            return attempts;
        }

        public long getLastAttemptAt() {
            return lastAttemptAt;
        }

        public String getDetail() {
            return detail;
        }
    }

    public static final class PvpKillRecord {

        private final long lastKillMillis;
        private final long epochDay;
        private final int dailyCount;

        public PvpKillRecord(
                long lastKillMillis,
                long epochDay,
                int dailyCount
        ) {
            this.lastKillMillis =
                    Math.max(
                            0L,
                            lastKillMillis
                    );

            this.epochDay = epochDay;

            this.dailyCount =
                    Math.max(
                            0,
                            dailyCount
                    );
        }

        public long getLastKillMillis() {
            return lastKillMillis;
        }

        public long getEpochDay() {
            return epochDay;
        }

        public int getDailyCount() {
            return dailyCount;
        }
    }
}
