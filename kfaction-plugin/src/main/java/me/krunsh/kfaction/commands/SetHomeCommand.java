package me.krunsh.kfaction.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.core.operation.OperationResult;
import me.krunsh.kfaction.core.operation.OperationSource;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.StoredLocation;

public class SetHomeCommand extends SubCommand {

    public SetHomeCommand(Kfaction plugin) {
        super(plugin);
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

        Faction faction =
                resolveFaction(
                        player
                );

        if (faction == null) {
            sendMessage(
                    sender,
                    "sethome.not-in-faction"
            );
            return;
        }

        OperationResult<StoredLocation> result =
                plugin.getFactionManager()
                        .getHomeWarpService()
                        .setHome(
                                player,
                                faction,
                                context(player)
                        );

        if (!result.isSuccess()) {
            if (result.getStatus()
                    == OperationResult.Status.FORBIDDEN
                    && result.hasDetail()
                    && result.getDetail()
                            .contains(
                                    "territoire"
                            )) {
                sendMessage(
                        sender,
                        "sethome.not-in-territory"
                );
                return;
            }

            sendFailure(
                    player,
                    result
            );
            return;
        }

        sendMessage(
                sender,
                "sethome.success"
        );
    }

    private Faction resolveFaction(
            Player player
    ) {
        FPlayer fPlayer =
                plugin.getFPlayerManager()
                        .findLoaded(
                                player.getUniqueId()
                        );

        if (fPlayer == null
                || !fPlayer.hasFaction()) {
            return null;
        }

        return plugin.getFactionManager()
                .getFaction(
                        fPlayer.getFactionId()
                );
    }

    private static OperationContext context(
            Player player
    ) {
        return OperationContext.actor(
                player.getUniqueId(),
                player.getName(),
                OperationSource.COMMAND
        );
    }

    private static void sendFailure(
            Player player,
            OperationResult<?> result
    ) {
        player.sendMessage(
                "§c✖ "
                        + (result != null
                        && result.hasDetail()
                                ? result.getDetail()
                                : "SetHome refusé")
        );
    }

    @Override public String getName() { return "sethome"; }
    @Override public String getDescription() { return "Définir le home de faction"; }
    @Override public String getUsage() { return ""; }
}
