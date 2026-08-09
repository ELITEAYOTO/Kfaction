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
        RegisteredServiceProvider<KfactionApiV2> registration =
                Bukkit.getServicesManager()
                        .getRegistration(
                                KfactionApiV2.class
                        );

        return registration != null
                ? registration.getProvider()
                : null;
    }

    public static boolean isAvailable() {
        return get() != null;
    }
}
