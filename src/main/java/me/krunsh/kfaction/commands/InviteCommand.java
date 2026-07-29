package me.krunsh.kfaction.commands;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.PermissionAction;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class InviteCommand extends SubCommand {
    public InviteCommand(Kfaction plugin) { super(plugin); }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        
        if (!fPlayer.hasFaction()) {
            sendMessage(sender, "invite.not-in-faction");
            return;
        }
        
        if (args.length < 1) {
            sendMessage(sender, "invite.usage");
            return;
        }
        
        Faction faction = plugin.getFactionManager().getFaction(fPlayer.getFactionId());
        if (faction == null) {
            sendMessage(sender, "general.error");
            return;
        }
        
        if (!faction.hasPermission(player.getUniqueId(), PermissionAction.INVITE)) {
            sendMessage(sender, "general.no-permission");
            return;
        }
        
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sendMessage(sender, "invite.player-not-found");
            return;
        }
        
        FPlayer targetFPlayer = plugin.getFPlayerManager().getFPlayer(target);
        if (targetFPlayer.hasFaction()) {
            sendMessage(sender, "invite.already-in-faction");
            return;
        }
        
        faction.addInvite(target.getUniqueId());
        sendMessage(sender, "invite.success", "{player}", target.getName());
        plugin.getMessageManager().send(target, "invite.received", "{faction}", faction.getName());
    }
    
    @Override public String getName() { return "invite"; }
    @Override public String getDescription() { return "Inviter un joueur"; }
    @Override public String getUsage() { return "<joueur>"; }
}
