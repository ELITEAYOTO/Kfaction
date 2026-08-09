package me.krunsh.kfaction.progression;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

/**
 * Résolution soft-depend par réflexion: KFaction continue de charger sans
 * WorldGuard et accepte aussi l'implémentation WG6 fournie par FAWE.
 */
public final class WorldGuardRegionResolver {
    private WorldGuardRegionResolver() {}

    public static Set<String> resolve(Location location) {
        if (location == null || location.getWorld() == null) {
            return Collections.emptySet();
        }
        Plugin worldGuard = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (worldGuard == null || !worldGuard.isEnabled()) {
            return Collections.emptySet();
        }
        try {
            Method getManager = worldGuard.getClass()
                    .getMethod("getRegionManager", org.bukkit.World.class);
            Object manager = getManager.invoke(worldGuard, location.getWorld());
            if (manager == null) return Collections.emptySet();
            Method applicable = manager.getClass()
                    .getMethod("getApplicableRegions", Location.class);
            Object set = applicable.invoke(manager, location);
            Method getRegions = set.getClass().getMethod("getRegions");
            Iterable<?> regions = (Iterable<?>) getRegions.invoke(set);
            LinkedHashSet<String> ids = new LinkedHashSet<String>();
            for (Object region : regions) {
                Object id = region.getClass().getMethod("getId").invoke(region);
                if (id != null) ids.add(String.valueOf(id).toLowerCase(
                        java.util.Locale.ROOT));
            }
            return Collections.unmodifiableSet(ids);
        } catch (ReflectiveOperationException | LinkageError ex) {
            return Collections.emptySet();
        }
    }
}
