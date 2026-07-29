package me.krunsh.kfaction.commands;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FPlayer;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PowerCommand extends SubCommand {
    public PowerCommand(Kfaction plugin) { super(plugin); }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        
        if (args.length > 0) {
            // Afficher le power d'un autre joueur
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            FPlayer targetFPlayer = plugin.getFPlayerManager().getFPlayer(target.getUniqueId());
            
            sendMessage(sender, "power.other",
                "{player}", target.getName(),
                "{power}", String.valueOf(plugin.getPowerManager().getPlayerPower(target.getUniqueId())),
                "{maxpower}", String.valueOf(plugin.getPowerManager().getPlayerMaxPower(target.getUniqueId())));
        } else {
            // Afficher son propre power
            FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
            
            sendMessage(sender, "power.self",
                "{power}", String.valueOf(plugin.getPowerManager().getPlayerPower(player.getUniqueId())),
                "{maxpower}", String.valueOf(plugin.getPowerManager().getPlayerMaxPower(player.getUniqueId())));
            
            if (fPlayer.hasFaction()) {
                Faction faction = plugin.getFactionManager().getFaction(fPlayer.getFactionId());
                if (faction != null) {
                    sendMessage(sender, "power.faction",
                        "{power}", String.valueOf(plugin.getPowerManager().getFactionPower(faction)),
                        "{maxpower}", String.valueOf(plugin.getPowerManager().getFactionMaxPower(faction)),
                        "{claims}", String.valueOf(faction.getClaimCount()));
                }
            }
        }
    }
    
    @Override public String getName() { return "power"; }
    @Override public String getDescription() { return "Afficher les informations de power"; }
    @Override public String getUsage() { return "[joueur]"; }
}
