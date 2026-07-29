package me.krunsh.kfaction.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.Relation;

/**
 * Commande /f show [faction] - Afficher les infos d'une faction
 */
public class ShowCommand extends SubCommand {
    
    public ShowCommand(Kfaction plugin) {
        super(plugin);
    }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        Faction faction;
        
        if (args.length > 0) {
            // Faction spécifiée
            faction = plugin.getFactionManager().getFactionByName(args[0]);
            if (faction == null) {
                sendMessage(sender, "show.faction-not-found", "{name}", args[0]);
                return;
            }
        } else {
            // Faction du joueur
            if (!(sender instanceof Player)) {
                sendMessage(sender, "show.specify-faction");
                return;
            }
            faction = plugin.getFactionManager().getPlayerFaction((Player) sender);
            if (faction == null) {
                sendMessage(sender, "show.not-in-faction");
                return;
            }
        }
        
        displayFactionInfo(sender, faction);
    }
    
    private void displayFactionInfo(CommandSender sender, Faction faction) {
        Faction viewerFaction = null;
        if (sender instanceof Player) {
            viewerFaction = plugin.getFactionManager().getPlayerFaction((Player) sender);
        }
        
        String relationColor = "&f";
        if (viewerFaction != null) {
            Relation relation = viewerFaction.getRelationTo(faction);
            relationColor = relation.getColorPrefix();
        }
        
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
            "&8&m----------------------------------------"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
            relationColor + "&l" + faction.getName()));
        
        if (!faction.getDescription().isEmpty()) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&7" + faction.getDescription()));
        }
        
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
            "&8&m----------------------------------------"));
        
        // Leader
        String leaderName = "Aucun";
        if (faction.getLeader() != null) {
            FPlayer leaderFP = plugin.getFPlayerManager().getFPlayer(faction.getLeader());
            leaderName = leaderFP != null ? leaderFP.getLastKnownName() : "Inconnu";
        }
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
            "&6Chef: &f" + leaderName));
        
        // Membres
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
            "&6Membres: &f" + faction.getMemberCount() + "/" + 
            plugin.getConfigManager().getInt("factions.max-members", 15)));
        
        // Power
        double power = plugin.getPowerManager().getFactionPower(faction);
        double maxPower = plugin.getPowerManager().getFactionMaxPower(faction);
        String powerColor = power < faction.getClaimCount() ? "&c" : "&a";
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
            "&6Power: " + powerColor + String.format("%.1f", power) + 
            "&7/" + String.format("%.1f", maxPower)));
        
        // Claims
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
            "&6Territoire: &f" + faction.getClaimCount() + " chunks"));
        
        // Banque
        if (plugin.getHookManager().hasVault()) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&6Banque: &f" + plugin.getHookManager().getVaultHook().format(faction.getBank())));
        }
        
        // Relations
        Set<String> allies = faction.getAllies();
        if (!allies.isEmpty()) {
            StringBuilder sb = new StringBuilder("&6Alliés: &d");
            for (String allyId : allies) {
                Faction ally = plugin.getFactionManager().getFaction(allyId);
                if (ally != null) {
                    sb.append(ally.getName()).append(", ");
                }
            }
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                sb.substring(0, sb.length() - 2)));
        }
        
        Set<String> enemies = faction.getEnemies();
        if (!enemies.isEmpty()) {
            StringBuilder sb = new StringBuilder("&6Ennemis: &c");
            for (String enemyId : enemies) {
                Faction enemy = plugin.getFactionManager().getFaction(enemyId);
                if (enemy != null) {
                    sb.append(enemy.getName()).append(", ");
                }
            }
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                sb.substring(0, sb.length() - 2)));
        }
        
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
            "&8&m----------------------------------------"));
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String partial = args[0].toLowerCase();
            for (Faction faction : plugin.getFactionManager().getPlayerFactions()) {
                if (faction.getName().toLowerCase().startsWith(partial)) {
                    completions.add(faction.getName());
                }
            }
            return completions;
        }
        return Collections.emptyList();
    }
    
    @Override
    public String getName() {
        return "show";
    }
    
    @Override
    public String getDescription() {
        return "Affiche les informations d'une faction";
    }
    
    @Override
    public String getUsage() {
        return "[faction]";
    }
    
    @Override
    public boolean isPlayerOnly() {
        return false;
    }
}
