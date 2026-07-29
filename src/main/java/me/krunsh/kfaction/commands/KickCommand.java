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

public class KickCommand extends SubCommand {
    public KickCommand(Kfaction plugin) { super(plugin); }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        
        if (!fPlayer.hasFaction()) {
            sendMessage(sender, "kick.not-in-faction");
            return;
        }
        
        if (args.length < 1) {
            sendMessage(sender, "kick.usage");
            return;
        }
        
        Faction faction = plugin.getFactionManager().getFaction(fPlayer.getFactionId());
        if (faction == null) {
            sendMessage(sender, "general.error");
            return;
        }
        
        if (!faction.hasPermission(player.getUniqueId(), PermissionAction.KICK)) {
            sendMessage(sender, "general.no-permission");
            return;
        }
        
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        UUID targetId = target.getUniqueId();
        
        if (!faction.isMember(targetId)) {
            sendMessage(sender, "kick.not-member");
            return;
        }
        
        if (faction.isLeader(targetId)) {
            sendMessage(sender, "kick.cannot-kick-leader");
            return;
        }
        
        faction.removeMember(targetId);
        FPlayer targetFPlayer = plugin.getFPlayerManager().getFPlayer(targetId);
        String oldFactionId = targetFPlayer.getFactionId();
        targetFPlayer.setFactionId(null);
        plugin.getFPlayerManager().notifyFactionChange(targetId, oldFactionId, null);
        
        sendMessage(sender, "kick.success", "{player}", target.getName());
        
        // Log de l'action
        plugin.getLogManager().log(faction.getId(), LogType.MEMBER_KICK, 
            player.getUniqueId(), player.getName(),
            targetId, target.getName(), null);
        
        if (target.isOnline()) {
            plugin.getMessageManager().send((Player) target, "kick.kicked", "{faction}", faction.getName());
        }
    }
    
    @Override public String getName() { return "kick"; }
    @Override public String getDescription() { return "Expulser un membre"; }
    @Override public String getUsage() { return "<joueur>"; }
}
