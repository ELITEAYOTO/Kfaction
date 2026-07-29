package me.krunsh.kfaction.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.PermissionAction;

/**
 * Commande /f unclaimall [confirm] - Libère tous les claims
 */
public class UnclaimAllCommand extends SubCommand {
    
    public UnclaimAllCommand(Kfaction plugin) {
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
        
        // Vérifier permission (nécessite UNCLAIM ou être leader)
        if (!faction.hasPermission(player.getUniqueId(), PermissionAction.UNCLAIM)) {
            sendMessage(sender, "general.no-permission");
            return;
        }
        
        // Demander confirmation
        if (args.length == 0 || !args[0].equalsIgnoreCase("confirm")) {
            int claimCount = faction.getClaimCount();
            sendMessage(sender, "unclaimall.confirm", 
                "{count}", String.valueOf(claimCount));
            return;
        }
        
        // Unclaim tout
        int count = faction.getClaimCount();
        plugin.getClaimManager().unclaimAll(faction);
        
        sendMessage(sender, "unclaimall.success", "{count}", String.valueOf(count));
        
        // Broadcast à la faction
        for (Player member : faction.getOnlinePlayers()) {
            if (!member.equals(player)) {
                plugin.getMessageManager().send(member, "unclaimall.broadcast",
                    "{player}", player.getName(),
                    "{count}", String.valueOf(count));
            }
        }
    }
    
    @Override public String getName() { return "unclaimall"; }
    @Override public String getDescription() { return "Libère tous les claims de la faction"; }
    @Override public String getUsage() { return "[confirm]"; }
}
