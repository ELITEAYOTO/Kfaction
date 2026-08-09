package me.krunsh.kfaction.policy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import me.krunsh.kfaction.data.FLocation;

/** Sélection bornée des chunks d'une safezone/warzone. */
public final class ZoneUnclaimSelection {

    private ZoneUnclaimSelection() {
    }

    public static boolean isZoneType(String type) {
        return "warzone".equalsIgnoreCase(type) || "safezone".equalsIgnoreCase(type);
    }

    public static List<FLocation> radius(Collection<FLocation> zoneClaims,
                                         FLocation center, int radius) {
        if (zoneClaims == null || center == null || radius < 0) {
            return Collections.emptyList();
        }
        List<FLocation> selected = new ArrayList<>();
        for (FLocation location : zoneClaims) {
            if (center.getWorldName().equals(location.getWorldName())
                    && Math.abs(location.getX() - center.getX()) <= radius
                    && Math.abs(location.getZ() - center.getZ()) <= radius) {
                selected.add(location);
            }
        }
        return selected;
    }
}
