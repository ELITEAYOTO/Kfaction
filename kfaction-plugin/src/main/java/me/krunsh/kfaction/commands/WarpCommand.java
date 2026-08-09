package me.krunsh.kfaction.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.core.operation.OperationResult;
import me.krunsh.kfaction.core.operation.OperationSource;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionWarp;
import me.krunsh.kfaction.data.StoredLocation;
import me.krunsh.kfaction.services.HomeWarpService;

/**
 * /f warp
 * /f warp list
 * /f warp <nom>
 * /f warp delete <nom>
 * /f warp password <nom>
 * /f warp password <nom> off
 */
public class WarpCommand extends SubCommand {

    public WarpCommand(Kfaction plugin) {
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

        if (args.length == 0
                || "list".equalsIgnoreCase(
                        args[0]
                )) {
            showWarpList(
                    sender,
                    faction
            );
            return;
        }

        if ("delete".equalsIgnoreCase(args[0])
                || "del".equalsIgnoreCase(args[0])) {
            if (args.length < 2) {
                sendMessage(
                        sender,
                        "warp.usage-delete"
                );
                return;
            }

            deleteWarp(
                    player,
                    faction,
                    args[1]
            );
            return;
        }

        if ("password".equalsIgnoreCase(args[0])
                || "pass".equalsIgnoreCase(args[0])) {
            handlePassword(
                    player,
                    faction,
                    args
            );
            return;
        }

        OperationResult<String> result =
                plugin.getFactionManager()
                        .getHomeWarpService()
                        .requestWarpTeleportInteractive(
                                player,
                                faction,
                                args[0],
                                context(player)
                        );

        if (!result.isSuccess()) {
            if (result.getStatus()
                    == OperationResult.Status.NOT_FOUND) {
                sendMessage(
                        sender,
                        "warp.not-found",
                        "{name}",
                        args[0]
                );
                return;
            }

            sendFailure(
                    player,
                    result
            );
        }
    }

    private void showWarpList(
            CommandSender sender,
            Faction faction
    ) {
        Map<String, FactionWarp> warps =
                faction.getWarpDataSnapshot();

        if (warps.isEmpty()) {
            sendMessage(
                    sender,
                    "warp.no-warps"
            );
            return;
        }

        int maxWarps =
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
                        );

        sender.sendMessage(
                "§6§l━━━ Warps §7("
                        + warps.size()
                        + "/"
                        + maxWarps
                        + ") §6§l━━━"
        );

        List<String> names =
                new ArrayList<String>(
                        warps.keySet()
                );

        Collections.sort(names);

        for (String name : names) {
            FactionWarp warp =
                    warps.get(name);

            if (warp == null
                    || warp.getStoredLocation() == null) {
                continue;
            }

            StoredLocation location =
                    warp.getStoredLocation();

            sender.sendMessage(
                    "§e• "
                            + name
                            + (warp.isPasswordProtected()
                                    ? " §c[LOCKED]"
                                    : " §a[PUBLIC]")
                            + " §7- "
                            + location.getWorldName()
                            + " ("
                            + (int) Math.floor(
                                    location.getX()
                            )
                            + ", "
                            + (int) Math.floor(
                                    location.getY()
                            )
                            + ", "
                            + (int) Math.floor(
                                    location.getZ()
                            )
                            + ")"
                            + (!location.isWorldAvailable()
                                    ? " §c[WORLD OFFLINE]"
                                    : "")
            );
        }

        sender.sendMessage(
                "§7Utilise §e/f warp <nom> §7pour te téléporter."
        );
    }

    private void deleteWarp(
            Player player,
            Faction faction,
            String name
    ) {
        OperationResult<FactionWarp> result =
                plugin.getFactionManager()
                        .getHomeWarpService()
                        .deleteWarp(
                                player,
                                faction,
                                name,
                                context(player)
                        );

        if (!result.isSuccess()) {
            if (result.getStatus()
                    == OperationResult.Status.NOT_FOUND) {
                sendMessage(
                        player,
                        "warp.not-found",
                        "{name}",
                        name
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
                player,
                "warp.deleted",
                "{name}",
                result.getValue()
                        .getName()
        );
    }

    private void handlePassword(
            Player player,
            Faction faction,
            String[] args
    ) {
        if (args.length < 2) {
            player.sendMessage(
                    "§cUsage: /f warp password <nom> [off]"
            );
            return;
        }

        String name =
                args[1];

        HomeWarpService service =
                plugin.getFactionManager()
                        .getHomeWarpService();

        if (args.length >= 3
                && ("off".equalsIgnoreCase(args[2])
                || "remove".equalsIgnoreCase(args[2])
                || "clear".equalsIgnoreCase(args[2]))) {
            OperationResult<FactionWarp> result =
                    service.setWarpPassword(
                            player,
                            faction,
                            name,
                            null,
                            context(player)
                    );

            if (!result.isSuccess()) {
                sendFailure(
                        player,
                        result
                );
                return;
            }

            player.sendMessage(
                    "§aProtection du warp §e"
                            + result.getValue()
                                    .getName()
                            + " §adésactivée."
            );
            return;
        }

        OperationResult<String> result =
                service.requestWarpPasswordSetup(
                        player,
                        faction,
                        name,
                        context(player)
                );

        if (!result.isSuccess()) {
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
                                : "Opération warp refusée")
        );
    }

    @Override
    public List<String> tabComplete(
            CommandSender sender,
            String[] args
    ) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }

        Player player =
                (Player) sender;

        Faction faction =
                resolveFaction(
                        player
                );

        if (faction == null) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> values =
                    new ArrayList<String>();

            values.add("list");
            values.add("delete");
            values.add("password");
            values.addAll(
                    faction.getWarpNames()
            );

            return filter(
                    values,
                    args[0]
            );
        }

        if (args.length == 2
                && ("delete".equalsIgnoreCase(args[0])
                || "del".equalsIgnoreCase(args[0])
                || "password".equalsIgnoreCase(args[0])
                || "pass".equalsIgnoreCase(args[0]))) {
            return filter(
                    new ArrayList<String>(
                            faction.getWarpNames()
                    ),
                    args[1]
            );
        }

        if (args.length == 3
                && ("password".equalsIgnoreCase(args[0])
                || "pass".equalsIgnoreCase(args[0]))) {
            return filter(
                    Collections.singletonList(
                            "off"
                    ),
                    args[2]
            );
        }

        return Collections.emptyList();
    }

    private static List<String> filter(
            List<String> values,
            String prefix
    ) {
        String normalized =
                prefix != null
                        ? prefix.toLowerCase(
                                Locale.ROOT
                        )
                        : "";

        List<String> result =
                new ArrayList<String>();

        for (String value : values) {
            if (value != null
                    && value.toLowerCase(
                            Locale.ROOT
                    ).startsWith(
                            normalized
                    )) {
                result.add(value);
            }
        }

        Collections.sort(result);

        return result;
    }

    @Override public String getName() { return "warp"; }
    @Override public String getDescription() { return "Warps de faction V2"; }
    @Override public String getUsage() { return "[list|<nom>|delete <nom>|password <nom> [off]]"; }
}
