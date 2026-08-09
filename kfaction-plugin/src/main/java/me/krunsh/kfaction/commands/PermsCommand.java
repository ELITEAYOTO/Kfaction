package me.krunsh.kfaction.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionRole;
import me.krunsh.kfaction.data.PermissionAction;
import me.krunsh.kfaction.data.Relation;
import me.krunsh.kfaction.permissions.FactionCapability;

/**
 * /f perms
 * /f perms toggle <role|relation> <legacy-permission>
 * /f perms reset <role|relation|all>
 *
 * Cette commande reste autonome. Les interfaces graphiques externes utilisent
 * KfactionPlayerActions et ne pilotent jamais cette commande en texte.
 */
public class PermsCommand extends SubCommand {

    public PermsCommand(Kfaction plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "perms";
    }

    @Override
    public void execute(
            CommandSender sender,
            String[] args
    ) {
        Player player = getPlayer(sender);

        if (player == null) {
            return;
        }

        FPlayer fPlayer =
                plugin.getFPlayerManager()
                        .findLoaded(
                                player.getUniqueId()
                        );

        if (fPlayer == null
                || !fPlayer.hasFaction()) {
            sendMessage(
                    sender,
                    "perms.not-in-faction"
            );
            return;
        }

        Faction faction =
                plugin.getFactionManager()
                        .getFaction(
                                fPlayer.getFactionId()
                        );

        if (faction == null) {
            sendMessage(
                    sender,
                    "general.error"
            );
            return;
        }

        FactionRole canonicalRole =
                faction.getRole(
                        player.getUniqueId()
                );

        boolean legacyColeaderCompatibility =
                canonicalRole == FactionRole.COLEADER
                        || canonicalRole == FactionRole.LEADER;

        if (!plugin.getPermissionManager().can(
                player,
                FactionCapability.EDIT_PERMISSIONS
        ) && !legacyColeaderCompatibility) {
            sendMessage(
                    sender,
                    "perms.no-permission"
            );
            return;
        }

        /*
         * Les mutations CLI fonctionnent même sans Kgui.
         */
        if (args.length > 0
                && args[0].equalsIgnoreCase(
                        "toggle"
                )) {
            handleToggle(
                    player,
                    faction,
                    args
            );
            return;
        }

        if (args.length > 0
                && args[0].equalsIgnoreCase(
                        "reset"
                )) {
            handleReset(
                    player,
                    faction,
                    args
            );
            return;
        }

        showHelp(player);
    }

    private void handleToggle(
            Player player,
            Faction faction,
            String[] args
    ) {
        if (args.length < 3) {
            player.sendMessage(
                    "§c✖ Usage: /f perms toggle <role|relation> <permission>"
            );
            return;
        }

        String targetArg =
                args[1];

        PermissionAction action =
                PermissionAction.fromConfigKey(
                        args[2]
                );

        if (action == null) {
            player.sendMessage(
                    "§c✖ Permission legacy invalide: "
                            + args[2]
            );
            return;
        }

        FactionRole role =
                parseRole(targetArg);

        if (role != null) {
            if (role == FactionRole.LEADER) {
                player.sendMessage(
                        "§c✖ Les permissions du Leader ne peuvent pas être modifiées."
                );
                return;
            }

            boolean current =
                    faction.hasPermission(
                            role,
                            action
                    );

            faction.setPermission(
                    role,
                    action,
                    !current
            );

            plugin.getStorageManager()
                    .markDirty(faction);

            player.sendMessage(
                    "§7Permission §f"
                            + action.getDisplayName()
                            + " §7pour §f"
                            + role.getDisplayName()
                            + " §7: "
                            + (!current
                                    ? "§a✔ Activée"
                                    : "§c✖ Désactivée")
            );

            return;
        }

        Relation relation =
                parseRelation(
                        targetArg
                );

        if (relation != null
                && relation != Relation.MEMBER) {
            boolean current =
                    faction.hasPermission(
                            relation,
                            action
                    );

            faction.setPermission(
                    relation,
                    action,
                    !current
            );

            plugin.getStorageManager()
                    .markDirty(faction);

            player.sendMessage(
                    "§7Permission §f"
                            + action.getDisplayName()
                            + " §7pour §f"
                            + relation.getDisplayName()
                            + " §7: "
                            + (!current
                                    ? "§a✔ Activée"
                                    : "§c✖ Désactivée")
            );

            return;
        }

        player.sendMessage(
                "§c✖ Rôle ou relation invalide: "
                        + targetArg
        );
    }

