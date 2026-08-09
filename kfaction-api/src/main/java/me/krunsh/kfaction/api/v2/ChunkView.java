package me.krunsh.kfaction.api.v2;

import me.krunsh.kfaction.data.FLocation;

/**
 * Coordonnée de chunk immutable de l'API V2.
 */
public final class ChunkView {

    private final String world;
    private final int x;
    private final int z;

    public ChunkView(
            String world,
            int x,
            int z
    ) {
        this.world = world;
        this.x = x;
        this.z = z;
    }

    public static ChunkView from(
            FLocation location
    ) {
        return location != null
                ? new ChunkView(
                        location.getWorldName(),
                        location.getX(),
                        location.getZ()
                )
                : null;
    }

    public String getWorld() {
        return world;
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    public String getKey() {
        return world
                + ":"
                + x
                + ":"
                + z;
    }

    @Override
    public String toString() {
        return getKey();
    }
}
