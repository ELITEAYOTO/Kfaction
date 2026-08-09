package me.krunsh.kfaction.commands;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FactionLog.LogType;
import me.krunsh.kfaction.data.FLocation;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.managers.ClaimManager.ClaimResult;
import me.krunsh.kfaction.permissions.FactionCapability;

/**
 * /f claim [rayon]
 *
 * Le batch de claim reste compatible V1 dans ce lot.
 * Sa planification atomique sera déplacée dans ClaimService lors du lot
 * consacré aux claims/fill/connectivité.
 */
public class ClaimCommand extends SubCommand {

    public ClaimCommand(Kfaction plugin) {
        super(plugin);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        if (player == null) return;

        FPlayer fPlayer = plugin.getFPlayerManager()
                .findLoaded(player.getUniqueId());

        if (fPlayer == null || !fPlayer.hasFaction()) {
            sendMessage(sender, "claim.not-in-faction");
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
                FactionCapability.CLAIM
        )) {
            sendMessage(sender, "general.no-permission");
            return;
        }

        int radius = 1;
        if (args.length > 0) {
            try {
                radius = Integer.parseInt(args[0]);
                if (radius < 1) radius = 1;
                if (radius > 10) {
                    sendMessage(sender, "claim.radius-too-large");
                    return;
                }
            } catch (NumberFormatException exception) {
                sendMessage(sender, "claim.invalid-radius");
                return;
            }
        }

        FLocation center = new FLocation(player.getLocation());

        if (radius == 1) {
            ClaimResult result = plugin.getClaimManager()
                    .claim(player, faction, center);

            if (result.isSuccess()) {
                plugin.getLogManager().log(
                        faction.getId(),
                        LogType.TERRITORY_CLAIM,
                        player,
                        null,
                        "[" + center.getX() + ", " + center.getZ() + "]"
                );
                sendMessage(
                        sender,
                        "claim.success",
                        "{x}", String.valueOf(center.getX()),
                        "{z}", String.valueOf(center.getZ())
                );
            } else {
                sendMessage(
                        sender,
                        "claim.failed",
                        "{reason}", result.getMessage()
                );
            }
            return;
        }

        List<FLocation> toClaim = new ArrayList<FLocation>();
        int offset = radius - 1;

        for (int dx = -offset; dx <= offset; dx++) {
            for (int dz = -offset; dz <= offset; dz++) {
                toClaim.add(new FLocation(
                        center.getWorldName(),
                        center.getX() + dx,
                        center.getZ() + dz
                ));
            }
        }

        double factionPower = plugin.getPowerManager()
                .getFactionPower(faction);
        int currentClaims = faction.getClaimCount();
        int maxClaims = (int) factionPower;
        int availableClaims = maxClaims - currentClaims;

        if (availableClaims < toClaim.size()) {
            sendMessage(
                    sender,
                    "claim.not-enough-power",
                    "{needed}", String.valueOf(toClaim.size()),
                    "{available}", String.valueOf(availableClaims)
            );
            return;
        }

        int success = 0;
        int failed = 0;

        for (FLocation location : toClaim) {
            ClaimResult result = plugin.getClaimManager()
                    .claim(player, faction, location);

            if (result.isSuccess()) {
                success++;
            } else {
                failed++;
            }
        }

        if (success > 0) {
            plugin.getLogManager().log(
                    faction.getId(),
                    LogType.TERRITORY_CLAIM,
                    player,
                    null,
                    success + " chunks (rayon " + radius + ")"
            );
            sendMessage(
                    sender,
                    "claim.radius-success",
                    "{count}", String.valueOf(success),
                    "{radius}", String.valueOf(radius)
            );
        }

        if (failed > 0) {
            sendMessage(
                    sender,
                    "claim.radius-failed",
                    "{count}", String.valueOf(failed)
            );
        }
    }

    @Override public String getName() { return "claim"; }
    @Override public String getDescription() { return "Revendiquer un ou plusieurs chunks"; }
    @Override public String getUsage() { return "[rayon]"; }
}
