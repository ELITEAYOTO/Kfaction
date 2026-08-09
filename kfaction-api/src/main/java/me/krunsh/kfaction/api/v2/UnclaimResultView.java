package me.krunsh.kfaction.api.v2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Résultat public d'un unclaim.
 */
public final class UnclaimResultView {

    private final String type;
    private final int unclaimedCount;
    private final int claimGroupAssignmentsRemoved;
    private final boolean homeRemoved;

    private final List<String> removedWarps;
    private final List<ChunkView> locations;

    public UnclaimResultView(
            String type,
            int unclaimedCount,
            int claimGroupAssignmentsRemoved,
            boolean homeRemoved,
            List<String> removedWarps,
            List<ChunkView> locations
    ) {
        this.type = type;
        this.unclaimedCount = Math.max(0, unclaimedCount);
        this.claimGroupAssignmentsRemoved =
                Math.max(0, claimGroupAssignmentsRemoved);
        this.homeRemoved = homeRemoved;

        this.removedWarps =
                Collections.unmodifiableList(
                        new ArrayList<String>(
                                removedWarps != null
                                        ? removedWarps
                                        : Collections.<String>emptyList()
                        )
                );

        this.locations =
                Collections.unmodifiableList(
                        new ArrayList<ChunkView>(
                                locations != null
                                        ? locations
                                        : Collections.<ChunkView>emptyList()
                        )
                );
    }

    public String getType() {
        return type;
    }

    public int getUnclaimedCount() {
        return unclaimedCount;
    }

    public int getClaimGroupAssignmentsRemoved() {
        return claimGroupAssignmentsRemoved;
    }

    public boolean isHomeRemoved() {
        return homeRemoved;
    }

    public List<String> getRemovedWarps() {
        return removedWarps;
    }

    public List<ChunkView> getLocations() {
        return locations;
    }
}
