package me.krunsh.kfaction.commands;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionLog;
import me.krunsh.kfaction.data.PermissionAction;

/**
 * Commande /f logs [filtre] - Ouvre le GUI des logs de faction
 * Filtres disponibles: all, members, territory, economy, tp
 * 
 * TOUT est géré via GUI, pas d'affichage chat.
 */
public class LogsCommand extends SubCommand {
    
    private static final List<String> VALID_FILTERS = Arrays.asList(
        "all", "tous",
        "members", "membres", "m",
        "territory", "territoire", "t",
        "economy", "eco", "e",
        "tp", "teleport"
    );
    
    public LogsCommand(Kfaction plugin) {
        super(plugin);
    }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        if (player == null) return;
        
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        
        if (!fPlayer.hasFaction()) {
            sendMessage(sender, "logs.not-in-faction");
            return;
        }
        
        Faction faction = plugin.getFactionManager().getFaction(fPlayer.getFactionId());
        if (faction == null) {
            sendMessage(sender, "general.error");
            return;
        }
        
        // Vérifier la permission
        if (!faction.hasPermission(player.getUniqueId(), PermissionAction.VIEW_LOGS)) {
            sendMessage(sender, "general.no-permission");
            return;
        }
        
        // Vérifier que Kgui est disponible
        if (!plugin.getHookManager().hasKgui() || !plugin.getHookManager().getKguiHook().isAvailable()) {
            player.sendMessage("§c✖ Le système de logs n'est pas disponible (Kgui non chargé).");
            return;
        }
        
        // Déterminer le filtre
        String filter = "all";
        if (args.length > 0) {
            filter = args[0].toLowerCase();
        }
        
        // Récupérer les logs selon le filtre
        List<FactionLog> logs;
        String menuId;
        
        switch (filter) {
            case "members":
            case "membres":
            case "m":
                logs = plugin.getLogManager().getLogsByCategory(faction.getId(), "members");
                menuId = "faction_logs_members";
                break;
                
            case "territory":
            case "territoire":
            case "t":
                logs = plugin.getLogManager().getLogsByCategory(faction.getId(), "territory");
                menuId = "faction_logs_territory";
                break;
                
            case "economy":
            case "eco":
            case "e":
                logs = plugin.getLogManager().getLogsByCategory(faction.getId(), "economy");
                menuId = "faction_logs_economy";
                break;
                
            case "tp":
            case "teleport":
                logs = plugin.getLogManager().getLogsByCategory(faction.getId(), "tp");
                menuId = "faction_logs_tp";
                break;
                
            default:
                // all / tous / autre
                logs = plugin.getLogManager().getLogs(faction.getId());
                menuId = "faction_logs";
                break;
        }
        
        // Ouvrir le GUI avec les logs injectés
        // Debug: afficher nombre de logs
        if (plugin.isDebugMode()) {
            plugin.getLogger().info("[DEBUG] LogsCommand: " + logs.size() + " logs for faction " + faction.getId() + " (filter: " + filter + ")");
        }
        
        boolean opened = plugin.getHookManager().getKguiHook().openLogsMenuWithContent(player, logs, menuId);
        
        if (!opened) {
            player.sendMessage("§c✖ Impossible d'ouvrir le menu des logs.");
        }
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return filterStartsWith(args[0], Arrays.asList("all", "members", "territory", "economy", "tp"));
        }
        return Collections.emptyList();
    }
    
    private List<String> filterStartsWith(String input, List<String> options) {
        String lower = input.toLowerCase();
        return options.stream()
            .filter(opt -> opt.toLowerCase().startsWith(lower))
            .collect(java.util.stream.Collectors.toList());
    }
    
    @Override public String getName() { return "logs"; }
    @Override public String getDescription() { return "Voir les logs de faction"; }
    @Override public String getUsage() { return "[filtre]"; }
}
