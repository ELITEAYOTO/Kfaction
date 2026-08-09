package me.krunsh.kfaction.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.api.event.FactionDisbandEvent;
import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.core.operation.OperationResult;
import me.krunsh.kfaction.core.operation.OperationSource;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.services.FactionLifecycleService;

public class DisbandCommand extends SubCommand {

    private final FactionLifecycleService lifecycleService;

    public DisbandCommand(Kfaction plugin) {
        super(plugin);
        this.lifecycleService =
                new FactionLifecycleService(plugin);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        if (player == null) {
            return;
        }

        Faction faction =
                plugin.getFactionManager()
                        .getPlayerFaction(player);

        if (faction == null) {
            sendMessage(sender, "disband.not-in-faction");
            return;
        }

        if (!faction.isLeader(player.getUniqueId())) {
            sendMessage(sender, "disband.not-leader");
            return;
        }

        FactionDisbandEvent event =
                new FactionDisbandEvent(
                        faction,
                        player,
                        FactionDisbandEvent.DisbandReason.COMMAND
                );

        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            sendMessage(sender, "disband.cancelled");
            return;
        }

        String name = faction.getName();

        OperationResult<Integer> result =
                lifecycleService.disband(
                        faction,
                        OperationContext.actor(
                                player.getUniqueId(),
                                player.getName(),
                                OperationSource.COMMAND
                        )
                );

        if (!result.isSuccessful()) {
            player.sendMessage(
                    "§cImpossible de dissoudre la faction: "
                            + result.getStatus().name()
            );
            return;
        }

        sendMessage(
                sender,
                "disband.success",
                "{name}", name
        );
    }

    @Override public String getName() { return "disband"; }
    @Override public String getDescription() { return "Dissoudre la faction"; }
    @Override public String getUsage() { return ""; }
}
