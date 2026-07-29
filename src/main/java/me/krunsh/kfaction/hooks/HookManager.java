package me.krunsh.kfaction.hooks;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import me.krunsh.kfaction.Kfaction;

/**
 * Gestionnaire des hooks vers les plugins externes
 * Détecte et initialise les intégrations disponibles
 */
public class HookManager {
    
    private final Kfaction plugin;
    
    // Hooks disponibles
    private VaultHook vaultHook;
    private PlaceholderAPIHook placeholderHook;
    private KcoreHook kcoreHook;
    private KchatHook kchatHook;
    private KguiHook kguiHook;
    private KclassementHook kclassementHook;
    
    public HookManager(Kfaction plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Initialise tous les hooks disponibles
     */
    public void initialize() {
        // Vault (économie)
        if (isPluginEnabled("Vault")) {
            vaultHook = new VaultHook(plugin);
            if (vaultHook.initialize()) {
                plugin.getLogger().info("Hook Vault activé");
            } else {
                vaultHook = null;
                plugin.getLogger().warning("Vault trouvé mais pas d'économie disponible");
            }
        }
        
        // PlaceholderAPI
        if (isPluginEnabled("PlaceholderAPI")) {
            placeholderHook = new PlaceholderAPIHook(plugin);
            placeholderHook.initialize();
            plugin.getLogger().info("Hook PlaceholderAPI activé");
        }
        
        // Kcore (système de combat)
        if (isPluginEnabled("Kcore")) {
            kcoreHook = new KcoreHook(plugin);
            kcoreHook.initialize();
            plugin.getLogger().info("Hook Kcore activé");
        }
        
        // Kchat (système de chat)
        if (isPluginEnabled("Kchat")) {
            kchatHook = new KchatHook(plugin);
            kchatHook.initialize();
            plugin.getLogger().info("Hook Kchat activé");
        }
        
        // Kgui (système de menus)
        if (isPluginEnabled("Kgui")) {
            kguiHook = new KguiHook(plugin);
            kguiHook.initialize();
            plugin.getLogger().info("Hook Kgui activé");
        }
        
        // Kclassement (F-Top)
        if (isPluginEnabled("Kclassement")) {
            kclassementHook = new KclassementHook(plugin);
            kclassementHook.initialize();
            plugin.getLogger().info("Hook Kclassement activé");
        }
    }
    
    /**
     * Ferme tous les hooks
     */
    public void shutdown() {
        if (placeholderHook != null) {
            placeholderHook.shutdown();
        }
    }
    
    /**
     * Vérifie si un plugin est chargé et activé
     */
    private boolean isPluginEnabled(String name) {
        Plugin p = Bukkit.getPluginManager().getPlugin(name);
        return p != null && p.isEnabled();
    }
    
    // === Getters ===
    
    public boolean hasVault() {
        return vaultHook != null;
    }
    
    public VaultHook getVaultHook() {
        return vaultHook;
    }
    
    public boolean hasPlaceholderAPI() {
        return placeholderHook != null;
    }
    
    public PlaceholderAPIHook getPlaceholderHook() {
        return placeholderHook;
    }
    
    public boolean hasKcore() {
        return kcoreHook != null;
    }
    
    public KcoreHook getKcoreHook() {
        return kcoreHook;
    }
    
    public boolean hasKchat() {
        return kchatHook != null;
    }
    
    public KchatHook getKchatHook() {
        return kchatHook;
    }
    
    public boolean hasKgui() {
        return kguiHook != null;
    }
    
    public KguiHook getKguiHook() {
        return kguiHook;
    }
    
    public boolean hasKclassement() {
        return kclassementHook != null;
    }
    
    public KclassementHook getKclassementHook() {
        return kclassementHook;
    }
}