    private void handleReset(
            Player player,
            Faction faction,
            String[] args
    ) {
        if (args.length < 2) {
            player.sendMessage(
                    "§c✖ Usage: /f perms reset <role|relation|all>"
            );
            return;
        }

        if (args[1].equalsIgnoreCase(
                "all"
        )) {
            plugin.getPermissionManager()
                    .resetAllDefaults(
                            faction
                    );

            player.sendMessage(
                    "§a✔ Toutes les permissions ont été réinitialisées selon config.yml."
            );

            return;
        }

        FactionRole role =
                parseRole(
                        args[1]
                );

        if (role != null) {
            if (!plugin.getPermissionManager()
                    .resetRoleDefaults(
                            faction,
                            role
                    )) {
                player.sendMessage(
                        "§c✖ Ce rôle ne peut pas être réinitialisé."
                );
                return;
            }

            player.sendMessage(
                    "§a✔ Permissions de "
                            + role.getDisplayName()
                            + " réinitialisées."
            );

            return;
        }

        Relation relation =
                parseRelation(
                        args[1]
                );

        if (relation != null
                && relation != Relation.MEMBER) {
            plugin.getPermissionManager()
                    .resetRelationDefaults(
                            faction,
                            relation
                    );

            player.sendMessage(
                    "§a✔ Permissions relation "
                            + relation.getDisplayName()
                            + " réinitialisées."
            );

            return;
        }

        player.sendMessage(
                "§c✖ Rôle ou relation invalide: "
                        + args[1]
        );
    }

    private FactionRole parseRole(
            String arg
    ) {
        if (arg == null) {
            return null;
        }

        switch (arg.toLowerCase()) {
            case "recruit":
            case "recrue":
            case "r":
                return FactionRole.RECRUIT;

            case "member":
            case "membre":
            case "m":
                return FactionRole.MEMBER;

            case "officer":
            case "officier":
            case "off":
            case "o":
                return FactionRole.OFFICER;

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

    private Relation parseRelation(
            String arg
    ) {
        if (arg == null) {
            return null;
        }

        try {
            return Relation.valueOf(
                    arg.toUpperCase()
            );
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void showHelp(Player player) {
        player.sendMessage("§6§l━━ Permissions faction ━━");
        player.sendMessage("§e/f perms toggle <rôle|relation> <permission>");
        player.sendMessage("§e/f perms reset <rôle|relation|all>");
        player.sendMessage("§7Le pack Kgui optionnel fournit une interface configurable.");
    }

    @Override
    public List<String> tabComplete(
            CommandSender sender,
            String[] args
    ) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }

        Player player =
                (Player) sender;

        FPlayer fPlayer =
                plugin.getFPlayerManager()
                        .findLoaded(
                                player.getUniqueId()
                        );

        if (fPlayer == null
                || !fPlayer.hasFaction()) {
            return Collections.emptyList();
        }

        Faction faction =
                plugin.getFactionManager()
                        .getFaction(
                                fPlayer.getFactionId()
                        );

        if (faction == null) {
            return Collections.emptyList();
        }

        FactionRole role =
                faction.getRole(
                        player.getUniqueId()
                );

        if (!plugin.getPermissionManager().can(
                player,
                FactionCapability.EDIT_PERMISSIONS
        )
                && role != FactionRole.COLEADER
                && role != FactionRole.LEADER) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filter(
                    list(
                            "recruit",
                            "member",
                            "officer",
                            "moderator",
                            "coleader",
                            "toggle",
                            "reset"
                    ),
                    args[0]
            );
        }

        if (args.length == 2
                && (args[0].equalsIgnoreCase("toggle")
                || args[0].equalsIgnoreCase("reset"))) {
            List<String> targets =
                    list(
                            "RECRUIT",
                            "MEMBER",
                            "OFFICER",
                            "MODERATOR",
                            "COLEADER",
                            "ALLY",
                            "TRUCE",
                            "NEUTRAL",
                            "ENEMY"
                    );

            if (args[0].equalsIgnoreCase(
                    "reset"
            )) {
                targets.add("ALL");
            }

            return filter(
                    targets,
                    args[1]
            );
        }

        if (args.length == 3
                && args[0].equalsIgnoreCase(
                        "toggle"
                )) {
            List<String> permissions =
                    new ArrayList<String>();

            for (PermissionAction action
                    : PermissionAction.values()) {
                if (action.isDisplayable()) {
                    permissions.add(
                            action.getConfigKey()
                    );
                }
            }

            return filter(
                    permissions,
                    args[2]
            );
        }

        return Collections.emptyList();
    }

    private static List<String> list(
            String... values
    ) {
        List<String> result =
                new ArrayList<String>();

        if (values != null) {
            Collections.addAll(
                    result,
                    values
            );
        }

        return result;
    }

    private static List<String> filter(
            List<String> values,
            String prefix
    ) {
        if (values == null) {
            return Collections.emptyList();
        }

        String normalized =
                prefix == null
                        ? ""
                        : prefix.toLowerCase();

        List<String> result =
                new ArrayList<String>();

        for (String value : values) {
            if (value.toLowerCase()
                    .startsWith(
                            normalized
                    )) {
                result.add(value);
            }
        }

        return result;
    }

    @Override
    public String getUsage() {
        return "[role|toggle|reset]";
    }

    @Override
    public String getDescription() {
        return "Gérer les permissions des rôles";
    }
}
