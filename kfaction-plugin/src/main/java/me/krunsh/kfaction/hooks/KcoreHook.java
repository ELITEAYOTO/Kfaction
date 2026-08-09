package me.krunsh.kfaction.hooks;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.api.v2.KfactionApiV2;
import me.krunsh.kfaction.api.v2.KfactionApis;
import me.krunsh.kfaction.api.v2.TerritoryView;
import me.krunsh.kfaction.data.FLocation;

/**
 * Adaptateur Kcore V2.
 *
 * Toute lecture de territoire/PvP passe par l'API publique Kfaction.
 */
public final class KcoreHook {

    private final Kfaction plugin;

    private volatile KfactionApiV2 cachedApi;

    public KcoreHook(
            Kfaction plugin
    ) {
        this.plugin = plugin;
    }

    public void initialize() {
        /*
         * L'ancienne version n'avait pas encore de vraie API provider Kcore.
         * On conserve cette façade stable pour les appels existants.
         */
    }

    public boolean isSafeZone(
            Location location
    ) {
        TerritoryView territory =
                territory(location);

        return territory != null
                && territory.isSafezone();
    }

    public boolean isWarZone(
            Location location
    ) {
        TerritoryView territory =
                territory(location);

        return territory != null
                && territory.isWarzone();
    }

    public boolean canPvP(
            Player attacker,
            Player defender
    ) {
        KfactionApiV2 api =
                api();

        return api != null
                && api.canPvp(
                        attacker,
                        defender
                );
    }

    public String getZoneName(
            Location location
    ) {
        TerritoryView territory =
                territory(location);

        if (territory == null) {
            return "Wilderness";
        }

        if (territory.getFactionName() != null
                && !territory.getFactionName()
                        .trim()
                        .isEmpty()) {
            return territory.getFactionName();
        }

        switch (territory.getType()) {
            case SAFEZONE:
                return "SafeZone";

            case WARZONE:
                return "WarZone";

            case GLOBAL_ZONE:
                return territory.getZoneDisplayName() != null
                        ? territory.getZoneDisplayName()
                        : territory.getZoneId() != null
                                ? territory.getZoneId()
                                : "GlobalZone";

            case FACTION:
                return territory.getFactionId() != null
                        ? territory.getFactionId()
                        : "Faction";

            default:
                return "Wilderness";
        }
    }

    private KfactionApiV2 api() {
        KfactionApiV2 current = cachedApi;

        if (current == null) {
            current = KfactionApis.get();

            if (current != null) {
                cachedApi = current;
            }
        }

        return current;
    }

    private TerritoryView territory(
            Location location
    ) {
        KfactionApiV2 api =
                api();

        if (api == null
                || location == null
                || location.getWorld() == null) {
            return null;
        }

        return api.getTerritory(
                new FLocation(location),
                null
        );
    }
}
