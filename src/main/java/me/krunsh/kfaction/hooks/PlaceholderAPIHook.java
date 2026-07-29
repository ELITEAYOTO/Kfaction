package me.krunsh.kfaction.hooks;

import me.clip.placeholderapi.PlaceholderAPI;
import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.placeholders.KfactionExpansion;

/**
 * Hook pour PlaceholderAPI
 * Délègue à KfactionExpansion pour les placeholders
 */
public class PlaceholderAPIHook {
    
    private final Kfaction plugin;
    private KfactionExpansion expansion;
    
    public PlaceholderAPIHook(Kfaction plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Initialise l'expansion
     */
    public void initialize() {
        // Vérifier si une expansion kfaction est déjà enregistrée (évite le doublon)
        if (PlaceholderAPI.isRegistered("kfaction")) {
            plugin.getLogger().info("PlaceholderAPI: expansion kfaction déjà enregistrée");
            return;
        }
        
        expansion = new KfactionExpansion(plugin);
        expansion.register();
        plugin.getLogger().info("PlaceholderAPI hook activé");
    }
    
    /**
     * Ferme l'expansion
     */
    public void shutdown() {
        if (expansion != null) {
            expansion.unregister();
        }
    }
}
