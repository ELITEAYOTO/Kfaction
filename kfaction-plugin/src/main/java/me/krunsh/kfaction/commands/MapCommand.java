package me.krunsh.kfaction.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.audit.AuditCategory;
import me.krunsh.kfaction.audit.AuditOutcome;
import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.core.operation.OperationSource;
import me.krunsh.kfaction.data.FLocation;
import me.krunsh.kfaction.data.FPlayer;

/**
 * /f map
 * /f map on
 * /f map off
 * /f map toggle
 * /f map status
 */
public final class MapCommand extends SubCommand {

    public MapCommand(
            Kfaction plugin
    ) {
        super(plugin);
    }

    @Override
    public void execute(
            CommandSender sender,
            String[] args
    ) {
        Player player =
                getPlayer(sender);

        if (player == null) {
            return;
        }

        FPlayer fPlayer =
                plugin.getFPlayerManager()
                        .getOrCreate(player);

        if (args.length == 0) {
            plugin.getMapManager()
                    .showMap(
                            player,
                            new FLocation(
                                    player.getLocation()
                            )
                    );

            return;
        }

        String argument =
                args[0].toLowerCase(
                        Locale.ROOT
                );

        if ("status".equals(argument)) {
            player.sendMessage(
                    fPlayer.isMapAutoUpdateEnabled()
                            ? "§aAuto-map: activée"
                            : "§cAuto-map: désactivée"
            );

            player.sendMessage(
                    plugin.getMapManager()
                            .isAutoMapEnabledGlobally()
                            ? "§7Auto-map serveur: §aautorisée"
                            : "§7Auto-map serveur: §cdésactivée par configuration"
            );

            player.sendMessage(
                    "§7Refresh: §funiquement au changement de chunk"
            );

            return;
        }

        boolean requested;

        if ("on".equals(argument)
                || "auto".equals(argument)) {
            requested = true;

        } else if ("off".equals(argument)) {
            requested = false;

        } else if ("toggle".equals(argument)) {
            requested =
                    !fPlayer.isMapAutoUpdateEnabled();

        } else {
            player.sendMessage(
                    "§cUsage: /f map [on|off|toggle|status]"
            );

            return;
        }

        if (requested
                && !plugin.getMapManager()
                        .isAutoMapEnabledGlobally()) {
            player.sendMessage(
                    "§cL'auto-map est désactivée par la configuration du serveur."
            );

            return;
        }

        boolean changed =
                fPlayer.isMapAutoUpdateEnabled()
                        != requested;

        fPlayer.setMapAutoUpdateEnabled(
                requested
        );

        if (changed) {
            plugin.getStorageManager()
                    .markDirty(fPlayer);

            auditToggle(
                    player,
                    requested
            );
        }

        if (requested) {
            sendMessage(
                    sender,
                    "map.auto-enabled"
            );

            /*
             * Affichage immédiat à l'activation.
             * Les suivants seront uniquement sur changement de chunk.
             */
            plugin.getMapManager()
                    .showMap(player);

        } else {
            plugin.getMapManager()
                    .clearAutoState(
                            player.getUniqueId()
                    );

            sendMessage(
                    sender,
                    "map.auto-disabled"
            );
        }
    }

    private void auditToggle(
            Player player,
            boolean enabled
    ) {
        if (plugin.getLogManager() == null) {
            return;
        }

        plugin.getLogManager()
                .audit(
                        OperationContext.actor(
                                player.getUniqueId(),
                                player.getName(),
                                OperationSource.COMMAND
                        ),
                        AuditCategory.MAP,
                        enabled
                                ? "MAP_AUTO_ENABLE"
                                : "MAP_AUTO_DISABLE",
                        AuditOutcome.SUCCESS,
                        null,
                        null,
                        null,
                        "chunk-change-only=true"
                );
    }

    @Override
    public List<String> tabComplete(
            CommandSender sender,
            String[] args
    ) {
        if (args.length != 1) {
            return Collections.emptyList();
        }

        String prefix =
                args[0] != null
                        ? args[0].toLowerCase(
                                Locale.ROOT
                        )
                        : "";

        List<String> result =
                new ArrayList<String>();

        for (String value
                : Arrays.asList(
                        "on",
                        "off",
                        "toggle",
                        "status"
                )) {
            if (value.startsWith(prefix)) {
                result.add(value);
            }
        }

        return result;
    }

    @Override
    public String getName() {
        return "map";
    }

    @Override
    public String getDescription() {
        return "Afficher la carte des territoires";
    }

    @Override
    public String getUsage() {
        return "[on|off|toggle|status]";
    }
}
