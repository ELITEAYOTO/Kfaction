package me.krunsh.kfaction.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.api.event.FactionDisbandEvent;
import me.krunsh.kfaction.data.Faction;

public class DisbandCommand extends SubCommand {
    public DisbandCommand(Kfaction plugin) { super(plugin); }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        if (player == null) return;
        
        Faction faction = plugin.getFactionManager().getPlayerFaction(player);
        if (faction == null) { sendMessage(sender, "disband.not-in-faction"); return; }
        if (!faction.isLeader(player.getUniqueId())) { sendMessage(sender, "disband.not-leader"); return; }
        
        // Déclencher l'event
        FactionDisbandEvent event = new FactionDisbandEvent(faction, player, FactionDisbandEvent.DisbandReason.COMMAND);
        Bukkit.getPluginManager().callEvent(event);
        
        if (event.isCancelled()) {
            sendMessage(sender, "disband.cancelled");
            return;
        }
        
        String name = faction.getName();
        plugin.getFactionManager().disbandFaction(faction);
        sendMessage(sender, "disband.success", "{name}", name);
    }
    
    @Override public String getName() { return "disband"; }
    @Override public String getDescription() { return "Dissoudre la faction"; }
    @Override public String getUsage() { return ""; }
}
