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

/**
 * Routeur principal /f.
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

    private void registerSubCommands() {
        register(new HelpCommand(plugin), "help", "?", "aide");
        register(new CreateCommand(plugin), "create", "creer", "new");
        register(new DisbandCommand(plugin), "disband", "dissoudre", "delete");
        register(new JoinCommand(plugin), "join", "rejoindre");
        register(new LeaveCommand(plugin), "leave", "quitter");
        register(new InviteCommand(plugin), "invite", "inviter");
        register(new KickCommand(plugin), "kick", "exclure");
        register(new ShowCommand(plugin), "show", "info", "who", "f");
        register(new RenameCommand(plugin), "rename", "renommer");

        register(new ClaimCommand(plugin), "claim");
        register(new UnclaimCommand(plugin), "unclaim");
        register(new AutoClaimCommand(plugin), "autoclaim", "ac");
        register(new AutoCommand(plugin), "auto");
        register(new UnclaimAllCommand(plugin), "unclaimall", "unclaima");
        register(new MapCommand(plugin), "map", "carte");
        register(new HomeCommand(plugin), "home", "hq");
        register(new SetHomeCommand(plugin), "sethome", "sethq");
        register(new WarpCommand(plugin), "warp", "w");
        register(new SetWarpCommand(plugin), "setwarp", "sw");

        register(new PromoteCommand(plugin), "promote", "promouvoir");
        register(new DemoteCommand(plugin), "demote", "retrograder");
        register(new LeaderCommand(plugin), "leader", "chef");

        // OFFICER est désormais distinct de MODERATOR.
        register(new ModCommand(plugin), "mod", "moderator");
        register(new ColeaderCommand(plugin), "coleader", "colead", "souschef");

        register(new AllyCommand(plugin), "ally", "allier");
        register(new EnemyCommand(plugin), "enemy", "ennemi");
        register(new NeutralCommand(plugin), "neutral", "neutre");
        register(new TruceCommand(plugin), "truce", "treve");

        register(new ChatCommand(plugin), "chat", "c");
        register(new CoordsCommand(plugin), "coords", "coord", "pos");

        register(new PowerCommand(plugin), "power", "p", "pow");

        register(new DepositCommand(plugin), "deposit", "dep");
        register(new WithdrawCommand(plugin), "withdraw", "wit");
        register(new BankCommand(plugin), "bank", "banque", "solde");
        register(new TntCommand(plugin), "tnt", "tntbank");

        register(new SpyCommand(plugin), "spy");
        register(new UnspyCommand(plugin), "unspy");

        register(new ListCommand(plugin), "list", "liste");
        register(new TopCommand(plugin), "top", "ftop");
        register(new MenuCommand(plugin), "menu", "gui");
        register(new LogsCommand(plugin), "logs", "log", "historique");
        register(new PermsCommand(plugin), "perms", "permissions", "perm");

        register(new LevelCommand(plugin), "level", "lvl", "niveau");
        register(new QuestCommand(plugin), "quest", "quete", "quests");
        register(new ChestCommand(plugin), "chest", "coffre", "fchest");
        register(new FlyCommand(plugin), "fly", "vol");
    }

    private void register(SubCommand command, String name, String... commandAliases) {
        String canonical = name.toLowerCase();
        subCommands.put(canonical, command);

        for (String alias : commandAliases) {
            aliases.put(alias.toLowerCase(), canonical);
        }
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (args.length == 0) {
            // /f reste une entrée stable vers l'aide. /f f affiche sa faction.
            getSubCommand("help").execute(sender, new String[0]);
            return true;
        }

        String subName = args[0].toLowerCase();
        SubCommand sub = getSubCommand(subName);

        if (sub == null) {
            if (sender instanceof Player) {
                plugin.getMessageManager().send(
                        (Player) sender,
                        "general.unknown-command",
                        "{command}", subName
                );
            } else {
                sender.sendMessage("§cCommande inconnue: " + subName);
            }
            return true;
        }

        if (sub.isPlayerOnly() && !(sender instanceof Player)) {
            sender.sendMessage("§cCette commande ne peut être exécutée que par un joueur.");
            return true;
        }

        if (sub.getPermission() != null && !sender.hasPermission(sub.getPermission())) {
            if (sender instanceof Player) {
                plugin.getMessageManager().send(
                        (Player) sender,
                        "general.no-permission"
                );
            } else {
                sender.sendMessage("§cVous n'avez pas la permission.");
            }
            return true;
        }

        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        sub.execute(sender, subArgs);
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();

            for (String name : subCommands.keySet()) {
                if (!name.startsWith(partial)) {
                    continue;
                }

                SubCommand sub = subCommands.get(name);
                if (sub.getPermission() == null
                        || sender.hasPermission(sub.getPermission())) {
                    completions.add(name);
                }
            }

            return completions;
        }

        if (args.length > 1) {
            SubCommand sub = getSubCommand(args[0].toLowerCase());
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

    private SubCommand getSubCommand(String name) {
        String key = name.toLowerCase();

        SubCommand direct = subCommands.get(key);
        if (direct != null) {
            return direct;
        }

        String canonical = aliases.get(key);
        return canonical != null ? subCommands.get(canonical) : null;
    }
}
