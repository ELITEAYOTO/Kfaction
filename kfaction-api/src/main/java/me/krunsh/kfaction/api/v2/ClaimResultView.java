package me.krunsh.kfaction.api.v2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Résultat public d'un claim.
 */
public final class ClaimResultView {

    private final String mode;
    private final int requestedCount;
    private final int claimedCount;
    private final int skippedOwnedCount;
    private final int overclaimCount;
    private final List<ChunkView> locations;

    public ClaimResultView(
            String mode,
            int requestedCount,
            int claimedCount,
            int skippedOwnedCount,
            int overclaimCount,
            List<ChunkView> locations
    ) {
        this.mode = mode;
        this.requestedCount = Math.max(0, requestedCount);
        this.claimedCount = Math.max(0, claimedCount);
        this.skippedOwnedCount = Math.max(0, skippedOwnedCount);
        this.overclaimCount = Math.max(0, overclaimCount);

        this.locations =
                Collections.unmodifiableList(
                        new ArrayList<ChunkView>(
                                locations != null
                                        ? locations
                                        : Collections.<ChunkView>emptyList()
                        )
                );
    }

    public String getMode() {
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

    public List<ChunkView> getLocations() {
        return locations;
    }

    public boolean hasOverclaims() {
        return overclaimCount > 0;
    }
}
