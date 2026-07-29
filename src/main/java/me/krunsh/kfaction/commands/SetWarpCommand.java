package me.krunsh.kfaction.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FactionLog.LogType;
import me.krunsh.kfaction.data.FLocation;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.PermissionAction;

import org.bukkit.Location;

/**
 * Commande /f setwarp <nom> [password] - Créer un warp de faction
 */
public class SetWarpCommand extends SubCommand {
    
    public SetWarpCommand(Kfaction plugin) {
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
        
        // Vérifier permission
        if (!faction.hasPermission(player.getUniqueId(), PermissionAction.SETHOME)) {
            sendMessage(sender, "general.no-permission");
            return;
        }
        
        // Vérifier le nom du warp
        if (args.length < 1) {
            sendMessage(sender, "warp.usage-setwarp");
            return;
        }
        
        String warpName = args[0].toLowerCase();
        
        // Valider le nom
        if (warpName.length() > 16) {
            sendMessage(sender, "warp.name-too-long");
            return;
        }
        
        if (!warpName.matches("^[a-zA-Z0-9_-]+$")) {
            sendMessage(sender, "warp.invalid-name");
            return;
        }
        
        // Vérifier la limite de warps
        int maxWarps = plugin.getConfigManager().getInt("warps.max-per-faction", 1);
        if (!faction.hasWarp(warpName) && faction.getWarpCount() >= maxWarps) {
            sendMessage(sender, "warp.limit-reached", "{max}", String.valueOf(maxWarps));
            return;
        }
        
        // Vérifier qu'on est dans le territoire de la faction
        FLocation loc = new FLocation(player.getLocation());
        Faction owner = plugin.getClaimManager().getFactionAt(loc);
        if (owner == null || !faction.getId().equals(owner.getId())) {
            sendMessage(sender, "warp.must-be-in-territory");
            return;
        }
        
        // Créer/mettre à jour le warp
        boolean isUpdate = faction.hasWarp(warpName);
        Location warpLoc = player.getLocation();
        faction.setWarp(warpName, warpLoc);
        plugin.getStorageManager().markDirty(faction);
        plugin.getLogManager().log(faction.getId(), LogType.TERRITORY_SETWARP, player, null,
            warpName + " [" + warpLoc.getBlockX() + ", " + warpLoc.getBlockY() + ", " + warpLoc.getBlockZ() + "]");
        
        if (isUpdate) {
            sendMessage(sender, "warp.updated", "{name}", warpName);
        } else {
            sendMessage(sender, "warp.created", "{name}", warpName);
        }
        
        // Broadcast à la faction
        for (Player member : faction.getOnlinePlayers()) {
            if (!member.equals(player)) {
                plugin.getMessageManager().send(member, "warp.created-broadcast",
                    "{player}", player.getName(),
                    "{name}", warpName);
            }
        }
    }
    
    @Override public String getName() { return "setwarp"; }
    @Override public String getDescription() { return "Créer un warp de faction"; }
    @Override public String getUsage() { return "<nom>"; }
}
