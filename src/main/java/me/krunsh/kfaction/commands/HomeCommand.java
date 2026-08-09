package me.krunsh.kfaction.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.core.operation.OperationResult;
import me.krunsh.kfaction.core.operation.OperationSource;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;

/**
 * /f home
 *
 * La commande ne possède plus de BukkitRunnable: tout le warmup vit dans
 * HomeWarpService V2.
 */
public class HomeCommand extends SubCommand {

    public HomeCommand(Kfaction plugin) {
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
                    "home.not-in-faction"
            );
            return;
        }

        OperationResult<String> result =
                plugin.getFactionManager()
                        .getHomeWarpService()
                        .requestHomeTeleport(
                                player,
                                faction,
                                context(player)
                        );

        if (!result.isSuccess()) {
            if (result.getStatus()
                    == OperationResult.Status.NOT_FOUND) {
                sendMessage(
                        sender,
                        "home.not-set"
                );
                return;
            }

            sendFailure(
                    player,
                    result
            );
        }
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
                                : "Téléportation refusée")
        );
    }

    @Override public String getName() { return "home"; }
    @Override public String getDescription() { return "Se téléporter au home de faction"; }
    @Override public String getUsage() { return ""; }
}
