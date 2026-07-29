package me.krunsh.kfaction.managers;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import me.krunsh.kfaction.Kfaction;

/**
 * Gestionnaire de la configuration principale
 * Gère le fichier config.yml
 */
public class ConfigManager {
    
    private final Kfaction plugin;
    private FileConfiguration config;
    private File configFile;
    
    public ConfigManager(Kfaction plugin) {
        this.plugin = plugin;
        reload(); // Charger immédiatement la config
    }
    
    /**
     * Initialise et charge la configuration
     */
    public void initialize() {
        reload();
        plugin.getLogger().info("ConfigManager initialisé");
    }
    
    /**
     * Recharge la configuration depuis le fichier
     */
    public void reload() {
        if (configFile == null) {
            configFile = new File(plugin.getDataFolder(), "config.yml");
        }
        
        // Créer le fichier par défaut s'il n'existe pas
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        
        config = YamlConfiguration.loadConfiguration(configFile);
    }
    
    /**
     * Sauvegarde la configuration
     */
    public void save() {
        if (config == null || configFile == null) return;
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder config.yml: " + e.getMessage());
        }
    }
    
    // === Getters typés ===
    
    public FileConfiguration getConfig() {
        return config;
    }
    
    public String getString(String path) {
        return config.getString(path);
    }
    
    public String getString(String path, String defaultValue) {
        return config.getString(path, defaultValue);
    }
    
    public int getInt(String path) {
        return config.getInt(path);
    }
    
    public int getInt(String path, int defaultValue) {
        return config.getInt(path, defaultValue);
    }
    
    public double getDouble(String path) {
        return config.getDouble(path);
    }
    
    public double getDouble(String path, double defaultValue) {
        return config.getDouble(path, defaultValue);
    }
    
    public boolean getBoolean(String path) {
        return config.getBoolean(path);
    }
    
    public boolean getBoolean(String path, boolean defaultValue) {
        return config.getBoolean(path, defaultValue);
    }
    
    public long getLong(String path, long defaultValue) {
        return config.getLong(path, defaultValue);
    }
    
    public List<String> getStringList(String path) {
        return config.getStringList(path);
    }
    
    public boolean contains(String path) {
        return config.contains(path);
    }
    
    public void set(String path, Object value) {
        config.set(path, value);
    }
}
