package me.krunsh.kfaction.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Location persistable indépendante du chargement runtime d'un World Bukkit.
 *
 * Contrairement à org.bukkit.Location, cet objet peut être restauré même si
 * le monde n'est pas encore chargé au démarrage.
 */
public final class StoredLocation {

    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public StoredLocation(
            String worldName,
            double x,
            double y,
            double z,
            float yaw,
            float pitch
    ) {
        if (worldName == null
                || worldName.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "worldName cannot be empty"
            );
        }

        this.worldName = worldName.trim();
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public static StoredLocation fromBukkit(
            Location location
    ) {
        if (location == null
                || location.getWorld() == null) {
            return null;
        }

        return new StoredLocation(
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
        );
    }

    public String getWorldName() {
        return worldName;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public int getChunkX() {
        return floorToBlock(x) >> 4;
    }

    public int getChunkZ() {
        return floorToBlock(z) >> 4;
    }

    public World resolveWorld() {
        return Bukkit.getWorld(worldName);
    }

    public boolean isWorldAvailable() {
        return resolveWorld() != null;
    }

    public Location toBukkitLocation() {
        World world = resolveWorld();

        if (world == null) {
            return null;
        }

        return new Location(
                world,
                x,
                y,
                z,
                yaw,
                pitch
        );
    }

    public boolean isInChunk(
            FLocation chunk
    ) {
        if (chunk == null) {
            return false;
        }

        return worldName.equalsIgnoreCase(
                chunk.getWorldName()
        )
                && getChunkX() == chunk.getX()
                && getChunkZ() == chunk.getZ();
    }

    public boolean samePosition(
            StoredLocation other
    ) {
        if (other == null) {
            return false;
        }

        return worldName.equalsIgnoreCase(
                other.worldName
        )
                && Double.compare(x, other.x) == 0
                && Double.compare(y, other.y) == 0
                && Double.compare(z, other.z) == 0
                && Float.compare(yaw, other.yaw) == 0
                && Float.compare(pitch, other.pitch) == 0;
    }

    private static int floorToBlock(double value) {
        int integer = (int) value;

        return value < integer
                ? integer - 1
                : integer;
    }

    @Override
    public String toString() {
        return worldName
                + " "
                + x
                + ","
                + y
                + ","
                + z;
    }
}
