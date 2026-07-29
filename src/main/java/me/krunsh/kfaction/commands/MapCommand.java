package me.krunsh.kfaction.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FLocation;
import me.krunsh.kfaction.data.FPlayer;

/**
 * Commande /f map [on|off] - Affiche la carte des factions
 */
public class MapCommand extends SubCommand {
    
    public MapCommand(Kfaction plugin) {
        super(plugin);
    }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        
        // Vérifier si on toggle l'auto-map
        if (args.length > 0) {
            String arg = args[0].toLowerCase();
            if (arg.equals("on") || arg.equals("auto")) {
                fPlayer.setMapAutoUpdateEnabled(true);
                sendMessage(sender, "map.auto-enabled");
                // Afficher la carte immédiatement
                plugin.getMapManager().showMap(player);
                return;
            } else if (arg.equals("off")) {
                fPlayer.setMapAutoUpdateEnabled(false);
                sendMessage(sender, "map.auto-disabled");
                return;
            }
        }
        
        // Affichage unique de la carte
        FLocation center = new FLocation(player.getLocation());
        plugin.getMapManager().showMap(player, center);
    }
    
    @Override public String getName() { return "map"; }
    @Override public String getDescription() { return "Afficher la carte des factions"; }
    @Override public String getUsage() { return "[on|off]"; }
}
