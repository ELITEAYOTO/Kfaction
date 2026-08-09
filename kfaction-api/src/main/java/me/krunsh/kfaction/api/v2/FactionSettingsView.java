package me.krunsh.kfaction.api.v2;

/** Paramètres utiles à une interface, sans exposer l'objet Faction. */
public final class FactionSettingsView {

    private final String factionId;
    private final PositionView home;
    private final boolean open;
    private final boolean permanent;
    private final boolean factionFlyEnabled;
    private final boolean antiSethomeEnabled;
    private final int maxMembers;
    private final int maxWarps;

    public FactionSettingsView(String factionId, PositionView home, boolean open,
                               boolean permanent, boolean factionFlyEnabled,
                               boolean antiSethomeEnabled, int maxMembers, int maxWarps) {
        if (factionId == null) throw new IllegalArgumentException("factionId cannot be null");
        this.factionId = factionId;
        this.home = home;
        this.open = open;
        this.permanent = permanent;
        this.factionFlyEnabled = factionFlyEnabled;
        this.antiSethomeEnabled = antiSethomeEnabled;
        this.maxMembers = Math.max(0, maxMembers);
        this.maxWarps = Math.max(0, maxWarps);
    }

    public String getFactionId() { return factionId; }
    public PositionView getHome() { return home; }
    public boolean isOpen() { return open; }
    public boolean isPermanent() { return permanent; }
    public boolean isFactionFlyEnabled() { return factionFlyEnabled; }
    public boolean isAntiSethomeEnabled() { return antiSethomeEnabled; }
    public int getMaxMembers() { return maxMembers; }
    public int getMaxWarps() { return maxWarps; }
}
