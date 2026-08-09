package me.krunsh.kfaction.commands;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;

/**
 * Commande /f coords - Partage ses coordonnées dans le chat faction
 */
public class CoordsCommand extends SubCommand {
    
    public CoordsCommand(Kfaction plugin) {
        super(plugin);
    }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        
        if (!fPlayer.hasFaction()) {
            sendMessage(sender, "coords.not-in-faction");
            return;
        }
        
        Faction faction = plugin.getFactionManager().getFaction(fPlayer.getFactionId());
        if (faction == null) {
            sendMessage(sender, "general.error");
            return;
        }
        
        Location loc = player.getLocation();
        String world = loc.getWorld().getName();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        
        // Message formaté pour le chat faction
        String message = plugin.getMessageManager().get("coords.broadcast",
            "{player}", player.getName(),
            "{world}", world,
            "{x}", String.valueOf(x),
            "{y}", String.valueOf(y),
            "{z}", String.valueOf(z));
        
        // Envoyer à tous les membres en ligne
        faction.broadcast(message);
        
        // Confirmer à l'envoyeur
        sendMessage(sender, "coords.sent");
    }
    
    @Override public String getName() { return "coords"; }
    @Override public String getDescription() { return "Partager vos coordonnées avec la faction"; }
    @Override public String getUsage() { return ""; }
}
