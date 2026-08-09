package me.krunsh.kfaction.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.api.event.PlayerLeaveFactionEvent;
import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.core.operation.OperationResult;
import me.krunsh.kfaction.core.operation.OperationSource;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.services.MembershipService;
import me.krunsh.kfaction.services.MembershipService.ChangeReason;

public class LeaveCommand extends SubCommand {

    private final MembershipService membershipService;

    public LeaveCommand(Kfaction plugin) {
        super(plugin);
        this.membershipService = new MembershipService(plugin);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        if (player == null) {
            return;
        }

        FPlayer fPlayer =
                plugin.getFPlayerManager().getOrCreate(player);

        if (!fPlayer.hasFaction()) {
            sendMessage(sender, "leave.not-in-faction");
            return;
        }

        Faction faction =
                plugin.getFactionManager()
                        .getFaction(fPlayer.getFactionId());

        if (faction == null) {
            sendMessage(sender, "general.error");
            return;
        }

        if (faction.isLeader(player.getUniqueId())) {
            sendMessage(sender, "leave.leader-cannot-leave");
            return;
        }

        PlayerLeaveFactionEvent event =
                new PlayerLeaveFactionEvent(
                        player,
                        faction,
                        PlayerLeaveFactionEvent.LeaveReason.LEAVE
                );

        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            sendMessage(sender, "leave.cancelled");
            return;
        }

        OperationResult<Void> result =
                membershipService.remove(
                        faction,
                        player.getUniqueId(),
                        ChangeReason.LEAVE,
                        OperationContext.actor(
                                player.getUniqueId(),
                                player.getName(),
                                OperationSource.COMMAND
                        ),
                        false
                );

        if (!result.isSuccessful()) {
            player.sendMessage(
                    "§cImpossible de quitter la faction: "
                            + result.getStatus().name()
            );
            return;
        }

        sendMessage(
                sender,
                "leave.success",
                "{name}", faction.getName()
        );
    }

    @Override public String getName() { return "leave"; }
    @Override public String getDescription() { return "Quitter la faction"; }
    @Override public String getUsage() { return ""; }
}
