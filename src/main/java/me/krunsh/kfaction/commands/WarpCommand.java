package me.krunsh.kfaction.commands;

import java.util.Set;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FactionLog.LogType;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;

/**
 * Commande /f warp <nom> | list | delete <nom> - Gérer les warps de faction
 */
public class WarpCommand extends SubCommand {
    
    public WarpCommand(Kfaction plugin) {
        super(plugin);
    }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        
        if (!fPlayer.hasFaction()) {
            sendMessage(sender, "general.no-faction");
            return;
        }
        
        Faction faction = plugin.getFactionManager().getPlayerFaction(player);
        
        // Sans argument ou "list" -> afficher la liste
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            showWarpList(sender, faction);
            return;
        }
        
        // "delete" ou "del" -> supprimer un warp
        if (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("del")) {
            if (args.length < 2) {
                sendMessage(sender, "warp.usage-delete");
                return;
            }
            deleteWarp(sender, player, faction, args[1]);
            return;
        }
        
        // Sinon, c'est un nom de warp -> téléportation
        teleportToWarp(sender, player, faction, args[0]);
    }
    
    private void showWarpList(CommandSender sender, Faction faction) {
        Set<String> warpNames = faction.getWarpNames();
        
        if (warpNames.isEmpty()) {
            sendMessage(sender, "warp.no-warps");
            return;
        }
        
        int maxWarps = plugin.getConfigManager().getInt("warps.max-per-faction", 1);
        sender.sendMessage("§6§l━━━ Warps §7(" + warpNames.size() + "/" + maxWarps + ") §6§l━━━");
        
        for (String name : warpNames) {
            Location loc = faction.getWarp(name);
            sender.sendMessage("§e• " + name + " §7- " + 
                loc.getWorld().getName() + " (" + 
                loc.getBlockX() + ", " + 
                loc.getBlockY() + ", " + 
                loc.getBlockZ() + ")");
        }
        
        sender.sendMessage("§7Utilise §e/f warp <nom> §7pour te téléporter.");
    }
    
    private void teleportToWarp(CommandSender sender, Player player, Faction faction, String warpName) {
        Location warp = faction.getWarp(warpName);
        
        if (warp == null) {
            sendMessage(sender, "warp.not-found", "{name}", warpName);
            return;
        }
        
        // TODO: Add combat tag check when CombatTagPlus hook is implemented
        
        // Téléporter
        player.teleport(warp);
        plugin.getLogManager().log(faction.getId(), LogType.TP_WARP, player, null,
            warpName + " [" + warp.getBlockX() + ", " + warp.getBlockY() + ", " + warp.getBlockZ() + "]");
        sendMessage(sender, "warp.teleported", "{name}", warpName);
    }
    
    private void deleteWarp(CommandSender sender, Player player, Faction faction, String warpName) {
        // Vérifier permission (même que setwarp)
        if (!faction.hasPermission(player.getUniqueId(), me.krunsh.kfaction.data.PermissionAction.SETHOME)) {
            sendMessage(sender, "general.no-permission");
            return;
        }
        
        if (!faction.hasWarp(warpName)) {
            sendMessage(sender, "warp.not-found", "{name}", warpName);
            return;
        }
        
        faction.removeWarp(warpName);
        plugin.getStorageManager().markDirty(faction);
        plugin.getLogManager().log(faction.getId(), LogType.TERRITORY_DELWARP, player, null, warpName);
        
        sendMessage(sender, "warp.deleted", "{name}", warpName);
    }
    
    @Override public String getName() { return "warp"; }
    @Override public String getDescription() { return "Se téléporter à un warp"; }
    @Override public String getUsage() { return "<nom> | list | delete <nom>"; }
}
