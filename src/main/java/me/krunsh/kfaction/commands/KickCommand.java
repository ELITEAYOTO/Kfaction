package me.krunsh.kfaction.commands;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.api.event.PlayerLeaveFactionEvent;
import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.core.operation.OperationResult;
import me.krunsh.kfaction.core.operation.OperationSource;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionRole;
import me.krunsh.kfaction.data.PermissionAction;
import me.krunsh.kfaction.services.MembershipService;
import me.krunsh.kfaction.services.MembershipService.ChangeReason;

public class KickCommand extends SubCommand {

    private final MembershipService membershipService;

    public KickCommand(Kfaction plugin) {
        super(plugin);
        this.membershipService = new MembershipService(plugin);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);

        FPlayer fPlayer =
                plugin.getFPlayerManager().getOrCreate(player);

        if (!fPlayer.hasFaction()) {
            sendMessage(sender, "kick.not-in-faction");
            return;
        }

        if (args.length < 1) {
            sendMessage(sender, "kick.usage");
            return;
        }

        Faction faction =
                plugin.getFactionManager()
                        .getFaction(fPlayer.getFactionId());

        if (faction == null) {
            sendMessage(sender, "general.error");
            return;
        }

        if (!faction.hasPermission(
                player.getUniqueId(),
                PermissionAction.KICK
        )) {
            sendMessage(sender, "general.no-permission");
            return;
        }

        OfflinePlayer target =
                Bukkit.getOfflinePlayer(args[0]);

        UUID targetId = target.getUniqueId();

        if (!faction.isMember(targetId)) {
            sendMessage(sender, "kick.not-member");
            return;
        }

        if (faction.isLeader(targetId)) {
            sendMessage(sender, "kick.cannot-kick-leader");
            return;
        }

        FactionRole senderRole =
                faction.getRole(player.getUniqueId());

        FactionRole targetRole =
                faction.getRole(targetId);

        /*
         * Protection anti-inside :
         * une permission KICK ne permet pas d'expulser un rang égal/supérieur.
         */
        if (senderRole == null
                || targetRole == null
                || !senderRole.isHigherThan(targetRole)) {
            player.sendMessage(
                    "§cTu ne peux pas expulser un membre ayant "
                            + "un rang égal ou supérieur au tien."
            );
            return;
        }

        if (target.isOnline() && target.getPlayer() != null) {
            PlayerLeaveFactionEvent event =
                    new PlayerLeaveFactionEvent(
                            target.getPlayer(),
                            faction,
                            PlayerLeaveFactionEvent.LeaveReason.KICK
                    );

            Bukkit.getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                player.sendMessage(
                        "§cL'expulsion a été annulée par un autre plugin."
                );
                return;
            }
        }

        OperationResult<Void> result =
                membershipService.remove(
                        faction,
                        targetId,
                        ChangeReason.KICK,
                        OperationContext.actor(
                                player.getUniqueId(),
                                player.getName(),
                                OperationSource.COMMAND
                        ),
                        false
                );

        if (!result.isSuccessful()) {
            player.sendMessage(
                    "§cImpossible d'expulser ce membre: "
                            + result.getStatus().name()
            );
            return;
        }

        sendMessage(
                sender,
                "kick.success",
                "{player}", safeName(target)
        );

        if (target.isOnline() && target.getPlayer() != null) {
            plugin.getMessageManager().send(
                    target.getPlayer(),
                    "kick.kicked",
                    "{faction}", faction.getName()
            );
        }
    }

    private static String safeName(OfflinePlayer player) {
        return player.getName() != null
                ? player.getName()
                : player.getUniqueId().toString();
    }

    @Override public String getName() { return "kick"; }
    @Override public String getDescription() { return "Expulser un membre"; }
    @Override public String getUsage() { return "<joueur>"; }
}
