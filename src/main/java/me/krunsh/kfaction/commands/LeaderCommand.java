package me.krunsh.kfaction.commands;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;

public class LeaderCommand extends SubCommand {
    public LeaderCommand(Kfaction plugin) { super(plugin); }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        
        if (!fPlayer.hasFaction()) {
            sendMessage(sender, "leader.not-in-faction");
            return;
        }
        
        if (args.length < 1) {
            sendMessage(sender, "leader.usage");
            return;
        }
        
        Faction faction = plugin.getFactionManager().getFaction(fPlayer.getFactionId());
        if (faction == null) {
            sendMessage(sender, "general.error");
            return;
        }
        
        if (!faction.isLeader(player.getUniqueId())) {
            sendMessage(sender, "leader.not-leader");
            return;
        }
        
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        UUID targetId = target.getUniqueId();
        
        if (!faction.isMember(targetId)) {
            sendMessage(sender, "leader.not-member");
            return;
        }
        
        if (targetId.equals(player.getUniqueId())) {
            sendMessage(sender, "leader.already-leader");
            return;
        }
        
        faction.setLeader(targetId);
        sendMessage(sender, "leader.success", "{player}", target.getName());
    }
    
    @Override public String getName() { return "leader"; }
    @Override public String getDescription() { return "Transférer le leadership"; }
    @Override public String getUsage() { return "<joueur>"; }
}
