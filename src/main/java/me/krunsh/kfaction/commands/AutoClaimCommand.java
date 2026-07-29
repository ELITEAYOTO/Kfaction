package me.krunsh.kfaction.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FPlayer;

/**
 * Commande /f autoclaim - Active/désactive l'auto-claim
 */
public class AutoClaimCommand extends SubCommand {
    
    public AutoClaimCommand(Kfaction plugin) {
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
        
        // Vérifier permission
        me.krunsh.kfaction.data.Faction faction = plugin.getFactionManager().getPlayerFaction(player);
        if (!faction.hasPermission(player.getUniqueId(), me.krunsh.kfaction.data.PermissionAction.CLAIM)) {
            sendMessage(sender, "general.no-permission");
            return;
        }
        
        boolean newState = !fPlayer.isAutoClaimEnabled();
        fPlayer.setAutoClaimEnabled(newState);
        
        if (newState) {
            sendMessage(sender, "autoclaim.enabled");
            // Claim immédiatement le chunk actuel
            me.krunsh.kfaction.data.FLocation loc = new me.krunsh.kfaction.data.FLocation(player.getLocation());
            plugin.getClaimManager().claim(faction, loc);
        } else {
            sendMessage(sender, "autoclaim.disabled");
        }
    }
    
    @Override public String getName() { return "autoclaim"; }
    @Override public String getDescription() { return "Active/désactive l'auto-claim"; }
    @Override public String getUsage() { return ""; }
}
