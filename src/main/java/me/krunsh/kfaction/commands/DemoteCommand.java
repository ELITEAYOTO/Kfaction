package me.krunsh.kfaction.commands;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionLog.LogType;
import me.krunsh.kfaction.data.PermissionAction;

public class DemoteCommand extends SubCommand {
    public DemoteCommand(Kfaction plugin) { super(plugin); }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        
        if (!fPlayer.hasFaction()) {
            sendMessage(sender, "demote.not-in-faction");
            return;
        }
        
        if (args.length < 1) {
            sendMessage(sender, "demote.usage");
            return;
        }
        
        Faction faction = plugin.getFactionManager().getFaction(fPlayer.getFactionId());
        if (faction == null) {
            sendMessage(sender, "general.error");
            return;
        }
        
        if (!faction.hasPermission(player.getUniqueId(), PermissionAction.DEMOTE)) {
            sendMessage(sender, "general.no-permission");
            return;
        }
        
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        UUID targetId = target.getUniqueId();
        
        if (!faction.isMember(targetId)) {
            sendMessage(sender, "demote.not-member");
            return;
        }
        
        boolean demoted = faction.demote(targetId);
        if (demoted) {
            plugin.getLogManager().log(faction.getId(), LogType.MEMBER_DEMOTE, 
                player.getUniqueId(), player.getName(), targetId, target.getName(), null);
            sendMessage(sender, "demote.success", "{player}", target.getName());
        } else {
            sendMessage(sender, "demote.min-rank");
        }
    }
    
    @Override public String getName() { return "demote"; }
    @Override public String getDescription() { return "Rétrograder un membre"; }
    @Override public String getUsage() { return "<joueur>"; }
}
