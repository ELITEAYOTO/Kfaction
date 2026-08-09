package me.krunsh.kfaction.services.claim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.krunsh.kfaction.api.event.FactionClaimEvent.ClaimType;
import me.krunsh.kfaction.data.FLocation;

/**
 * Plan immuable d'une opération de claim.
 *
 * Aucun changement du domaine n'a encore eu lieu lorsqu'un ClaimPlan existe.
 * Il peut donc être validé entièrement avant la phase PRE events puis commit.
 */
public final class ClaimPlan {

    public enum Mode {
        SINGLE,
        RADIUS,
        FILL
    }

    public static final class Entry {

        private final FLocation location;
        private final String previousOwnerId;
        private final ClaimType claimType;

        public Entry(
                FLocation location,
                String previousOwnerId,
                ClaimType claimType
        ) {
            if (location == null) {
                throw new IllegalArgumentException(
                        "location cannot be null"
                );
            }
            if (claimType == null) {
                throw new IllegalArgumentException(
                        "claimType cannot be null"
                );
            }

            this.location = location;
            this.previousOwnerId = previousOwnerId;
            this.claimType = claimType;
        }

        public FLocation getLocation() {
            return location;
        }

        public String getPreviousOwnerId() {
            return previousOwnerId;
        }

        public ClaimType getClaimType() {
            return claimType;
        }

        public boolean isOverclaim() {
            return claimType == ClaimType.OVERCLAIM;
        }
    }

    private final Mode mode;
    private final List<Entry> entries;
    private final int requestedCount;
    private final int skippedOwnedCount;

    public ClaimPlan(
            Mode mode,
            List<Entry> entries,
            int requestedCount,
            int skippedOwnedCount
    ) {
        if (mode == null) {
            throw new IllegalArgumentException(
                    "mode cannot be null"
            );
        }
        if (entries == null) {
            throw new IllegalArgumentException(
                    "entries cannot be null"
            );
        }
        if (requestedCount < 0
                || skippedOwnedCount < 0) {
            throw new IllegalArgumentException(
                    "counts cannot be negative"
            );
        }

        this.mode = mode;
        this.entries =
                Collections.unmodifiableList(
                        new ArrayList<Entry>(entries)
                );
        this.requestedCount = requestedCount;
        this.skippedOwnedCount = skippedOwnedCount;
    }

    public Mode getMode() {
        return mode;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public int getRequestedCount() {
        return requestedCount;
    }

    public int getSkippedOwnedCount() {
        return skippedOwnedCount;
    }

    public int getMutationCount() {
        return entries.size();
    }

    public int getOverclaimCount() {
        int count = 0;

        for (Entry entry : entries) {
            if (entry.isOverclaim()) {
                count++;
            }
        }

        return count;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
