package me.krunsh.kfaction.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.core.operation.OperationResult;
import me.krunsh.kfaction.core.operation.OperationSource;
import me.krunsh.kfaction.data.ClaimGroup;
import me.krunsh.kfaction.data.ClaimGroup.Rule;
import me.krunsh.kfaction.data.FLocation;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionRole;
import me.krunsh.kfaction.data.Relation;
import me.krunsh.kfaction.permissions.FactionCapability;
import me.krunsh.kfaction.permissions.TerritoryAction;
import me.krunsh.kfaction.services.ClaimGroupService;

/**
 * /f claimgroup ...
 *
 * Alias recommandé: /f cg
 */
public final class ClaimGroupCommand extends SubCommand {

    private final ClaimGroupService service;

    public ClaimGroupCommand(Kfaction plugin) {
        super(plugin);
        this.service =
                new ClaimGroupService(plugin);
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
                    "general.no-faction"
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

        if (args.length == 0) {
            sendHelp(player);
            return;
        }

        String sub =
                args[0].toLowerCase();

        if ("list".equals(sub)
                || "ls".equals(sub)) {
            showList(
                    player,
                    faction
            );
            return;
        }

        if ("info".equals(sub)
                || "show".equals(sub)) {
            showInfo(
                    player,
                    faction,
                    args
            );
            return;
        }

        if (!plugin.getPermissionManager().can(
                player,
                FactionCapability.MANAGE_CLAIM_GROUPS
        )) {
            sendMessage(
                    sender,
                    "general.no-permission"
            );
            return;
        }

        OperationContext context =
                OperationContext.actor(
                        player.getUniqueId(),
                        player.getName(),
                        OperationSource.COMMAND
                );

        if ("create".equals(sub)
                || "new".equals(sub)) {
            if (args.length < 2) {
                player.sendMessage(
                        "§cUsage: /f cg create <id>"
                );
                return;
            }

            OperationResult<ClaimGroup> result =
                    service.create(
                            faction,
                            args[1],
                            context
                    );

            if (!result.isSuccess()) {
                sendFailure(
                        player,
                        result
                );
                return;
            }

            player.sendMessage(
                    "§a✔ Claim Group créé: §e"
                            + result.getValue().getId()
            );
            return;
        }

        if ("delete".equals(sub)
                || "remove".equals(sub)
                || "del".equals(sub)) {
            if (args.length < 2) {
                player.sendMessage(
                        "§cUsage: /f cg delete <id>"
                );
                return;
            }

            OperationResult<Integer> result =
                    service.delete(
                            faction,
                            args[1],
                            context
                    );

            if (!result.isSuccess()) {
                sendFailure(
                        player,
                        result
                );
                return;
            }

            player.sendMessage(
                    "§a✔ Claim Group supprimé. §7"
                            + result.getValue()
                            + " chunk(s) ont été désaffectés, aucun claim supprimé."
            );
            return;
        }

        if ("assign".equals(sub)
                || "set".equals(sub)) {
            if (args.length < 2) {
                player.sendMessage(
                        "§cUsage: /f cg assign <id>"
                );
                return;
            }

            FLocation location =
                    new FLocation(
                            player.getLocation()
                    );

            OperationResult<ClaimGroup> result =
                    service.assign(
                            faction,
                            args[1],
                            location,
                            context
                    );

            if (result.getStatus()
                    == OperationResult.Status.NO_CHANGE) {
                player.sendMessage(
                        "§eCe chunk est déjà dans ce Claim Group."
                );
                return;
            }

            if (!result.isSuccess()) {
                sendFailure(
                        player,
                        result
                );
                return;
            }

            player.sendMessage(
                    "§a✔ Chunk §e"
                            + location.getX()
                            + ","
                            + location.getZ()
                            + " §aaffecté au groupe §e"
                            + result.getValue().getId()
            );
            return;
        }

        if ("unassign".equals(sub)
                || "unset".equals(sub)) {
            FLocation location =
                    new FLocation(
                            player.getLocation()
                    );

            OperationResult<String> result =
                    service.unassign(
                            faction,
                            location,
                            context
                    );

            if (result.getStatus()
                    == OperationResult.Status.NO_CHANGE) {
                player.sendMessage(
                        "§eCe chunk n'appartient à aucun Claim Group."
                );
                return;
            }

            if (!result.isSuccess()) {
                sendFailure(
                        player,
                        result
                );
                return;
            }

            player.sendMessage(
                    "§a✔ Chunk retiré du groupe §e"
                            + result.getValue()
                            + "§a. Les permissions générales s'appliquent à nouveau."
            );
            return;
        }

        if ("rule".equals(sub)
                || "perm".equals(sub)) {
            handleRule(
                    player,
                    faction,
                    args,
                    context
            );
            return;
        }

        sendHelp(player);
    }

