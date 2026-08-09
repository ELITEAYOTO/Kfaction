package me.krunsh.kfaction.hooks;

import org.bukkit.Bukkit;

import me.clip.placeholderapi.PlaceholderAPI;
import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.placeholders.KfactionExpansion;

/**
 * Lifecycle idempotent de l'expansion PlaceholderAPI.
 */
public final class PlaceholderAPIHook {

    private final Kfaction plugin;

    private KfactionExpansion expansion;

    private boolean registered;
    private boolean ownedRegistration;
    private boolean activationScheduled;

    public PlaceholderAPIHook(
            Kfaction plugin
    ) {
        this.plugin = plugin;
    }

    public void initialize() {
        if (registered
                || activationScheduled) {
            return;
        }

        activationScheduled = true;

        /*
         * Kfaction historique possède encore un registerPlaceholders()
         * plus tard dans onEnable. Le tick différé évite tout double
         * enregistrement et garantit que l'API V2 est déjà disponible.
         */
        Bukkit.getScheduler()
                .runTask(
                        plugin,
                        new Runnable() {
                            @Override
                            public void run() {
                                activate();
                            }
                        }
                );
    }

    private void activate() {
        activationScheduled = false;

        if (PlaceholderAPI.isRegistered(
                "kfaction"
        )) {
            registered = true;
            ownedRegistration = false;
            expansion = null;

            me.krunsh.kfaction.utils.KfactionLogger.debug(
                    plugin,
                    "PlaceholderAPI: expansion kfaction déjà enregistrée."
            );

            return;
        }

        expansion =
                new KfactionExpansion(
                        plugin
                );

        ownedRegistration =
                expansion.register();

        registered =
                ownedRegistration
                        || PlaceholderAPI.isRegistered(
                                "kfaction"
                        );

        if (!registered) {
            expansion = null;

            plugin.getLogger().warning(
                    "PlaceholderAPI: échec d'enregistrement de l'expansion kfaction"
            );
        }
    }

    public void shutdown() {
        activationScheduled = false;

        if (ownedRegistration
                && expansion != null) {
            try {
                expansion.unregister();
            } finally {
                ownedRegistration = false;
                registered = false;
                expansion = null;
            }
        }
    }

    public boolean isActivationScheduled() {
        return activationScheduled;
    }

    public boolean isRegistered() {
        return registered;
    }

    public boolean ownsRegistration() {
        return ownedRegistration;
    }
}
