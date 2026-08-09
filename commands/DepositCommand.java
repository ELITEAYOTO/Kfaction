package me.krunsh.kfaction.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.core.operation.OperationResult;
import me.krunsh.kfaction.core.operation.OperationSource;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.economy.EconomyTransactionResult;
import me.krunsh.kfaction.economy.MoneyAmount;
import me.krunsh.kfaction.services.EconomyService;

public class DepositCommand extends SubCommand {

    public DepositCommand(Kfaction plugin) {
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
                resolveFaction(player);

        if (faction == null) {
            sendMessage(
                    sender,
                    "deposit.not-in-faction"
            );
            return;
        }

        if (args.length < 1) {
            sendMessage(
                    sender,
                    "deposit.usage"
            );
            return;
        }

        EconomyService service =
                plugin.getEconomyManager()
                        .getService();

        OperationResult<MoneyAmount> parsed =
                service.parseAmount(
                        args[0]
                );

        if (!parsed.isSuccess()) {
            sendMessage(
                    sender,
                    "deposit.invalid-amount"
            );
            return;
        }

        OperationResult<EconomyTransactionResult> result =
                service.depositToFaction(
                        player,
                        faction,
                        parsed.getValue(),
                        context(player)
                );

        if (!result.isSuccess()) {
            if (result.getStatus()
                    == OperationResult.Status.FORBIDDEN
                    && result.hasDetail()
                    && result.getDetail()
                            .contains(
                                    "Solde joueur"
                            )) {
                sendMessage(
                        sender,
                        "deposit.not-enough-money"
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
                "deposit.success",
                "{amount}",
                service.format(
                        result.getValue()
                                .getAmount()
                )
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
        String reason =
                result != null
                        && result.hasDetail()
                        ? result.getDetail()
                        : "Dépôt refusé";

        player.sendMessage(
                "§c✖ " + reason
        );
    }

    @Override public String getName() { return "deposit"; }
    @Override public String getDescription() { return "Déposer de l'argent dans la banque de faction"; }
    @Override public String getUsage() { return "<montant>"; }
}
