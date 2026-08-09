package me.krunsh.kfaction.commands;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.audit.AuditCategory;
import me.krunsh.kfaction.audit.AuditEntry;
import me.krunsh.kfaction.audit.AuditQuery;
import me.krunsh.kfaction.audit.AuditService;
import me.krunsh.kfaction.data.Faction;

/**
 * Sous-routeur /kfaction audit.
 *
 * Recherches DB toujours async.
 */
public final class AdminAuditCommand {

    private final Kfaction plugin;

    public AdminAuditCommand(
            Kfaction plugin
    ) {
        this.plugin = plugin;
    }

    public void execute(
            final CommandSender sender,
            String[] args
    ) {
        if (!sender.hasPermission(
                "kfaction.admin.audit"
        )) {
            sender.sendMessage(
                    "§cPermission requise: kfaction.admin.audit"
            );
            return;
        }

        AuditService service =
                plugin.getLogManager()
                        .getAuditService();

        if (args.length < 2
                || "help".equalsIgnoreCase(
                        args[1]
                )) {
            sendHelp(sender);
            return;
        }

        String sub =
                args[1].toLowerCase(
                        Locale.ROOT
                );

        if ("status".equals(sub)) {
            sender.sendMessage(
                    "§6§l━━━ Audit V2 §6§l━━━"
            );

            sender.sendMessage(
                    "§7DB: §f"
                            + service.getStore()
                                    .getDatabaseFile()
                                    .getAbsolutePath()
            );

            sender.sendMessage(
                    "§7Queue: §f"
                            + service.getQueueSize()
                            + "/"
                            + service.getQueueCapacity()
            );

            sender.sendMessage(
                    "§7Dropped: "
                            + (service.getDroppedEntries() == 0L
                                    ? "§a0"
                                    : "§c"
                                    + service.getDroppedEntries())
            );

            return;
        }

        if ("recent".equals(sub)) {
            int limit =
                    parseLimit(
                            args,
                            2,
                            25
                    );

            runQuery(
                    sender,
                    AuditQuery.builder()
                            .limit(limit)
                            .build()
            );

            return;
        }

        if ("search".equals(sub)) {
            AuditQuery query =
                    parseSearch(
                            sender,
                            args
                    );

            if (query != null) {
                runQuery(
                        sender,
                        query
                );
            }

            return;
        }

        sender.sendMessage(
                "§cSous-commande audit inconnue."
        );
        sendHelp(sender);
    }

    public List<String> tabComplete(
            CommandSender sender,
            String[] args
    ) {
        if (!sender.hasPermission(
                "kfaction.admin.audit"
        )) {
            return Collections.emptyList();
        }

        if (args.length == 2) {
            return filter(
                    Arrays.asList(
                            "status",
                            "recent",
                            "search",
                            "help"
                    ),
                    args[1]
            );
        }

        if (args.length >= 3
                && "search".equalsIgnoreCase(
                        args[1]
                )) {
            return filter(
                    Arrays.asList(
                            "faction=",
                            "player=",
                            "category=",
                            "action=",
                            "correlation=",
                            "since=24h",
                            "limit=50"
                    ),
                    args[args.length - 1]
            );
        }

        return Collections.emptyList();
    }

