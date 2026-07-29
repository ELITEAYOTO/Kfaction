package me.krunsh.kfaction.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.Faction;

/**
 * Commande principale /kfaction (alias: /f, /faction, /kf, /fac)
 * Route vers les sous-commandes appropriées
 */
public class KfactionCommand implements CommandExecutor, TabCompleter {
    
    private final Kfaction plugin;
    private final Map<String, SubCommand> subCommands;
    private final Map<String, String> aliases;
    
    public KfactionCommand(Kfaction plugin) {
        this.plugin = plugin;
        this.subCommands = new HashMap<>();
        this.aliases = new HashMap<>();
        
        registerSubCommands();
    }
    
    /**
     * Enregistre toutes les sous-commandes
     */
    private void registerSubCommands() {
        // Commandes de base
        register(new HelpCommand(plugin), "help", "?", "aide");
        register(new CreateCommand(plugin), "create", "creer", "new");
        register(new DisbandCommand(plugin), "disband", "dissoudre", "delete");
        register(new JoinCommand(plugin), "join", "rejoindre");
        register(new LeaveCommand(plugin), "leave", "quitter");
        register(new InviteCommand(plugin), "invite", "inviter");
        register(new KickCommand(plugin), "kick", "exclure");
        register(new ShowCommand(plugin), "show", "info", "who", "f");
        register(new RenameCommand(plugin), "rename", "renommer");
        
        // Gestion territoire
        register(new ClaimCommand(plugin), "claim");
        register(new UnclaimCommand(plugin), "unclaim");
        register(new AutoClaimCommand(plugin), "autoclaim", "ac");
        register(new UnclaimAllCommand(plugin), "unclaimall", "unclaima");
        register(new MapCommand(plugin), "map", "carte");
        register(new HomeCommand(plugin), "home", "hq");
        register(new SetHomeCommand(plugin), "sethome", "sethq");
        register(new WarpCommand(plugin), "warp", "w");
        register(new SetWarpCommand(plugin), "setwarp", "sw");
        
        // Gestion membres
        register(new PromoteCommand(plugin), "promote", "promouvoir");
        register(new DemoteCommand(plugin), "demote", "retrograder");
        register(new LeaderCommand(plugin), "leader", "chef");
        register(new ModCommand(plugin), "mod", "moderator", "officer");
        register(new ColeaderCommand(plugin), "coleader", "colead", "souschef");
        
        // Relations
        register(new AllyCommand(plugin), "ally", "allier");
        register(new EnemyCommand(plugin), "enemy", "ennemi");
        register(new NeutralCommand(plugin), "neutral", "neutre");
        register(new TruceCommand(plugin), "truce", "treve");
        
        // Chat
        register(new ChatCommand(plugin), "chat", "c");
        register(new CoordsCommand(plugin), "coords", "coord", "pos");
        
        // Power
        register(new PowerCommand(plugin), "power", "p", "pow");
        
        // Économie
        register(new DepositCommand(plugin), "deposit", "dep");
        register(new WithdrawCommand(plugin), "withdraw", "wit");
        register(new BankCommand(plugin), "bank", "banque", "solde");
        register(new TntCommand(plugin), "tnt", "tntbank");
        
        // Note: Les commandes admin sont maintenant dans /kfaction (KfactionAdminCommand)
        // Seuls spy/unspy restent accessibles via /f pour les staffs en jeu
        register(new SpyCommand(plugin), "spy");
        register(new UnspyCommand(plugin), "unspy");
        
        // Divers
        register(new ListCommand(plugin), "list", "liste");
        register(new TopCommand(plugin), "top", "ftop");
        register(new MenuCommand(plugin), "menu", "gui");
        register(new LogsCommand(plugin), "logs", "log", "historique");
        register(new PermsCommand(plugin), "perms", "permissions", "perm");
        
        // Système de niveaux
        register(new LevelCommand(plugin), "level", "lvl", "niveau");
        register(new QuestCommand(plugin), "quest", "quete", "quests");
        register(new ChestCommand(plugin), "chest", "coffre", "fchest");
        register(new FlyCommand(plugin), "fly", "vol");
    }
    
    /**
     * Enregistre une sous-commande avec ses alias
     */
    private void register(SubCommand command, String name, String... commandAliases) {
        subCommands.put(name.toLowerCase(), command);
        for (String alias : commandAliases) {
            aliases.put(alias.toLowerCase(), name.toLowerCase());
        }
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Sans arguments = afficher l'aide ou les infos de la faction du joueur
        if (args.length == 0) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                Faction faction = plugin.getFactionManager().getPlayerFaction(player);
                if (faction != null) {
                    // Afficher les infos de sa propre faction
                    getSubCommand("show").execute(sender, new String[]{});
                } else {
                    getSubCommand("help").execute(sender, new String[]{});
                }
            } else {
                getSubCommand("help").execute(sender, new String[]{});
            }
            return true;
        }
        
        // Trouver la sous-commande
        String subName = args[0].toLowerCase();
        SubCommand sub = getSubCommand(subName);
        
        if (sub == null) {
            if (sender instanceof Player) {
                plugin.getMessageManager().send((Player) sender, "general.unknown-command",
                    "{command}", subName);
            } else {
                sender.sendMessage("§cCommande inconnue: " + subName);
            }
            return true;
        }
        
        // Vérifier si c'est joueur only
        if (sub.isPlayerOnly() && !(sender instanceof Player)) {
            sender.sendMessage("§cCette commande ne peut être exécutée que par un joueur.");
            return true;
        }
        
        // Vérifier la permission
        if (sub.getPermission() != null && !sender.hasPermission(sub.getPermission())) {
            if (sender instanceof Player) {
                plugin.getMessageManager().send((Player) sender, "general.no-permission");
            } else {
                sender.sendMessage("§cVous n'avez pas la permission.");
            }
            return true;
        }
        
        // Exécuter la sous-commande
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        sub.execute(sender, subArgs);
        
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // Compléter les noms de sous-commandes
            String partial = args[0].toLowerCase();
            for (String name : subCommands.keySet()) {
                if (name.startsWith(partial)) {
                    SubCommand sub = subCommands.get(name);
                    if (sub.getPermission() == null || sender.hasPermission(sub.getPermission())) {
                        completions.add(name);
                    }
                }
            }
        } else if (args.length > 1) {
            // Déléguer à la sous-commande
            String subName = args[0].toLowerCase();
            SubCommand sub = getSubCommand(subName);
            if (sub != null) {
                String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
                List<String> subCompletions = sub.tabComplete(sender, subArgs);
                if (subCompletions != null) {
                    completions.addAll(subCompletions);
                }
            }
        }
        
        return completions;
    }
    
    /**
     * Obtient une sous-commande par son nom ou alias
     */
    private SubCommand getSubCommand(String name) {
        name = name.toLowerCase();
        
        // Chercher directement
        if (subCommands.containsKey(name)) {
            return subCommands.get(name);
        }
        
        // Chercher via alias
        if (aliases.containsKey(name)) {
            return subCommands.get(aliases.get(name));
        }
        
        return null;
    }
}
