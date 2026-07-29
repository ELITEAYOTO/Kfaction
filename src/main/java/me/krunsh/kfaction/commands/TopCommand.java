package me.krunsh.kfaction.commands;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.Faction;

import org.bukkit.command.CommandSender;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class TopCommand extends SubCommand {
    public TopCommand(Kfaction plugin) { super(plugin); }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        String sortBy = "power";
        if (args.length > 0) {
            sortBy = args[0].toLowerCase();
        }
        
        List<Faction> factions;
        
        switch (sortBy) {
            case "members":
                factions = plugin.getFactionManager().getAllFactions().stream()
                    .sorted(Comparator.comparingInt(Faction::getMemberCount).reversed())
                    .limit(10)
                    .collect(Collectors.toList());
                sendMessage(sender, "top.header-members");
                break;
            case "land":
            case "claims":
                factions = plugin.getFactionManager().getAllFactions().stream()
                    .sorted(Comparator.comparingInt(Faction::getClaimCount).reversed())
                    .limit(10)
                    .collect(Collectors.toList());
                sendMessage(sender, "top.header-land");
                break;
            case "balance":
            case "money":
                factions = plugin.getFactionManager().getAllFactions().stream()
                    .sorted(Comparator.comparingDouble(Faction::getBalance).reversed())
                    .limit(10)
                    .collect(Collectors.toList());
                sendMessage(sender, "top.header-balance");
                break;
            default:
                factions = plugin.getFactionManager().getAllFactions().stream()
                    .sorted(Comparator.comparingDouble(Faction::getPower).reversed())
                    .limit(10)
                    .collect(Collectors.toList());
                sendMessage(sender, "top.header-power");
                break;
        }
        
        int rank = 1;
        for (Faction faction : factions) {
            sendMessage(sender, "top.entry",
                "{rank}", String.valueOf(rank++),
                "{name}", faction.getName(),
                "{power}", String.valueOf(faction.getPower()),
                "{members}", String.valueOf(faction.getMemberCount()),
                "{land}", String.valueOf(faction.getClaimCount()),
                "{balance}", String.valueOf(faction.getBalance()));
        }
    }
    
    @Override
    public boolean isPlayerOnly() {
        return false;
    }
    
    @Override public String getName() { return "top"; }
    @Override public String getDescription() { return "Afficher le classement des factions"; }
    @Override public String getUsage() { return "[power|members|land|balance]"; }
}
