package me.krunsh.kfaction.commands;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.core.operation.OperationResult;
import me.krunsh.kfaction.grace.GraceState;
import me.krunsh.kfaction.services.GraceService;

/**
 * /f grace status
 * /f grace start <durée> [raison]
 * /f grace extend <durée>
 * /f grace stop
 * /f grace reload
 */
public final class GraceCommand extends SubCommand {

    public GraceCommand(Kfaction plugin) {
        super(plugin);
    }

    @Override
    public void execute(
            CommandSender sender,
            String[] args
    ) {
        GraceService service =
                plugin.getPermissionManager()
                        .getGraceService();

        String sub =
                args.length == 0
                        ? "status"
                        : args[0].toLowerCase(
                                Locale.ROOT
                        );

        if ("status".equals(sub)
                || "info".equals(sub)) {
            if (!plugin.getConfigManager()
                    .getBoolean(
                            "grace.status-public",
                            true
                    )
                    && !canManage(sender)) {
                sender.sendMessage(
                        "§cVous n'avez pas la permission."
                );
                return;
            }

            sendStatus(
                    sender,
                    service
            );
            return;
        }

        if (!canManage(sender)) {
            sender.sendMessage(
                    "§cVous n'avez pas la permission "
                            + "de gérer la Grace Period."
            );
            return;
        }

        if ("start".equals(sub)
                || "begin".equals(sub)) {
            if (args.length < 2) {
                sender.sendMessage(
                        "§cUsage: /f grace start <durée> [raison]"
                );
                sender.sendMessage(
                        "§7Exemples: 30m, 12h, 2d, 1d12h"
                );
                return;
            }

            long duration =
                    parseDurationMillis(
                            args[1]
                    );

            if (duration <= 0L) {
                sender.sendMessage(
                        "§cDurée invalide. "
                                + "Exemples: 30m, 12h, 2d, 1d12h"
                );
                return;
            }

            String reason =
                    join(
                            args,
                            2
                    );

            OperationResult<GraceState> result =
                    service.start(
                            duration,
                            reason,
                            context(sender)
                    );

            sendResult(
                    sender,
                    result
            );
            return;
        }

        if ("extend".equals(sub)
                || "add".equals(sub)) {
            if (args.length < 2) {
                sender.sendMessage(
                        "§cUsage: /f grace extend <durée>"
                );
                return;
            }

            long duration =
                    parseDurationMillis(
                            args[1]
                    );

            if (duration <= 0L) {
                sender.sendMessage(
                        "§cDurée invalide."
                );
                return;
            }

            OperationResult<GraceState> result =
                    service.extend(
                            duration,
                            context(sender)
                    );

            sendResult(
                    sender,
                    result
            );
            return;
        }

        if ("stop".equals(sub)
                || "end".equals(sub)) {
            OperationResult<GraceState> result =
                    service.stop(
                            context(sender)
                    );

            if (result.getStatus()
                    == OperationResult.Status.NO_CHANGE) {
                sender.sendMessage(
                        "§eLa Grace Period est déjà inactive."
                );
                return;
            }

            sendResult(
                    sender,
                    result
            );
            return;
        }

        if ("reload".equals(sub)) {
            service.reload();

            sender.sendMessage(
                    "§aConfiguration Grace Period rechargée."
            );

            return;
        }

        sendHelp(sender);
    }

    private void sendStatus(
            CommandSender sender,
            GraceService service
    ) {
        GraceState state =
                service.getStateSnapshot();

        sender.sendMessage(
                "§6§l━━━ Grace Period V2 §6§l━━━"
        );

        if (!state.isActiveAt(
                System.currentTimeMillis()
        )) {
            sender.sendMessage(
                    "§7État: §cINACTIVE"
            );
            return;
        }

        sender.sendMessage(
                "§7État: §aACTIVE"
        );

        sender.sendMessage(
                "§7Temps restant: §e"
                        + GraceService.formatDuration(
                                service.getRemainingMillis()
                        )
        );

        sender.sendMessage(
                "§7Fin: §f"
                        + formatDate(
                                state.getEndsAt()
                        )
        );

        if (state.getStartedBy() != null) {
            sender.sendMessage(
                    "§7Activée par: §f"
                            + state.getStartedBy()
            );
        }

        if (state.getReason() != null) {
            sender.sendMessage(
                    "§7Raison: §f"
                            + state.getReason()
            );
        }

        sender.sendMessage(
                "§7Protection raids/overclaims: "
                        + (service.blocksRaids()
                                ? "§aON"
                                : "§cOFF")
        );

        sender.sendMessage(
                "§7PvP ennemi bloqué: "
                        + (service.blocksEnemyPvp()
                                ? "§aON"
                                : "§cOFF")
        );
    }

    private void sendResult(
            CommandSender sender,
            OperationResult<GraceState> result
    ) {
        if (result == null) {
            sender.sendMessage(
                    "§cOpération Grace Period invalide."
            );
            return;
        }

        if (!result.isSuccess()) {
            sender.sendMessage(
                    "§c✖ "
                            + (result.hasDetail()
                                    ? result.getDetail()
                                    : "Opération refusée")
            );
            return;
        }

        GraceState state =
                result.getValue();

        if (state != null
                && state.isActiveAt(
                        System.currentTimeMillis()
                )) {
            sender.sendMessage(
                    "§a✔ Grace Period active jusqu'au §f"
                            + formatDate(
                                    state.getEndsAt()
                            )
            );
        } else {
            sender.sendMessage(
                    "§a✔ Grace Period inactive."
            );
        }
    }

