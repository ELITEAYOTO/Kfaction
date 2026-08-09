package me.krunsh.kfaction.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.core.operation.OperationResult;
import me.krunsh.kfaction.core.operation.OperationSource;
import me.krunsh.kfaction.data.FLocation;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.permissions.FactionCapability;
import me.krunsh.kfaction.services.claim.ClaimBatchResult;

/**
 * /f autoclaim
 */
public class AutoClaimCommand extends SubCommand {

    public AutoClaimCommand(Kfaction plugin) {
        super(plugin);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        if (player == null) return;

        FPlayer fPlayer = plugin.getFPlayerManager()
                .findLoaded(player.getUniqueId());

        if (fPlayer == null || !fPlayer.hasFaction()) {
            sendMessage(sender, "general.no-faction");
            return;
        }

        Faction faction = plugin.getFactionManager()
                .getFaction(fPlayer.getFactionId());

        if (faction == null) {
            sendMessage(sender, "general.error");
            return;
        }

        if (!plugin.getPermissionManager().can(
                player,
                FactionCapability.AUTO_CLAIM
        )) {
            sendMessage(sender, "general.no-permission");
            return;
        }

        boolean enabled =
                !fPlayer.isAutoClaimEnabled();

        fPlayer.setAutoClaimEnabled(enabled);
        plugin.getStorageManager().markDirty(fPlayer);

        if (!enabled) {
            sendMessage(sender, "autoclaim.disabled");
            return;
        }

        sendMessage(sender, "autoclaim.enabled");

        FLocation location =
                new FLocation(player.getLocation());

        OperationResult<ClaimBatchResult> result =
                plugin.getClaimManager()
                        .getService()
                        .claimSingle(
                                player,
                                faction,
                                location,
                                OperationContext.actor(
                                        player.getUniqueId(),
                                        player.getName(),
                                        OperationSource.COMMAND
                                )
                        );

        if (result.isFailure()
                && result.getStatus()
                != OperationResult.Status.NO_CHANGE) {
            sendMessage(
                    sender,
                    "claim.failed",
                    "{reason}",
                    result.hasDetail()
                            ? result.getDetail()
                            : "Claim refusé"
            );
        }
    }

    @Override public String getName() { return "autoclaim"; }
    @Override public String getDescription() { return "Active/désactive l'auto-claim"; }
    @Override public String getUsage() { return ""; }
}
