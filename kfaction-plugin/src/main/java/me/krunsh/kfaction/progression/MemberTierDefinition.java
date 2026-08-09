package me.krunsh.kfaction.progression;

/** Tranche de membres entièrement pilotée par progression.yml. */
public final class MemberTierDefinition {
    private final String id;
    private final String displayName;
    private final int minPlayers;
    private final int maxPlayers;
    private final int rank;

    public MemberTierDefinition(String id, String displayName, int minPlayers,
            int maxPlayers, int rank) {
        this.id = id;
        this.displayName = displayName;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.rank = rank;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public int getMinPlayers() { return minPlayers; }
    public int getMaxPlayers() { return maxPlayers; }
    public int getRank() { return rank; }

    public boolean contains(int players) {
        return players >= minPlayers && players <= maxPlayers;
    }
}
