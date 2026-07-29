package me.krunsh.kfaction.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.listeners.FlyListener;

/**
 * /f fly - Toggle le fly de faction en territoire
 */
public class FlyCommand extends SubCommand {
    
    public FlyCommand(Kfaction plugin) {
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
        
        FlyListener flyListener = plugin.getFlyListener();
        if (flyListener == null) {
            player.sendMessage("§cLe système de fly n'est pas disponible.");
            return;
        }
        
        flyListener.toggleFly(player);
    }
    
    @Override
    public String getName() { return "fly"; }
    
    @Override
    public String getDescription() { return "Toggle le fly en territoire"; }
    
    @Override
    public String getUsage() { return ""; }
}