    private void handleRule(
            Player player,
            Faction faction,
            String[] args,
            OperationContext context
    ) {
        if (args.length < 6) {
            player.sendMessage(
                    "§cUsage rôle: /f cg rule <groupe> role <rôle> <action> <allow|deny|inherit>"
            );
            player.sendMessage(
                    "§cUsage relation: /f cg rule <groupe> relation <relation> <action> <allow|deny|inherit>"
            );
            return;
        }

        String groupId =
                args[1];

        String targetType =
                args[2];

        TerritoryAction action =
                TerritoryAction.fromConfigKey(
                        args[4]
                );

        Rule rule =
                Rule.parse(
                        args[5]
                );

        /*
         * Compatibilité avec une forme accidentelle à 7 arguments:
         * certains routeurs peuvent transmettre une valeur finale.
         * La syntaxe normale reste 6 args après "cg":
         * rule group role MEMBER BLOCK_BREAK deny
         */
        if (action == null
                && args.length >= 7) {
            action =
                    TerritoryAction.fromConfigKey(
                            args[5]
                    );

            rule =
                    Rule.parse(
                            args[6]
                    );
        }

        if (action == null) {
            player.sendMessage(
                    "§cAction territoriale invalide."
            );
            return;
        }

        if (rule == null) {
            player.sendMessage(
                    "§cRègle invalide: allow, deny ou inherit."
            );
            return;
        }

        if ("role".equalsIgnoreCase(targetType)
                || "rank".equalsIgnoreCase(targetType)) {
            FactionRole role =
                    FactionRole.parse(
                            args[3]
                    );

            if (role == null) {
                player.sendMessage(
                        "§cRôle invalide."
                );
                return;
            }

            OperationResult<Rule> result =
                    service.setRoleRule(
                            faction,
                            groupId,
                            role,
                            action,
                            rule,
                            context
                    );

            handleRuleResult(
                    player,
                    result,
                    "rôle "
                            + role.name(),
                    action
            );
            return;
        }

        if ("relation".equalsIgnoreCase(targetType)
                || "rel".equalsIgnoreCase(targetType)) {
            Relation relation =
                    Relation.fromString(
                            args[3]
                    );

            if (relation == null
                    || relation == Relation.MEMBER) {
                player.sendMessage(
                        "§cRelation invalide. Utilise ALLY, TRUCE, NEUTRAL ou ENEMY."
                );
                return;
            }

            OperationResult<Rule> result =
                    service.setRelationRule(
                            faction,
                            groupId,
                            relation,
                            action,
                            rule,
                            context
                    );

            handleRuleResult(
                    player,
                    result,
                    "relation "
                            + relation.name(),
                    action
            );
            return;
        }

        player.sendMessage(
                "§cType invalide: role ou relation."
        );
    }

