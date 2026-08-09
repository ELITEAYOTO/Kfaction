package me.krunsh.kfaction.services.claim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.krunsh.kfaction.api.event.FactionUnclaimEvent.UnclaimType;
import me.krunsh.kfaction.data.FLocation;

/**
 * Résultat immuable d'un batch d'unclaim.
 */
public final class UnclaimBatchResult {

    private final UnclaimType type;
    private final int unclaimedCount;
    private final int claimGroupAssignmentsRemoved;
    private final boolean homeRemoved;
    private final List<String> removedWarps;
    private final List<FLocation> locations;

    public UnclaimBatchResult(
            UnclaimType type,
            int unclaimedCount,
            int claimGroupAssignmentsRemoved,
            boolean homeRemoved,
            List<String> removedWarps,
            List<FLocation> locations
    ) {
        this.type = type;
        this.unclaimedCount = unclaimedCount;
        this.claimGroupAssignmentsRemoved =
                claimGroupAssignmentsRemoved;
        this.homeRemoved = homeRemoved;

        this.removedWarps =
                Collections.unmodifiableList(
                        new ArrayList<String>(
                                removedWarps
                        )
                );

        this.locations =
                Collections.unmodifiableList(
                        new ArrayList<FLocation>(
                                locations
                        )
                );
    }

    public UnclaimType getType() {
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

    public List<FLocation> getLocations() {
        return locations;
    }
}
