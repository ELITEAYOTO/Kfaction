package me.krunsh.kfaction.services.claim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.krunsh.kfaction.data.FLocation;

/**
 * Résultat métier immuable d'un commit de claims.
 */
public final class ClaimBatchResult {

    private final ClaimPlan.Mode mode;
    private final int requestedCount;
    private final int claimedCount;
    private final int skippedOwnedCount;
    private final int overclaimCount;
    private final List<FLocation> claimedLocations;

    public ClaimBatchResult(
            ClaimPlan.Mode mode,
            int requestedCount,
            int claimedCount,
            int skippedOwnedCount,
            int overclaimCount,
            List<FLocation> claimedLocations
    ) {
        this.mode = mode;
        this.requestedCount = requestedCount;
        this.claimedCount = claimedCount;
        this.skippedOwnedCount = skippedOwnedCount;
        this.overclaimCount = overclaimCount;

        this.claimedLocations =
                Collections.unmodifiableList(
                        new ArrayList<FLocation>(
                                claimedLocations
                        )
                );
    }

    public ClaimPlan.Mode getMode() {
        return mode;
    }

    public int getRequestedCount() {
        return requestedCount;
    }

    public int getClaimedCount() {
        return claimedCount;
    }

    public int getSkippedOwnedCount() {
        return skippedOwnedCount;
    }

    public int getOverclaimCount() {
        return overclaimCount;
    }

    public List<FLocation> getClaimedLocations() {
        return claimedLocations;
    }

    public boolean hasOverclaims() {
        return overclaimCount > 0;
    }

    @Override
    public String toString() {
        return "ClaimBatchResult{" +
                "mode=" + mode +
                ", requestedCount=" + requestedCount +
                ", claimedCount=" + claimedCount +
                ", skippedOwnedCount=" + skippedOwnedCount +
                ", overclaimCount=" + overclaimCount +
                '}';
    }
}
