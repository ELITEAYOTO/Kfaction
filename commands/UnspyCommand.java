package me.krunsh.kfaction.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;

/**
 * Commande /f unspy - Arrête l'espionnage du chat faction (admin)
 * Alias pratique de /f spy sans argument
 */
public class UnspyCommand extends SubCommand {
    
    public UnspyCommand(Kfaction plugin) {
        super(plugin);
    }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        
        // Vérifier permission admin
        if (!player.hasPermission("kfaction.admin.spy")) {
            sendMessage(sender, "general.no-permission");
            return;
        }
        
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        
        if (!fPlayer.isSpyingAnyFaction()) {
            sendMessage(sender, "spy.not-spying");
            return;
        }
        
        String oldFactionId = fPlayer.getSpyingFactionId();
        Faction faction = plugin.getFactionManager().getFaction(oldFactionId);
        String factionName = (faction != null) ? faction.getName() : oldFactionId;
        
        fPlayer.stopSpying();
        sendMessage(sender, "spy.disabled", "{faction}", factionName);
    }
    
    @Override public String getName() { return "unspy"; }
    @Override public String getDescription() { return "Arrêter l'espionnage chat faction (admin)"; }
    @Override public String getUsage() { return ""; }
}
