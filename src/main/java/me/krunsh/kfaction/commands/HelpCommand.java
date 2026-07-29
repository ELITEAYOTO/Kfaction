package me.krunsh.kfaction.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import me.krunsh.kfaction.Kfaction;

/**
 * Commande /f help - Affiche l'aide
 */
public class HelpCommand extends SubCommand {
    
    public HelpCommand(Kfaction plugin) {
        super(plugin);
    }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        // Check if requesting admin help
        if (args.length > 0 && (args[0].equalsIgnoreCase("admin") || args[0].equalsIgnoreCase("adm"))) {
            showAdminHelp(sender);
            return;
        }
        
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
            "&8&m----------------------------------------"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
            "&6&l    ⚔ Kfaction - Aide ⚔"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
            "&8&m----------------------------------------"));
        sender.sendMessage("");
        
        sendHelpLine(sender, "create <nom>", "Créer une faction");
        sendHelpLine(sender, "disband", "Dissoudre votre faction");
        sendHelpLine(sender, "join <faction>", "Rejoindre une faction");
        sendHelpLine(sender, "leave", "Quitter votre faction");
        sendHelpLine(sender, "invite <joueur>", "Inviter un joueur");
        sendHelpLine(sender, "kick <joueur>", "Exclure un joueur");
        sender.sendMessage("");
        
        sendHelpLine(sender, "claim", "Revendiquer un chunk");
        sendHelpLine(sender, "unclaim", "Abandonner un chunk");
        sendHelpLine(sender, "map", "Afficher la carte");
        sendHelpLine(sender, "home", "Se téléporter au home");
        sendHelpLine(sender, "sethome", "Définir le home");
        sendHelpLine(sender, "warp <nom>", "Se téléporter à un warp");
        sendHelpLine(sender, "setwarp <nom>", "Créer un warp");
        sender.sendMessage("");
        
        sendHelpLine(sender, "show [faction]", "Infos sur une faction");
        sendHelpLine(sender, "list", "Liste des factions");
        sendHelpLine(sender, "power", "Voir votre power");
        sendHelpLine(sender, "top", "Classement des factions");
        sender.sendMessage("");
        
        sendHelpLine(sender, "ally <faction>", "Proposer une alliance");
        sendHelpLine(sender, "enemy <faction>", "Déclarer un ennemi");
        sendHelpLine(sender, "chat [mode]", "Basculer le mode chat");
        sendHelpLine(sender, "tnt <deposit|withdraw>", "Gérer la banque TNT");
        sender.sendMessage("");
        
        if (sender.hasPermission("kfaction.admin")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&7Utilisez &e/f help admin &7pour les commandes admin."));
        }
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
            "&8&m----------------------------------------"));
    }
    
    private void showAdminHelp(CommandSender sender) {
        if (!sender.hasPermission("kfaction.admin")) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission.");
            return;
        }
        
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
            "&8&m----------------------------------------"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
            "&c&l    ⚡ Kfaction Admin ⚡"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
            "&8&m----------------------------------------"));
        sender.sendMessage("");
        
        sendAdminLine(sender, "admin bypass", "Toggle bypass protection claims");
        sendAdminLine(sender, "admin reload", "Recharger la configuration");
        sendAdminLine(sender, "admin debug", "Informations de debug");
        sender.sendMessage("");
        
        sendAdminLine(sender, "admin setpower <joueur> <val>", "Modifier le power");
        sendAdminLine(sender, "admin setrole <joueur> <role>", "Modifier le rôle");
        sendAdminLine(sender, "admin forcejoin <joueur> <fac>", "Forcer à rejoindre");
        sendAdminLine(sender, "admin forceleave <joueur>", "Forcer à quitter");
        sendAdminLine(sender, "admin forceleader <joueur> <fac>", "Forcer le leadership");
        sender.sendMessage("");
        
        sendAdminLine(sender, "admin disband <faction>", "Dissoudre une faction");
        sendAdminLine(sender, "admin rename <fac> <nom>", "Renommer une faction");
        sendAdminLine(sender, "admin settag <fac> <tag>", "Changer le tag");
        sendAdminLine(sender, "admin setbalance <fac> <val>", "Définir la banque");
        sendAdminLine(sender, "admin lock <faction>", "Verrouiller une faction");
        sendAdminLine(sender, "admin unlock <faction>", "Déverrouiller une faction");
        sender.sendMessage("");
        
        sendAdminLine(sender, "admin teleport <faction>", "TP au home de faction");
        sendAdminLine(sender, "admin claim <warzone|safezone>", "Claim zone système");
        sendAdminLine(sender, "admin unclaim", "Unclaim le chunk actuel");
        sendAdminLine(sender, "admin inspect <faction|joueur>", "Inspecter");
        sender.sendMessage("");
        
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
            "&8&m----------------------------------------"));
    }
    
    private void sendHelpLine(CommandSender sender, String command, String description) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
            "&e/f " + command + " &8- &7" + description));
    }
    
    private void sendAdminLine(CommandSender sender, String command, String description) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
            "&c/f " + command + " &8- &7" + description));
    }
    
    @Override
    public String getName() {
        return "help";
    }
    
    @Override
    public String getDescription() {
        return "Affiche l'aide";
    }
    
    @Override
    public String getUsage() {
        return "[admin]";
    }
    
    @Override
    public boolean isPlayerOnly() {
        return false;
    }
}