    private AuditQuery parseSearch(
            CommandSender sender,
            String[] args
    ) {
        AuditQuery.Builder builder =
                AuditQuery.builder();

        int limit = 50;

        for (int i = 2;
                i < args.length;
                i++) {
            String token =
                    args[i];

            int separator =
                    token.indexOf('=');

            if (separator <= 0
                    || separator
                    == token.length() - 1) {
                sender.sendMessage(
                        "§cFiltre invalide: "
                                + token
                );
                return null;
            }

            String key =
                    token.substring(
                            0,
                            separator
                    ).toLowerCase(
                            Locale.ROOT
                    );

            String value =
                    token.substring(
                            separator + 1
                    );

            if ("faction".equals(key)) {
                String factionId =
                        resolveFactionId(
                                value
                        );

                if (factionId == null) {
                    sender.sendMessage(
                            "§cFaction introuvable: "
                                    + value
                    );
                    return null;
                }

                builder.factionId(
                        factionId
                );
                continue;
            }

            if ("player".equals(key)) {
                UUID playerId =
                        resolvePlayerId(
                                value
                        );

                if (playerId == null) {
                    sender.sendMessage(
                            "§cJoueur/UUID invalide: "
                                    + value
                    );
                    return null;
                }

                builder.playerId(
                        playerId
                );
                continue;
            }

            if ("category".equals(key)) {
                AuditCategory category;

                try {
                    category =
                            AuditCategory.valueOf(
                                    value.toUpperCase(
                                            Locale.ROOT
                                    )
                            );
                } catch (IllegalArgumentException exception) {
                    sender.sendMessage(
                            "§cCatégorie audit invalide: "
                                    + value
                    );
                    return null;
                }

                builder.category(
                        category
                );
                continue;
            }

            if ("action".equals(key)) {
                builder.action(
                        value.toUpperCase(
                                Locale.ROOT
                        )
                );
                continue;
            }

            if ("correlation".equals(key)) {
                builder.correlationId(
                        value
                );
                continue;
            }

            if ("since".equals(key)) {
                long duration =
                        parseDurationMillis(
                                value
                        );

                if (duration <= 0L) {
                    sender.sendMessage(
                            "§cDurée since invalide: "
                                    + value
                    );
                    return null;
                }

                builder.sinceTimestamp(
                        Math.max(
                                1L,
                                System.currentTimeMillis()
                                        - duration
                        )
                );
                continue;
            }

            if ("limit".equals(key)) {
                try {
                    limit =
                            Integer.parseInt(
                                    value
                            );
                } catch (NumberFormatException exception) {
                    sender.sendMessage(
                            "§cLimit invalide: "
                                    + value
                    );
                    return null;
                }

                continue;
            }

            sender.sendMessage(
                    "§cFiltre audit inconnu: "
                            + key
            );
            return null;
        }

        builder.limit(
                Math.max(
                        1,
                        Math.min(
                                1000,
                                limit
                        )
                )
        );

        return builder.build();
    }

    private void runQuery(
            final CommandSender sender,
            AuditQuery query
    ) {
        sender.sendMessage(
                "§7Recherche audit..."
        );

        plugin.getLogManager()
                .getAuditService()
                .queryAsync(
                        query,
                        new Consumer<List<AuditEntry>>() {
                            @Override
                            public void accept(
                                    List<AuditEntry> entries
                            ) {
                                sendResults(
                                        sender,
                                        entries
                                );
                            }
                        },
                        new Consumer<Throwable>() {
                            @Override
                            public void accept(
                                    Throwable throwable
                            ) {
                                sender.sendMessage(
                                        "§cErreur lecture audit.db: "
                                                + throwable.getMessage()
                                );
                            }
                        }
                );
    }

    private void sendResults(
            CommandSender sender,
            List<AuditEntry> entries
    ) {
        sender.sendMessage(
                "§6§l━━━ Audit results §7("
                        + entries.size()
                        + ") §6§l━━━"
        );

        if (entries.isEmpty()) {
            sender.sendMessage(
                    "§7Aucune entrée."
            );
            return;
        }

        for (AuditEntry entry : entries) {
            String actor =
                    entry.getActorName() != null
                            ? entry.getActorName()
                            : entry.getActorId() != null
                                    ? entry.getActorId()
                                            .toString()
                                    : "SYSTEM";

            String target =
                    entry.getTargetName() != null
                            ? entry.getTargetName()
                            : entry.getTargetId() != null
                                    ? entry.getTargetId()
                                            .toString()
                                    : null;

            sender.sendMessage(
                    "§8["
                            + formatTime(
                                    entry.getTimestamp()
                            )
                            + "] "
                            + outcomeColor(
                                    entry
                            )
                            + entry.getOutcome().name()
                            + " §6"
                            + entry.getCategory().name()
                            + " §e"
                            + entry.getAction()
            );

            sender.sendMessage(
                    " §7actor=§f"
                            + actor
                            + (target != null
                                    ? " §7target=§f"
                                            + target
                                    : "")
                            + (entry.getFactionId() != null
                                    ? " §7faction=§f"
                                            + entry.getFactionId()
                                    : "")
            );

            sender.sendMessage(
                    " §8src="
                            + entry.getSource().name()
                            + " corr="
                            + shortCorrelation(
                                    entry.getCorrelationId()
                            )
                            + (entry.getDetails() != null
                                    ? " §7"
                                            + compact(
                                                    entry.getDetails()
                                            )
                                    : "")
            );
        }
    }

