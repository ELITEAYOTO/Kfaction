package me.krunsh.kfaction.commands;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionLog;
import me.krunsh.kfaction.data.PermissionAction;

/** Commande autonome /f logs [filtre], sans dépendance vers un moteur de GUI. */
public class LogsCommand extends SubCommand {

    private static final int CHAT_LIMIT = 12;

    public LogsCommand(Kfaction plugin) {
        super(plugin);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        if (player == null) return;

        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        if (fPlayer == null || !fPlayer.hasFaction()) {
            sendMessage(sender, "logs.not-in-faction");
            return;
        }
        Faction faction = plugin.getFactionManager().getFaction(fPlayer.getFactionId());
        if (faction == null) {
            sendMessage(sender, "general.error");
            return;
        }
        if (!faction.hasPermission(player.getUniqueId(), PermissionAction.VIEW_LOGS)) {
            sendMessage(sender, "general.no-permission");
            return;
        }

        String filter = args.length == 0 ? "all" : args[0].toLowerCase();
        List<FactionLog> logs = logs(faction.getId(), filter);
        player.sendMessage("§6§l━━ Historique faction §8(" + filter + ") §6§l━━");
        if (logs.isEmpty()) {
            player.sendMessage("§7Aucun log pour ce filtre.");
            return;
        }
        int displayed = Math.min(CHAT_LIMIT, logs.size());
        for (int index = 0; index < displayed; index++) {
            FactionLog log = logs.get(index);
            player.sendMessage("§8" + log.formatTime() + " §7• " + log.format());
        }
        if (logs.size() > displayed) {
            player.sendMessage("§7... et §e" + (logs.size() - displayed)
                    + " §7autres entrées. Le pack Kgui permet de les paginer.");
        }
    }

    private List<FactionLog> logs(String factionId, String filter) {
        switch (filter) {
            case "members": case "membres": case "m":
                return plugin.getLogManager().getLogsByCategory(factionId, "members");
            case "territory": case "territoire": case "t":
                return plugin.getLogManager().getLogsByCategory(factionId, "territory");
            case "economy": case "eco": case "e":
                return plugin.getLogManager().getLogsByCategory(factionId, "economy");
            case "tp": case "teleport":
                return plugin.getLogManager().getLogsByCategory(factionId, "tp");
            default:
                return plugin.getLogManager().getLogs(factionId);
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length != 1) return Collections.emptyList();
        final String prefix = args[0].toLowerCase();
        return Arrays.asList("all", "members", "territory", "economy", "tp").stream()
                .filter(value -> value.startsWith(prefix)).collect(Collectors.toList());
    }

    @Override public String getName() { return "logs"; }
    @Override public String getDescription() { return "Voir les logs de faction"; }
    @Override public String getUsage() { return "[filtre]"; }
}
