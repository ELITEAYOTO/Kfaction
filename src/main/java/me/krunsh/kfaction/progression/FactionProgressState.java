package me.krunsh.kfaction.progression;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * État persistant minimal. La progression est brute et n'est jamais ramenée à
 * l'objectif courant, afin de survivre aux changements de tranche/config.
 */
public final class FactionProgressState {
    public static final int CURRENT_SCHEMA_VERSION = 2;

    private volatile int schemaVersion;
    private volatile String lockedTierId;
    private volatile int lockedTierRank;
    private volatile int levelStarted;
    private volatile String pendingTransition;
    private final Map<String, Long> questProgress =
            new ConcurrentHashMap<String, Long>();
    private final Map<String, Long> archivedProgress =
            new ConcurrentHashMap<String, Long>();
    private final Set<String> pendingRewards =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final Map<String, PvpKillRecord> pvpKillLedger =
            new ConcurrentHashMap<String, PvpKillRecord>();

    public FactionProgressState() {
        this.schemaVersion = 0;
        this.lockedTierRank = -1;
        this.levelStarted = 0;
    }

    public int getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }
    public String getLockedTierId() { return lockedTierId; }
    public int getLockedTierRank() { return lockedTierRank; }
    public int getLevelStarted() { return levelStarted; }
    public String getPendingTransition() { return pendingTransition; }
    public void setPendingTransition(String value) { this.pendingTransition = value; }

    public synchronized void lockTier(MemberTierDefinition tier) {
        if (tier == null) return;
        if (lockedTierId == null || tier.getRank() > lockedTierRank) {
            lockedTierId = tier.getId();
            lockedTierRank = tier.getRank();
        }
    }

    public synchronized void restoreTier(String tierId, int tierRank) {
        this.lockedTierId = emptyToNull(tierId);
        this.lockedTierRank = tierRank;
    }

    public void restoreLevelStarted(int levelStarted) {
        this.levelStarted = Math.max(0, levelStarted);
    }

    public synchronized void beginLevel(int level, MemberTierDefinition tier) {
        archiveCurrent("level." + levelStarted);
        questProgress.clear();
        levelStarted = level;
        lockedTierId = null;
        lockedTierRank = -1;
        lockTier(tier);
        schemaVersion = CURRENT_SCHEMA_VERSION;
    }

    public long getProgress(String questId) {
        Long value = questProgress.get(questId);
        return value == null ? 0L : Math.max(0L, value.longValue());
    }

    public synchronized long addProgress(String questId, long amount) {
        if (questId == null || amount <= 0L) return getProgress(questId);
        long before = getProgress(questId);
        long after = before > Long.MAX_VALUE - amount
                ? Long.MAX_VALUE : before + amount;
        questProgress.put(questId, after);
        return after;
    }

    public synchronized void setProgress(String questId, long value) {
        if (questId == null) return;
        questProgress.put(questId, Math.max(0L, value));
    }

    public Map<String, Long> snapshotProgress() {
        return Collections.unmodifiableMap(
                new LinkedHashMap<String, Long>(questProgress));
    }

    public Map<String, Long> snapshotArchivedProgress() {
        return Collections.unmodifiableMap(
                new LinkedHashMap<String, Long>(archivedProgress));
    }

    public synchronized void restoreProgress(Map<String, Long> values) {
        questProgress.clear();
        if (values != null) {
            for (Map.Entry<String, Long> entry : values.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    questProgress.put(entry.getKey(),
                            Math.max(0L, entry.getValue().longValue()));
                }
            }
        }
    }

    public synchronized void restoreArchivedProgress(Map<String, Long> values) {
        archivedProgress.clear();
        if (values != null) archivedProgress.putAll(values);
    }

    public synchronized void archiveLegacy(String questId, long value) {
        if (questId != null) {
            archivedProgress.put("legacy." + questId, Math.max(0L, value));
        }
    }

    public Set<String> getPendingRewards() {
        return Collections.unmodifiableSet(pendingRewards);
    }

    public void addPendingReward(String rewardKey) {
        if (rewardKey != null) pendingRewards.add(rewardKey);
    }

    public void removePendingReward(String rewardKey) {
        if (rewardKey != null) pendingRewards.remove(rewardKey);
    }

    public void restorePendingRewards(Iterable<String> keys) {
        pendingRewards.clear();
        if (keys != null) for (String key : keys) addPendingReward(key);
    }

    /**
     * Vérifie puis réserve atomiquement un kill. La clé ne contient pas le
     * tueur: toute la faction partage la limite pour une victime donnée.
     */
    public synchronized boolean tryRecordPvpKill(String questId, String victimId,
            long nowMillis, int cooldownSeconds, int maxPerVictimPerDay) {
        if (questId == null || victimId == null || nowMillis < 0L) return false;
        String key = questId + "|" + victimId;
        long epochDay = Math.floorDiv(nowMillis, 86_400_000L);
        PvpKillRecord previous = pvpKillLedger.get(key);
        if (previous != null) {
            long cooldownMillis = Math.max(0L, cooldownSeconds) * 1000L;
            if (cooldownMillis > 0L && nowMillis >= previous.lastKillMillis
                    && nowMillis - previous.lastKillMillis < cooldownMillis) {
                return false;
            }
            if (maxPerVictimPerDay > 0 && previous.epochDay == epochDay
                    && previous.dailyCount >= maxPerVictimPerDay) {
                return false;
            }
        }
        int daily = previous != null && previous.epochDay == epochDay
                ? previous.dailyCount + 1 : 1;
        pvpKillLedger.put(key, new PvpKillRecord(nowMillis, epochDay, daily));
        return true;
    }

    public Map<String, PvpKillRecord> snapshotPvpKillLedger() {
        return Collections.unmodifiableMap(
                new LinkedHashMap<String, PvpKillRecord>(pvpKillLedger));
    }

    public synchronized void restorePvpKillLedger(
            Map<String, PvpKillRecord> values) {
        pvpKillLedger.clear();
        if (values != null) pvpKillLedger.putAll(values);
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(schemaVersion, lockedTierId, lockedTierRank,
                levelStarted, pendingTransition,
                new LinkedHashMap<String, Long>(questProgress),
                new LinkedHashMap<String, Long>(archivedProgress),
                new java.util.LinkedHashSet<String>(pendingRewards),
                new LinkedHashMap<String, PvpKillRecord>(pvpKillLedger));
    }

    public synchronized void restore(Snapshot snapshot) {
        if (snapshot == null) return;
        schemaVersion = snapshot.schemaVersion;
        lockedTierId = snapshot.lockedTierId;
        lockedTierRank = snapshot.lockedTierRank;
        levelStarted = snapshot.levelStarted;
        pendingTransition = snapshot.pendingTransition;
        questProgress.clear();
        questProgress.putAll(snapshot.questProgress);
        archivedProgress.clear();
        archivedProgress.putAll(snapshot.archivedProgress);
        pendingRewards.clear();
        pendingRewards.addAll(snapshot.pendingRewards);
        pvpKillLedger.clear();
        pvpKillLedger.putAll(snapshot.pvpKillLedger);
    }

    private void archiveCurrent(String prefix) {
        if (levelStarted <= 0 || questProgress.isEmpty()) return;
        for (Map.Entry<String, Long> entry : questProgress.entrySet()) {
            archivedProgress.put(prefix + "." + entry.getKey(), entry.getValue());
        }
    }

    private static String emptyToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static final class Snapshot {
        private final int schemaVersion;
        private final String lockedTierId;
        private final int lockedTierRank;
        private final int levelStarted;
        private final String pendingTransition;
        private final Map<String, Long> questProgress;
        private final Map<String, Long> archivedProgress;
        private final Set<String> pendingRewards;
        private final Map<String, PvpKillRecord> pvpKillLedger;

        private Snapshot(int schemaVersion, String lockedTierId,
                int lockedTierRank, int levelStarted, String pendingTransition,
                Map<String, Long> questProgress,
                Map<String, Long> archivedProgress,
                Set<String> pendingRewards,
                Map<String, PvpKillRecord> pvpKillLedger) {
            this.schemaVersion = schemaVersion;
            this.lockedTierId = lockedTierId;
            this.lockedTierRank = lockedTierRank;
            this.levelStarted = levelStarted;
            this.pendingTransition = pendingTransition;
            this.questProgress = questProgress;
            this.archivedProgress = archivedProgress;
            this.pendingRewards = pendingRewards;
            this.pvpKillLedger = pvpKillLedger;
        }
    }

    public static final class PvpKillRecord {
        private final long lastKillMillis;
        private final long epochDay;
        private final int dailyCount;

        public PvpKillRecord(long lastKillMillis, long epochDay, int dailyCount) {
            this.lastKillMillis = Math.max(0L, lastKillMillis);
            this.epochDay = epochDay;
            this.dailyCount = Math.max(0, dailyCount);
        }

        public long getLastKillMillis() { return lastKillMillis; }
        public long getEpochDay() { return epochDay; }
        public int getDailyCount() { return dailyCount; }
    }
}
