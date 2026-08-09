package me.krunsh.kfaction.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.managers.FactionChestManager;

/**
 * /f chest - Ouvrir le coffre de faction
 */
public class ChestCommand extends SubCommand {
    
    public ChestCommand(Kfaction plugin) {
        super(plugin);
    }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayerOrWarn(sender);
        if (player == null) return;
        
        Faction faction = plugin.getFactionManager().getPlayerFaction(player);
        if (faction == null) {
            player.sendMessage("§cVous n'êtes pas dans une faction.");
            return;
        }
        
        FactionChestManager chestManager = plugin.getFactionChestManager();
        if (chestManager == null) {
            player.sendMessage("§cLe système de coffre n'est pas disponible.");
            return;
        }
        
        chestManager.openChest(player, faction);
    }
    
    @Override
    public String getName() { return "chest"; }
    
    @Override
    public String getDescription() { return "Ouvrir le coffre de faction"; }
    
    @Override
    public String getUsage() { return ""; }
}
