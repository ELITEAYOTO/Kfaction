package me.krunsh.kfaction.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.core.operation.OperationResult;
import me.krunsh.kfaction.core.operation.OperationSource;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionWarp;

/**
 * /f setwarp <nom>
 *
 * Le mot de passe n'est volontairement PAS accepté comme argument afin de ne
 * pas l'exposer dans la commande. Utiliser ensuite:
 * /f warp password <nom>
 */
public class SetWarpCommand extends SubCommand {

    public SetWarpCommand(Kfaction plugin) {
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
                    "general.no-faction"
            );
            return;
        }

        if (args.length < 1) {
            sendMessage(
                    sender,
                    "warp.usage-setwarp"
            );
            return;
        }

        String name =
                args[0];

        boolean update =
                faction.hasWarp(name);

        OperationResult<FactionWarp> result =
                plugin.getFactionManager()
                        .getHomeWarpService()
                        .setWarp(
                                player,
                                faction,
                                name,
                                null,
                                context(player)
                        );

        if (!result.isSuccess()) {
            if (result.getStatus()
                    == OperationResult.Status.LIMIT_REACHED) {
                sendMessage(
                        sender,
                        "warp.limit-reached",
                        "{max}",
                        String.valueOf(
                                Math.max(
                                        0,
                                        plugin.getConfigManager()
                                                .getInt(
                                                        "warps.max-per-faction",
                                                        1
                                                )
                                )
                                        + Math.max(
                                                0,
                                                faction.getExtraWarps()
                                        )
                        )
                );
                return;
            }

            sendFailure(
                    player,
                    result
            );
            return;
        }

        FactionWarp warp =
                result.getValue();

        sendMessage(
                sender,
                update
                        ? "warp.updated"
                        : "warp.created",
                "{name}",
                warp.getName()
        );

        if (!update) {
            for (Player member
                    : faction.getOnlinePlayers()) {
                if (!member.equals(player)) {
                    plugin.getMessageManager()
                            .send(
                                    member,
                                    "warp.created-broadcast",
                                    "{player}",
                                    player.getName(),
                                    "{name}",
                                    warp.getName()
                            );
                }
            }
        }

        if (!warp.isPasswordProtected()) {
            player.sendMessage(
                    "§7Protection optionnelle: §e/f warp password "
                            + warp.getName()
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
                                : "SetWarp refusé")
        );
    }

    @Override public String getName() { return "setwarp"; }
    @Override public String getDescription() { return "Créer ou déplacer un warp de faction"; }
    @Override public String getUsage() { return "<nom>"; }
}