    private void handleRuleResult(
            Player player,
            OperationResult<Rule> result,
            String target,
            TerritoryAction action
    ) {
        if (result.getStatus()
                == OperationResult.Status.NO_CHANGE) {
            player.sendMessage(
                    "§eLa règle est déjà dans cet état."
            );
            return;
        }

        if (!result.isSuccess()) {
            sendFailure(
                    player,
                    result
            );
            return;
        }

        player.sendMessage(
                "§a✔ Règle mise à jour: §e"
                        + target
                        + " §7/ §e"
                        + action.name()
                        + " §7= §e"
                        + result.getValue().name()
        );
    }

    private void showList(
            Player player,
            Faction faction
    ) {
        if (faction.getClaimGroups()
                .isEmpty()) {
            player.sendMessage(
                    "§7Aucun Claim Group. §e/f cg create <id>"
            );
            return;
        }

        player.sendMessage(
                "§6§l━━━ Claim Groups §7("
                        + faction.getClaimGroupCount()
                        + ") §6§l━━━"
        );

        List<String> ids =
                new ArrayList<String>(
                        faction.getClaimGroups()
                                .keySet()
                );

        Collections.sort(ids);

        for (String id : ids) {
            ClaimGroup group =
                    faction.getClaimGroup(id);

            player.sendMessage(
                    "§e• "
                            + id
                            + " §7- chunks: §f"
                            + faction.countClaimsInGroup(id)
                            + " §7- overrides: §f"
                            + (group != null
                                    ? group.getOverrideCount()
                                    : 0)
            );
        }
    }

