package me.krunsh.kfaction.commands;

import java.util.Collections;
import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;

/**
 * Classe de base pour toutes les sous-commandes de /kfaction
 */
public abstract class SubCommand {
    
    protected final Kfaction plugin;
    
    public SubCommand(Kfaction plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Exécute la sous-commande
     * @param sender L'expéditeur de la commande
     * @param args Les arguments (sans le nom de la sous-commande)
     */
    public abstract void execute(CommandSender sender, String[] args);
    
    /**
     * @return Le nom de la sous-commande
     */
    public abstract String getName();
    
    /**
     * @return Description courte de la commande
     */
    public abstract String getDescription();
    
    /**
     * @return L'usage de la commande (ex: "<nom> [faction]")
     */
    public abstract String getUsage();
    
    /**
     * @return La permission requise ou null si aucune
     */
    public String getPermission() {
        return null;
    }
    
    /**
     * @return true si la commande ne peut être exécutée que par un joueur
     */
    public boolean isPlayerOnly() {
        return true;
    }
    
    /**
     * Auto-complétion des arguments
     * @param sender L'expéditeur
     * @param args Les arguments actuels
     * @return Liste des complétions possibles
     */
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
    
    // === Helpers ===
    
    /**
     * Envoie un message traduit au joueur
     */
    protected void sendMessage(CommandSender sender, String path) {
        if (sender instanceof Player) {
            plugin.getMessageManager().send((Player) sender, path);
        } else {
            sender.sendMessage(plugin.getMessageManager().get(path));
        }
    }
    
    /**
     * Envoie un message traduit avec remplacements
     */
    protected void sendMessage(CommandSender sender, String path, Object... replacements) {
        if (sender instanceof Player) {
            plugin.getMessageManager().send((Player) sender, path, replacements);
        } else {
            sender.sendMessage(plugin.getMessageManager().get(path, replacements));
        }
    }
    
    /**
     * Vérifie si l'expéditeur est un joueur
     */
    protected boolean requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cCette commande doit être exécutée par un joueur.");
            return false;
        }
        return true;
    }
    
    /**
     * Cast l'expéditeur en Player de manière sécurisée
     * @return Le Player ou null si l'expéditeur n'est pas un joueur
     */
    protected Player getPlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            return null;
        }
        return (Player) sender;
    }
    
    /**
     * Cast l'expéditeur en Player avec vérification et message d'erreur
     * @return Le Player ou null si l'expéditeur n'est pas un joueur (message envoyé)
     */
    protected Player getPlayerOrWarn(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cCette commande doit être exécutée par un joueur.");
            return null;
        }
        return (Player) sender;
    }
}
