package me.krunsh.kfaction.api.v2;

/** Warp public sans secret ni Location Bukkit mutable. */
public final class WarpView {

    private final String name;
    private final PositionView position;
    private final boolean passwordProtected;
    private final long createdAt;
    private final String createdBy;
    private final long updatedAt;

    public WarpView(String name, PositionView position, boolean passwordProtected,
                    long createdAt, String createdBy, long updatedAt) {
        if (name == null || name.trim().isEmpty() || position == null) {
            throw new IllegalArgumentException("name and position are required");
        }
        this.name = name.trim();
        this.position = position;
        this.passwordProtected = passwordProtected;
        this.createdAt = Math.max(0L, createdAt);
        this.createdBy = createdBy;
        this.updatedAt = Math.max(0L, updatedAt);
    }

    public String getName() { return name; }
    public PositionView getPosition() { return position; }
    public boolean isPasswordProtected() { return passwordProtected; }
    public long getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public long getUpdatedAt() { return updatedAt; }
}
