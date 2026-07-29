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

public class PromoteCommand extends SubCommand {
    public PromoteCommand(Kfaction plugin) { super(plugin); }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        
        if (!fPlayer.hasFaction()) {
            sendMessage(sender, "promote.not-in-faction");
            return;
        }
        
        if (args.length < 1) {
            sendMessage(sender, "promote.usage");
            return;
        }
        
        Faction faction = plugin.getFactionManager().getFaction(fPlayer.getFactionId());
        if (faction == null) {
            sendMessage(sender, "general.error");
            return;
        }
        
        if (!faction.hasPermission(player.getUniqueId(), PermissionAction.PROMOTE)) {
            sendMessage(sender, "general.no-permission");
            return;
        }
        
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        UUID targetId = target.getUniqueId();
        
        if (!faction.isMember(targetId)) {
            sendMessage(sender, "promote.not-member");
            return;
        }
        
        boolean promoted = faction.promote(targetId);
        if (promoted) {
            plugin.getLogManager().log(faction.getId(), LogType.MEMBER_PROMOTE, 
                player.getUniqueId(), player.getName(), targetId, target.getName(), null);
            sendMessage(sender, "promote.success", "{player}", target.getName());
        } else {
            sendMessage(sender, "promote.max-rank");
        }
    }
    
    @Override public String getName() { return "promote"; }
    @Override public String getDescription() { return "Promouvoir un membre"; }
    @Override public String getUsage() { return "<joueur>"; }
}
