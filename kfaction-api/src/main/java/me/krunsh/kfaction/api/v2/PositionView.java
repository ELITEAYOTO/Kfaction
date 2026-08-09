package me.krunsh.kfaction.api.v2;

/** Coordonnées de bloc immuables, sans retenir un World ou Location Bukkit. */
public final class PositionView {

    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public PositionView(String world, double x, double y, double z, float yaw, float pitch) {
        if (world == null || world.trim().isEmpty()) {
            throw new IllegalArgumentException("world cannot be empty");
        }
        if (!finite(x) || !finite(y) || !finite(z)
                || Float.isNaN(yaw) || Float.isInfinite(yaw)
                || Float.isNaN(pitch) || Float.isInfinite(pitch)) {
            throw new IllegalArgumentException("coordinates must be finite");
        }
        this.world = world.trim();
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    public String getWorld() { return world; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
}
