package me.krunsh.kfaction.services.claim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.krunsh.kfaction.api.event.FactionUnclaimEvent.UnclaimType;
import me.krunsh.kfaction.data.FLocation;

/**
 * Plan immuable d'un unclaim V2.
 */
public final class UnclaimPlan {

    public static final class Entry {

        private final FLocation location;
        private final String claimGroupId;

        public Entry(
                FLocation location,
                String claimGroupId
        ) {
            if (location == null) {
                throw new IllegalArgumentException(
                        "location cannot be null"
                );
            }

            this.location = location;
            this.claimGroupId = claimGroupId;
        }

        public FLocation getLocation() {
            return location;
        }

        public String getClaimGroupId() {
            return claimGroupId;
        }
    }

    private final UnclaimType type;
    private final List<Entry> entries;

    public UnclaimPlan(
            UnclaimType type,
            List<Entry> entries
    ) {
        if (type == null || entries == null) {
            throw new IllegalArgumentException(
                    "type/entries cannot be null"
            );
        }

        this.type = type;
        this.entries =
                Collections.unmodifiableList(
                        new ArrayList<Entry>(entries)
                );
    }

    public UnclaimType getType() {
        return type;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
