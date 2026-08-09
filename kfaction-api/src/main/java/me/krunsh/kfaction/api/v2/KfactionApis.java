package me.krunsh.kfaction.api.v2;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Helper optionnel pour récupérer l'API V2 depuis Bukkit ServicesManager.
 */
public final class KfactionApis {

    private KfactionApis() {
    }

    public static KfactionApiV2 get() {
        try {
            RegisteredServiceProvider<KfactionApiV2> registration =
                    Bukkit.getServicesManager().getRegistration(KfactionApiV2.class);
            return registration != null ? registration.getProvider() : null;
        } catch (LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    public static boolean isAvailable() {
        return get() != null;
    }

    public static KfactionApiV23 getV23() {
        try {
            RegisteredServiceProvider<KfactionApiV23> registration =
                    Bukkit.getServicesManager().getRegistration(KfactionApiV23.class);
            return registration != null ? registration.getProvider() : null;
        } catch (LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    public static KfactionPlayerActions getPlayerActions() {
        try {
            RegisteredServiceProvider<KfactionPlayerActions> registration =
                    Bukkit.getServicesManager().getRegistration(KfactionPlayerActions.class);
            return registration != null ? registration.getProvider() : null;
        } catch (LinkageError | RuntimeException ignored) {
            return null;
        }
    }
}
