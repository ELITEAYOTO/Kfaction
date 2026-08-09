package me.krunsh.kfaction.commands;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import org.bukkit.command.CommandSender;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.Faction;

/**
 * Commande /f list - Affiche le top 5 des factions par membres en ligne
 */
public class ListCommand extends SubCommand {
    public ListCommand(Kfaction plugin) { super(plugin); }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        Collection<Faction> allFactions = plugin.getFactionManager().getPlayerFactions();
        
        // Filtrer les factions système et trier par membres en ligne
        List<Faction> factions = new ArrayList<>();
        for (Faction faction : allFactions) {
            if (!faction.isSystemFaction() && faction.getOnlinePlayers().size() > 0) {
                factions.add(faction);
            }
        }
        
        // Trier par nombre de joueurs en ligne (décroissant)
        factions.sort(Comparator.comparingInt((Faction f) -> f.getOnlinePlayers().size()).reversed());
        
        // Limiter à 5
        int limit = Math.min(5, factions.size());
        
        // Header
        sendMessage(sender, "list.header-online");
        
        if (limit == 0) {
            sendMessage(sender, "list.no-factions-online");
            return;
        }
        
        // Afficher le top 5
        for (int i = 0; i < limit; i++) {
            Faction faction = factions.get(i);
            int online = faction.getOnlinePlayers().size();
            int total = faction.getMemberCount();
            
            sendMessage(sender, "list.entry-online",
                "{rank}", String.valueOf(i + 1),
                "{name}", faction.getName(),
                "{online}", String.valueOf(online),
                "{max}", String.valueOf(total));
        }
    }
    
    @Override
    public boolean isPlayerOnly() {
        return false;
    }
    
    @Override public String getName() { return "list"; }
    @Override public String getDescription() { return "Top 5 factions par joueurs en ligne"; }
    @Override public String getUsage() { return ""; }
}