    private void showInfo(
            Player player,
            Faction faction,
            String[] args
    ) {
        ClaimGroup group;

        if (args.length >= 2) {
            group =
                    faction.getClaimGroup(
                            args[1]
                    );
        } else {
            FLocation location =
                    new FLocation(
                            player.getLocation()
                    );

            group =
                    faction.getClaimGroupAt(
                            location
                    );
        }

        if (group == null) {
            player.sendMessage(
                    "§cAucun Claim Group trouvé."
            );
            return;
        }

        player.sendMessage(
                "§6§l━━━ Claim Group: §e"
                        + group.getId()
                        + " §6§l━━━"
        );

        player.sendMessage(
                "§7Chunks: §f"
                        + faction.countClaimsInGroup(
                                group.getId()
                        )
                        + " §7| Overrides: §f"
                        + group.getOverrideCount()
        );

        for (Map.Entry<FactionRole,
                Map<TerritoryAction, Rule>> roleEntry
                : group.getRoleRulesSnapshot()
                        .entrySet()) {
            for (Map.Entry<TerritoryAction, Rule> ruleEntry
                    : roleEntry.getValue()
                            .entrySet()) {
                player.sendMessage(
                        "§7ROLE §e"
                                + roleEntry.getKey().name()
                                + " §8→ §f"
                                + ruleEntry.getKey().name()
                                + " §8= "
                                + colorRule(
                                        ruleEntry.getValue()
                                )
                );
            }
        }

        for (Map.Entry<Relation,
                Map<TerritoryAction, Rule>> relationEntry
                : group.getRelationRulesSnapshot()
                        .entrySet()) {
            for (Map.Entry<TerritoryAction, Rule> ruleEntry
                    : relationEntry.getValue()
                            .entrySet()) {
                player.sendMessage(
                        "§7REL §e"
                                + relationEntry.getKey().name()
                                + " §8→ §f"
                                + ruleEntry.getKey().name()
                                + " §8= "
                                + colorRule(
                                        ruleEntry.getValue()
                                )
                );
            }
        }

        if (!group.hasOverrides()) {
            player.sendMessage(
                    "§7Aucun override: toutes les règles héritent de la faction."
            );
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage(
                "§6§l━━━ Claim Groups V2 §6§l━━━"
        );
        player.sendMessage(
                "§e/f cg list §7- liste des groupes"
        );
        player.sendMessage(
                "§e/f cg info [id] §7- détails du groupe ou du chunk courant"
        );
        player.sendMessage(
                "§e/f cg create <id> §7- créer"
        );
        player.sendMessage(
                "§e/f cg delete <id> §7- supprimer sans unclaim"
        );
        player.sendMessage(
                "§e/f cg assign <id> §7- affecter le chunk courant"
        );
        player.sendMessage(
                "§e/f cg unassign §7- retirer l'affectation du chunk"
        );
        player.sendMessage(
                "§e/f cg rule <id> role <rôle> <action> <allow|deny|inherit>"
        );
        player.sendMessage(
                "§e/f cg rule <id> relation <relation> <action> <allow|deny|inherit>"
        );
    }

    private static String colorRule(Rule rule) {
        if (rule == Rule.ALLOW) {
            return "§aALLOW";
        }

        if (rule == Rule.DENY) {
            return "§cDENY";
        }

        return "§7INHERIT";
    }

    private static void sendFailure(
            Player player,
            OperationResult<?> result
    ) {
        String detail =
                result != null
                        && result.hasDetail()
                        ? result.getDetail()
                        : "Opération refusée";

        player.sendMessage(
                "§c✖ "
                        + detail
        );
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

        Faction faction =
                plugin.getFactionManager()
                        .getPlayerFaction(player);

        if (faction == null) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filter(
                    list(
                            "list",
                            "info",
                            "create",
                            "delete",
                            "assign",
                            "unassign",
                            "rule"
                    ),
                    args[0]
            );
        }

        if (args.length == 2
                && ("info".equalsIgnoreCase(args[0])
                || "delete".equalsIgnoreCase(args[0])
                || "assign".equalsIgnoreCase(args[0])
                || "rule".equalsIgnoreCase(args[0]))) {
            return filter(
                    new ArrayList<String>(
                            faction.getClaimGroups()
                                    .keySet()
                    ),
                    args[1]
            );
        }

        if (args.length == 3
                && "rule".equalsIgnoreCase(
                        args[0]
                )) {
            return filter(
                    list(
                            "role",
                            "relation"
                    ),
                    args[2]
            );
        }

        if (args.length == 4
                && "rule".equalsIgnoreCase(
                        args[0]
                )) {
            if ("role".equalsIgnoreCase(
                    args[2]
            )) {
                List<String> roles =
                        new ArrayList<String>();

                for (FactionRole role
                        : FactionRole.values()) {
                    if (role != FactionRole.LEADER) {
                        roles.add(
                                role.name()
                        );
                    }
                }

                return filter(
                        roles,
                        args[3]
                );
            }

            if ("relation".equalsIgnoreCase(
                    args[2]
            )) {
                return filter(
                        list(
                                "ALLY",
                                "TRUCE",
                                "NEUTRAL",
                                "ENEMY"
                        ),
                        args[3]
                );
            }
        }

        if (args.length == 5
                && "rule".equalsIgnoreCase(
                        args[0]
                )) {
            List<String> actions =
                    new ArrayList<String>();

            for (TerritoryAction action
                    : TerritoryAction.values()) {
                actions.add(
                        action.name()
                );
            }

            return filter(
                    actions,
                    args[4]
            );
        }

        if (args.length == 6
                && "rule".equalsIgnoreCase(
                        args[0]
                )) {
            return filter(
                    list(
                            "allow",
                            "deny",
                            "inherit"
                    ),
                    args[5]
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
                prefix != null
                        ? prefix.toLowerCase()
                        : "";

        List<String> result =
                new ArrayList<String>();

        for (String value : values) {
            if (value.toLowerCase()
                    .startsWith(normalized)) {
                result.add(value);
            }
        }

        Collections.sort(result);
        return result;
    }

    @Override
    public String getName() {
        return "claimgroup";
    }

    @Override
    public String getDescription() {
        return "Gérer les groupes de claims et leurs ACL";
    }

    @Override
    public String getUsage() {
        return "<list|info|create|delete|assign|unassign|rule>";
    }
}