    private void sendHelp(
            CommandSender sender
    ) {
        sender.sendMessage(
                "§6§l━━━ Grace Period §6§l━━━"
        );
        sender.sendMessage(
                "§e/f grace status"
        );

        if (canManage(sender)) {
            sender.sendMessage(
                    "§e/f grace start <durée> [raison]"
            );
            sender.sendMessage(
                    "§e/f grace extend <durée>"
            );
            sender.sendMessage(
                    "§e/f grace stop"
            );
            sender.sendMessage(
                    "§e/f grace reload"
            );
        }
    }

    private boolean canManage(
            CommandSender sender
    ) {
        return !(sender instanceof Player)
                || sender.hasPermission(
                        "kfaction.admin"
                )
                || sender.hasPermission(
                        "kfaction.admin.grace"
                );
    }

    private static OperationContext context(
            CommandSender sender
    ) {
        if (sender instanceof Player) {
            Player player =
                    (Player) sender;

            return OperationContext.admin(
                    player.getUniqueId(),
                    player.getName()
            );
        }

        return OperationContext.admin(
                sender != null
                        ? sender.getName()
                        : "CONSOLE"
        );
    }

    /**
     * Formats:
     * 30m
     * 12h
     * 2d
     * 1d12h
     * 1w2d6h
     * 3600 -> secondes
     */
    public static long parseDurationMillis(
            String input
    ) {
        if (input == null) {
            return -1L;
        }

        String value =
                input.trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        if (value.isEmpty()) {
            return -1L;
        }

        if (value.matches("^[0-9]+$")) {
            try {
                long seconds =
                        Long.parseLong(value);

                return safeMultiply(
                        seconds,
                        1000L
                );
            } catch (NumberFormatException exception) {
                return -1L;
            }
        }

        long total = 0L;
        int index = 0;
        boolean found = false;

        while (index < value.length()) {
            int numberStart = index;

            while (index < value.length()
                    && Character.isDigit(
                            value.charAt(index)
                    )) {
                index++;
            }

            if (numberStart == index
                    || index >= value.length()) {
                return -1L;
            }

            long amount;

            try {
                amount =
                        Long.parseLong(
                                value.substring(
                                        numberStart,
                                        index
                                )
                        );
            } catch (NumberFormatException exception) {
                return -1L;
            }

            char unit =
                    value.charAt(index++);

            long multiplier;

            switch (unit) {
                case 's':
                    multiplier = 1000L;
                    break;

                case 'm':
                    multiplier = 60_000L;
                    break;

                case 'h':
                    multiplier = 3_600_000L;
                    break;

                case 'd':
                    multiplier = 86_400_000L;
                    break;

                case 'w':
                    multiplier = 604_800_000L;
                    break;

                default:
                    return -1L;
            }

            long part =
                    safeMultiply(
                            amount,
                            multiplier
                    );

            if (part < 0L
                    || total > Long.MAX_VALUE - part) {
                return -1L;
            }

            total += part;
            found = true;
        }

        return found
                ? total
                : -1L;
    }

    private static long safeMultiply(
            long left,
            long right
    ) {
        if (left < 0L || right < 0L) {
            return -1L;
        }

        if (left != 0L
                && right > Long.MAX_VALUE / left) {
            return -1L;
        }

        return left * right;
    }

    private static String join(
            String[] args,
            int start
    ) {
        if (args == null
                || start >= args.length) {
            return null;
        }

        StringBuilder builder =
                new StringBuilder();

        for (int i = start;
                i < args.length;
                i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }

            builder.append(args[i]);
        }

        String value =
                builder.toString()
                        .trim();

        return value.isEmpty()
                ? null
                : value;
    }

    private static String formatDate(
            long timestamp
    ) {
        return new SimpleDateFormat(
                "dd/MM/yyyy HH:mm:ss"
        ).format(
                new Date(timestamp)
        );
    }

    @Override
    public List<String> tabComplete(
            CommandSender sender,
            String[] args
    ) {
        if (args.length == 1) {
            List<String> values =
                    new ArrayList<String>();

            values.add("status");

            if (canManage(sender)) {
                values.add("start");
                values.add("extend");
                values.add("stop");
                values.add("reload");
            }

            return filter(
                    values,
                    args[0]
            );
        }

        if (args.length == 2
                && ("start".equalsIgnoreCase(
                        args[0]
                )
                || "extend".equalsIgnoreCase(
                        args[0]
                ))) {
            return filter(
                    list(
                            "30m",
                            "1h",
                            "6h",
                            "12h",
                            "1d",
                            "2d",
                            "7d"
                    ),
                    args[1]
            );
        }

        return Collections.emptyList();
    }

    private static List<String> list(
            String... values
    ) {
        List<String> result =
                new ArrayList<String>();

        if (values != null) {
            Collections.addAll(
                    result,
                    values
            );
        }

        return result;
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
            if (value.toLowerCase(
                    Locale.ROOT
            ).startsWith(normalized)) {
                result.add(value);
            }
        }

        Collections.sort(result);

        return result;
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }

    @Override
    public String getName() {
        return "grace";
    }

    @Override
    public String getDescription() {
        return "Afficher ou gérer la Grace Period";
    }

    @Override
    public String getUsage() {
        return "[status|start|extend|stop|reload]";
    }
}
