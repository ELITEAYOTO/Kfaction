package me.krunsh.kfaction.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionRole;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.PermissionAction;

/**
 * Commande /f perms [role] - Ouvre le GUI des permissions de faction
 * /f permissions est un alias
 * 
 * Rôles disponibles: recruit, member, moderator, coleader
 * Sans argument: ouvre le menu principal de sélection de rôle
 * Avec rôle: ouvre directement le menu de permissions de ce rôle
 * 
 * Action toggle: /f perm toggle <role> <permission>
 */
public class PermsCommand extends SubCommand {
    
    private static final List<String> VALID_ROLES = Arrays.asList(
        "recruit", "recrue", "r",
        "member", "membre", "m",
        "moderator", "moderateur", "mod",
        "coleader", "co", "cl"
    );
    
    public PermsCommand(Kfaction plugin) {
        super(plugin);
    }
    
    @Override
    public String getName() {
        return "perms";
    }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        if (player == null) return;
        
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        
        if (!fPlayer.hasFaction()) {
            sendMessage(sender, "perms.not-in-faction");
            return;
        }
        
        Faction faction = plugin.getFactionManager().getFaction(fPlayer.getFactionId());
        if (faction == null) {
            sendMessage(sender, "general.error");
            return;
        }
        
        // Seuls Leader et CoLeader peuvent gérer les permissions
        if (fPlayer.getRole() != FactionRole.LEADER && fPlayer.getRole() != FactionRole.COLEADER) {
            sendMessage(sender, "perms.no-permission");
            return;
        }
        
        // Vérifier que Kgui est disponible
        if (!plugin.getHookManager().hasKgui() || !plugin.getHookManager().getKguiHook().isAvailable()) {
            player.sendMessage("§c✖ Le système de permissions n'est pas disponible (Kgui non chargé).");
            return;
        }
        
        // Vérifier si c'est une action toggle
        if (args.length >= 3 && args[0].equalsIgnoreCase("toggle")) {
            handleToggle(player, faction, args);
            return;
        }
        
        // Déterminer le menu à ouvrir
        String menuId = "faction_permissions";
        
        if (args.length > 0) {
            String roleArg = args[0].toLowerCase();
            FactionRole role = parseRole(roleArg);
            
            if (role != null && role != FactionRole.LEADER) {
                menuId = "faction_perms_" + role.name().toLowerCase();
            }
        }
        
        // Ouvrir le menu
        boolean opened = plugin.getHookManager().getKguiHook().openMenu(player, menuId);
        
        if (!opened) {
            player.sendMessage("§c✖ Impossible d'ouvrir le menu des permissions.");
        }
    }
    
    /**
     * Gère le toggle d'une permission
     * Format: /f perm toggle <role|relation> <permission>
     */
    private void handleToggle(Player player, Faction faction, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§c✖ Usage: /f perm toggle <role|relation> <permission>");
            return;
        }
        
        String roleArg = args[1].toUpperCase();
        String permArg = args[2].toLowerCase();
        
        // Vérifier la permission
        PermissionAction action = PermissionAction.fromConfigKey(permArg);
        if (action == null) {
            player.sendMessage("§c✖ Permission invalide: " + permArg);
            return;
        }
        
        // Essayer de parser comme FactionRole d'abord
        FactionRole role = parseRole(roleArg.toLowerCase());
        if (role != null) {
            // On ne peut pas modifier les permissions du leader
            if (role == FactionRole.LEADER) {
                player.sendMessage("§c✖ Les permissions du Leader ne peuvent pas être modifiées.");
                return;
            }
            
            // Toggle la permission pour le rôle
            boolean currentState = faction.hasPermission(role, action);
            faction.setPermission(role, action, !currentState);
            
            // Message de confirmation
            String state = !currentState ? "§a✔ Activée" : "§c✖ Désactivée";
            player.sendMessage("§7Permission §f" + action.getDisplayName() + " §7pour §f" + role.getDisplayName() + " §7: " + state);
            
            // Rafraîchir le menu si ouvert
            plugin.getHookManager().getKguiHook().refreshMenu(player);
            return;
        }
        
        // Sinon, essayer de parser comme Relation
        me.krunsh.kfaction.data.Relation relation = parseRelation(roleArg);
        if (relation != null) {
            // Toggle la permission pour la relation
            boolean currentState = faction.hasPermission(relation, action);
            faction.setPermission(relation, action, !currentState);
            
            // Message de confirmation
            String state = !currentState ? "§a✔ Activée" : "§c✖ Désactivée";
            player.sendMessage("§7Permission §f" + action.getDisplayName() + " §7pour §f" + relation.getDisplayName() + " §7: " + state);
            
            // Rafraîchir le menu si ouvert
            plugin.getHookManager().getKguiHook().refreshMenu(player);
            return;
        }
        
        player.sendMessage("§c✖ Rôle ou relation invalide: " + roleArg);
    }
    
    /**
     * Parse un argument en Relation
     */
    private me.krunsh.kfaction.data.Relation parseRelation(String arg) {
        try {
            return me.krunsh.kfaction.data.Relation.valueOf(arg.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    /**
     * Parse un argument de rôle
     */
    private FactionRole parseRole(String arg) {
        switch (arg.toLowerCase()) {
            case "recruit":
            case "recrue":
            case "r":
                return FactionRole.RECRUIT;
            case "member":
            case "membre":
            case "m":
                return FactionRole.MEMBER;
            case "moderator":
            case "moderateur":
            case "mod":
                return FactionRole.MODERATOR;
            case "coleader":
            case "co":
            case "cl":
                return FactionRole.COLEADER;
            case "leader":
            case "l":
                return FactionRole.LEADER;
            default:
                return null;
        }
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }
        
        Player player = (Player) sender;
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        
        if (fPlayer == null || !fPlayer.hasFaction()) {
            return Collections.emptyList();
        }
        
        // Seuls Leader et CoLeader peuvent voir les suggestions
        if (fPlayer.getRole() != FactionRole.LEADER && fPlayer.getRole() != FactionRole.COLEADER) {
            return Collections.emptyList();
        }
        
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>(Arrays.asList(
                "recruit", "member", "moderator", "coleader", "toggle"
            ));
            String prefix = args[0].toLowerCase();
            return suggestions.stream()
                .filter(s -> s.startsWith(prefix))
                .collect(Collectors.toList());
        }
        
        if (args.length == 2 && args[0].equalsIgnoreCase("toggle")) {
            List<String> roles = Arrays.asList("RECRUIT", "MEMBER", "MODERATOR", "COLEADER");
            String prefix = args[1].toUpperCase();
            return roles.stream()
                .filter(r -> r.startsWith(prefix))
                .collect(Collectors.toList());
        }
        
        if (args.length == 3 && args[0].equalsIgnoreCase("toggle")) {
            // Liste des permissions
            String prefix = args[2].toLowerCase();
            return Arrays.stream(PermissionAction.values())
                .filter(PermissionAction::isDisplayable)
                .map(PermissionAction::getConfigKey)
                .filter(k -> k.startsWith(prefix))
                .collect(Collectors.toList());
        }
        
        return Collections.emptyList();
    }
    
    @Override
    public String getUsage() {
        return "/f perms [role]";
    }
    
    @Override
    public String getDescription() {
        return "Gérer les permissions des rôles";
    }
}