    private void sendHelp(
            CommandSender sender
    ) {
        sender.sendMessage(
                "§6§l━━━ Audit V2 §6§l━━━"
        );
        sender.sendMessage(
                "§e/kfaction audit status"
        );
        sender.sendMessage(
                "§e/kfaction audit recent [limit]"
        );
        sender.sendMessage(
                "§e/kfaction audit search "
                        + "[faction=<id|nom>] "
                        + "[player=<nom|uuid>]"
        );
        sender.sendMessage(
                "§e  [category=<CATEGORY>] "
                        + "[action=<ACTION>] "
                        + "[correlation=<id>]"
        );
        sender.sendMessage(
                "§e  [since=24h] [limit=50]"
        );
        sender.sendMessage(
                "§7Catégories: "
                        + Arrays.toString(
                                AuditCategory.values()
                        )
        );
    }

    private String resolveFactionId(
            String value
    ) {
        Faction faction =
                plugin.getFactionManager()
                        .getFaction(value);

        if (faction == null) {
            faction =
                    plugin.getFactionManager()
                            .getFactionByName(value);
        }

        return faction != null
                ? faction.getId()
                : value != null
                && value.matches(
                        "^[a-zA-Z0-9_-]{1,128}$"
                )
                        ? value
                        : null;
    }

    @SuppressWarnings("deprecation")
    private UUID resolvePlayerId(
            String value
    ) {
        if (value == null
                || value.trim().isEmpty()) {
            return null;
        }

        try {
            return UUID.fromString(
                    value
            );
        } catch (IllegalArgumentException ignored) {
            // Nom joueur.
        }

        OfflinePlayer offline =
                Bukkit.getOfflinePlayer(
                        value
                );

        return offline != null
                ? offline.getUniqueId()
                : null;
    }

    private static int parseLimit(
            String[] args,
            int index,
            int fallback
    ) {
        if (args.length <= index) {
            return fallback;
        }

        try {
            return Math.max(
                    1,
                    Math.min(
                            1000,
                            Integer.parseInt(
                                    args[index]
                            )
                    )
            );
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static long parseDurationMillis(
            String input
    ) {
        if (input == null
                || input.trim().isEmpty()) {
            return -1L;
        }

        String value =
                input.trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        long multiplier;

        if (value.endsWith("m")) {
            multiplier = 60_000L;
        } else if (value.endsWith("h")) {
            multiplier = 3_600_000L;
        } else if (value.endsWith("d")) {
            multiplier = 86_400_000L;
        } else if (value.endsWith("w")) {
            multiplier = 604_800_000L;
        } else {
            return -1L;
        }

        String number =
                value.substring(
                        0,
                        value.length() - 1
                );

        try {
            long amount =
                    Long.parseLong(number);

            if (amount <= 0L
                    || amount
                    > Long.MAX_VALUE / multiplier) {
                return -1L;
            }

            return amount
                    * multiplier;

        } catch (NumberFormatException exception) {
            return -1L;
        }
    }

    private static String formatTime(
            long timestamp
    ) {
        return new SimpleDateFormat(
                "dd/MM HH:mm:ss"
        ).format(
                new Date(timestamp)
        );
    }

    private static String outcomeColor(
            AuditEntry entry
    ) {
        switch (entry.getOutcome()) {
            case SUCCESS:
                return "§a";

            case DENIED:
            case CANCELLED:
                return "§e";

            case FAILED:
                return "§c";

            default:
                return "§7";
        }
    }

    private static String shortCorrelation(
            String correlation
    ) {
        if (correlation == null) {
            return "-";
        }

        return correlation.length() <= 8
                ? correlation
                : correlation.substring(
                        0,
                        8
                );
    }

    private static String compact(
            String value
    ) {
        if (value == null) {
            return "";
        }

        String compact =
                value.replace('\n', ' ')
                        .replace('\r', ' ')
                        .trim();

        return compact.length() > 180
                ? compact.substring(0, 180)
                        + "..."
                : compact;
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

        return result;
    }
}
