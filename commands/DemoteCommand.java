package me.krunsh.kfaction.commands;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.core.operation.OperationResult;
import me.krunsh.kfaction.core.operation.OperationResult.Status;
import me.krunsh.kfaction.core.operation.OperationSource;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionRole;
import me.krunsh.kfaction.permissions.FactionCapability;
import me.krunsh.kfaction.services.RoleService;

/**
 * /f demote <joueur>
 *
 * Pipeline V2:
 * permission -> hiérarchie anti-inside -> RoleService -> persistence/log/hooks.
 *
 * COLEADER -> MODERATOR -> OFFICER -> MEMBER -> RECRUIT
 */
public class DemoteCommand extends SubCommand {

    private final RoleService roleService;

    public DemoteCommand(Kfaction plugin) {
        super(plugin);
        this.roleService = new RoleService(plugin);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);

        FPlayer fPlayer =
                plugin.getFPlayerManager()
                        .getOrCreate(player);

        if (!fPlayer.hasFaction()) {
            sendMessage(sender, "demote.not-in-faction");
            return;
        }

        if (args.length < 1) {
            sendMessage(sender, "demote.usage");
            return;
        }

        Faction faction =
                plugin.getFactionManager()
                        .getFaction(
                                fPlayer.getFactionId()
                        );

        if (faction == null) {
            sendMessage(sender, "general.error");
            return;
        }

        if (!plugin.getPermissionManager()
                .can(
                        player,
                        FactionCapability.DEMOTE
                )) {
            sendMessage(sender, "demote.no-permission");
            return;
        }

        OfflinePlayer target =
                Bukkit.getOfflinePlayer(
                        args[0]
                );

        UUID targetId =
                target.getUniqueId();

        if (!faction.isMember(targetId)) {
            sendMessage(sender, "demote.not-member");
            return;
        }

        FactionRole senderRole =
                faction.getRole(
                        player.getUniqueId()
                );

        FactionRole targetRole =
                faction.getRole(
                        targetId
                );

        if (senderRole == null
                || targetRole == null
                || !senderRole.isHigherThan(targetRole)) {
            sendMessage(sender, "demote.hierarchy-denied");
            return;
        }

        OperationResult<FactionRole> result =
                roleService.demote(
                        faction,
                        targetId,
                        OperationContext.actor(
                                player.getUniqueId(),
                                player.getName(),
                                OperationSource.COMMAND
                        )
                );

        if (!result.isSuccess()) {
            if (result.getStatus()
                    == Status.LIMIT_REACHED) {
                sendMessage(sender, "demote.min-rank");
            } else if (result.getStatus()
                    == Status.NOT_FOUND) {
                sendMessage(sender, "demote.not-member");
            } else {
                sendMessage(sender, "general.error");
            }
            return;
        }

        FactionRole newRole =
                result.getValue();

        String targetName =
                safeName(target);

        sendMessage(
                sender,
                "demote.success",
                "{player}", targetName,
                "{role}", newRole != null
                        ? newRole.getDisplayName()
                        : "?"
        );

        if (target.isOnline()
                && target.getPlayer() != null) {
            plugin.getMessageManager()
                    .send(
                            target.getPlayer(),
                            "demote.demoted",
                            "{role}", newRole != null
                                    ? newRole.getDisplayName()
                                    : "?",
                            "{demoter}", player.getName()
                    );
        }

        faction.broadcast(
                plugin.getMessageManager()
                        .get(
                                "demote.broadcast",
                                "{player}", targetName,
                                "{role}", newRole != null
                                        ? newRole.getDisplayName()
                                        : "?"
                        )
        );
    }

    private static String safeName(OfflinePlayer player) {
        String name = player.getName();
        return name != null
                ? name
                : player.getUniqueId().toString();
    }

    @Override
    public String getName() {
        return "demote";
    }

    @Override
    public String getDescription() {
        return "Rétrograder un membre";
    }

    @Override
    public String getUsage() {
        return "<joueur>";
    }
}
