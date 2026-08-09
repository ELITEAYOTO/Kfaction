package me.krunsh.kfaction.progression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Vue diagnostique immuable de la progression d'une faction.
 */
public final class ProgressionStatus {

    public enum Health {
        DISABLED,
        READY,
        BLOCKED_PENDING_REWARD,
        BLOCKED_STATE_MISMATCH
    }

    private final Health health;
    private final int factionLevel;
    private final int levelStarted;
    private final int maxLevel;

    private final String tierId;

    private final int completedQuests;
    private final int totalQuests;
    private final int percent;

    private final String pendingTransition;
    private final List<String> pendingRewards;

    private final long lastProgressAt;
    private final long lastLevelUpAt;
    private final long transitionRevision;

    public ProgressionStatus(
            Health health,
            int factionLevel,
            int levelStarted,
            int maxLevel,
            String tierId,
            int completedQuests,
            int totalQuests,
            int percent,
            String pendingTransition,
            List<String> pendingRewards,
            long lastProgressAt,
            long lastLevelUpAt,
            long transitionRevision
    ) {
        this.health =
                health != null
                        ? health
                        : Health.DISABLED;

        this.factionLevel =
                Math.max(
                        0,
                        factionLevel
                );

        this.levelStarted =
                Math.max(
                        0,
                        levelStarted
                );

        this.maxLevel =
                Math.max(
                        0,
                        maxLevel
                );

        this.tierId = tierId;

        this.completedQuests =
                Math.max(
                        0,
                        completedQuests
                );

        this.totalQuests =
                Math.max(
                        0,
                        totalQuests
                );

        this.percent =
                Math.max(
                        0,
                        Math.min(
                                100,
                                percent
                        )
                );

        this.pendingTransition =
                pendingTransition;

        this.pendingRewards =
                Collections.unmodifiableList(
                        new ArrayList<String>(
                                pendingRewards != null
                                        ? pendingRewards
                                        : Collections.<String>
                                                emptyList()
                        )
                );

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

    public Health getHealth() {
        return health;
    }

    public boolean isHealthy() {
        return health == Health.READY;
    }

    public int getFactionLevel() {
        return factionLevel;
    }

    public int getLevelStarted() {
        return levelStarted;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public String getTierId() {
        return tierId;
    }

    public int getCompletedQuests() {
        return completedQuests;
    }

    public int getTotalQuests() {
        return totalQuests;
    }

    public int getPercent() {
        return percent;
    }

    public String getPendingTransition() {
        return pendingTransition;
    }

    public List<String> getPendingRewards() {
        return pendingRewards;
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
}
