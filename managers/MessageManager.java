package me.krunsh.kfaction.managers;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;

/**
 * Gestionnaire des messages et traductions
 * Gère le fichier messages.yml avec cache en mémoire
 */
public class MessageManager {
    
    private final Kfaction plugin;
    private FileConfiguration messages;
    private File messagesFile;
    
    // Cache des messages colorés
    private final Map<String, String> messageCache;
    
    // Préfixe global
    private String prefix;
    
    public MessageManager(Kfaction plugin) {
        this.plugin = plugin;
        this.messageCache = new HashMap<>();
    }
    
    /**
     * Initialise et charge les messages
     */
    public void initialize() {
        reload();
        plugin.getLogger().info("MessageManager initialisé");
    }
    
    /**
     * Recharge les messages depuis le fichier
     */
    public void reload() {
        messageCache.clear();
        
        if (messagesFile == null) {
            messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        }
        
        // Créer le fichier par défaut s'il n'existe pas
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        
        // Charger le préfixe
        prefix = colorize(messages.getString("prefix", "&8[&6Kfaction&8] "));
    }
    
    /**
     * Sauvegarde les messages
     */
    public void save() {
        if (messages == null || messagesFile == null) return;
        try {
            messages.save(messagesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder messages.yml: " + e.getMessage());
        }
    }
    
    // === Récupération de messages ===
    
    /**
     * Obtient un message traduit et coloré
     * @param path Le chemin du message
     * @return Le message coloré ou un message d'erreur
     */
    public String get(String path) {
        return messageCache.computeIfAbsent(path, p -> {
            String msg = messages.getString(p);
            if (msg == null) {
                return ChatColor.RED + "[Message manquant: " + p + "]";
            }
            return colorize(msg);
        });
    }
    
    /**
     * Obtient un message avec le préfixe
     * @param path Le chemin du message
     * @return Le message préfixé
     */
    public String getPrefixed(String path) {
        return prefix + get(path);
    }
    
    /**
     * Obtient un message avec des remplacements
     * @param path Le chemin du message
     * @param replacements Paires clé-valeur de remplacement
     * @return Le message formaté
     */
    public String get(String path, Object... replacements) {
        String msg = get(path);
        if (replacements.length % 2 != 0) {
            plugin.getLogger().warning("Nombre impair de remplacements pour: " + path);
            return msg;
        }
        for (int i = 0; i < replacements.length; i += 2) {
            String key = String.valueOf(replacements[i]);
            String value = String.valueOf(replacements[i + 1]);
            msg = msg.replace(key, value);
        }
        return msg;
    }
    
    /**
     * Obtient un message préfixé avec des remplacements
     * @param path Le chemin du message
     * @param replacements Paires clé-valeur de remplacement
     * @return Le message formaté et préfixé
     */
    public String getPrefixed(String path, Object... replacements) {
        return prefix + get(path, replacements);
    }
    
    // === Envoi de messages ===
    
    /**
     * Envoie un message à un joueur
     * @param player Le joueur
     * @param path Le chemin du message
     */
    public void send(Player player, String path) {
        player.sendMessage(getPrefixed(path));
    }
    
    /**
     * Envoie un message à un joueur avec des remplacements
     * @param player Le joueur
     * @param path Le chemin du message
     * @param replacements Paires clé-valeur
     */
    public void send(Player player, String path, Object... replacements) {
        player.sendMessage(getPrefixed(path, replacements));
    }
    
    /**
     * Envoie un message brut à un joueur
     * @param player Le joueur
     * @param message Le message
     */
    public void sendRaw(Player player, String message) {
        player.sendMessage(colorize(message));
    }
    
    // === Utilitaires ===
    
    /**
     * Colore un texte avec les codes couleur Minecraft
     * @param text Le texte à coloré
     * @return Le texte coloré
     */
    public String colorize(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }
    
    /**
     * Retire les couleurs d'un texte
     * @param text Le texte
     * @return Le texte sans couleur
     */
    public String stripColor(String text) {
        if (text == null) return "";
        return ChatColor.stripColor(colorize(text));
    }
    
    /**
     * @return Le préfixe des messages
     */
    public String getPrefix() {
        return prefix;
    }
    
    /**
     * Vérifie si un message existe
     * @param path Le chemin
     * @return true si le message existe
     */
    public boolean has(String path) {
        return messages.contains(path);
    }
    
    /**
     * Obtient un message brut sans couleur
     * @param path Le chemin
     * @return Le message brut
     */
    public String getRaw(String path) {
        return messages.getString(path, "");
    }
}
