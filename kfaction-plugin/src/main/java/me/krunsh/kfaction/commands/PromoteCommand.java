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
 * /f promote <joueur>
 *
 * Pipeline V2:
 * permission -> hiérarchie anti-inside -> RoleService -> persistence/log/hooks.
 *
 * RECRUIT -> MEMBER -> OFFICER -> MODERATOR -> COLEADER
 *
 * COLEADER -> LEADER reste un transfert de leadership séparé.
 */
public class PromoteCommand extends SubCommand {

    private final RoleService roleService;

    public PromoteCommand(Kfaction plugin) {
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
            sendMessage(sender, "promote.not-in-faction");
            return;
        }

        if (args.length < 1) {
            sendMessage(sender, "promote.usage");
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
                        FactionCapability.PROMOTE
                )) {
            sendMessage(sender, "promote.no-permission");
            return;
        }

        OfflinePlayer target =
                Bukkit.getOfflinePlayer(
                        args[0]
                );

        UUID targetId =
                target.getUniqueId();

        if (!faction.isMember(targetId)) {
            sendMessage(sender, "promote.not-member");
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
            sendMessage(sender, "promote.hierarchy-denied");
            return;
        }

        OperationResult<FactionRole> result =
                roleService.promote(
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
                sendMessage(sender, "promote.max-rank");
            } else if (result.getStatus()
                    == Status.NOT_FOUND) {
                sendMessage(sender, "promote.not-member");
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
                "promote.success",
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
                            "promote.promoted",
                            "{role}", newRole != null
                                    ? newRole.getDisplayName()
                                    : "?",
                            "{promoter}", player.getName()
                    );
        }

        faction.broadcast(
                plugin.getMessageManager()
                        .get(
                                "promote.broadcast",
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
        return "promote";
    }

    @Override
    public String getDescription() {
        return "Promouvoir un membre";
    }

    @Override
    public String getUsage() {
        return "<joueur>";
    }
}
