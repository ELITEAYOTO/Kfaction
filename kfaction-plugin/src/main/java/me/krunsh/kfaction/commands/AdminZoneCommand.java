package me.krunsh.kfaction.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.core.operation.OperationResult;
import me.krunsh.kfaction.data.FLocation;
import me.krunsh.kfaction.services.ZoneService;
import me.krunsh.kfaction.zones.ZoneDefinition;

/**
 * /kf zone ...
 *
 * Administration des Global Zones V2.2.
 */
public final class AdminZoneCommand {

    private final Kfaction plugin;

    public AdminZoneCommand(
            Kfaction plugin
    ) {
        if (plugin == null) {
            throw new IllegalArgumentException(
                    "plugin cannot be null"
            );
        }

        this.plugin = plugin;
    }

    public void execute(
            CommandSender sender,
            String[] args
    ) {
        if (!hasAnyZonePermission(
                sender
        )) {
            noPermission(sender);
            return;
        }

        if (args == null
                || args.length < 2) {
            help(sender);
            return;
        }

        String action =
                args[1].toLowerCase(
                        Locale.ROOT
                );

        if ("claim".equals(action)) {
            claim(
                    sender,
                    args
            );
            return;
        }

        if ("unclaim".equals(action)
                || "clear".equals(action)) {
            unclaim(
                    sender,
                    args
            );
            return;
        }

        if ("auto".equals(action)) {
            autoClaim(
                    sender,
                    args
            );
            return;
        }

        if ("autounclaim".equals(action)
                || "autoclear".equals(action)) {
            autoUnclaim(
                    sender,
                    args
            );
            return;
        }

        if ("info".equals(action)
                || "here".equals(action)) {
            info(sender);
            return;
        }

        if ("list".equals(action)) {
            list(
                    sender,
                    args
            );
            return;
        }

        if ("reload".equals(action)) {
            reload(sender);
            return;
        }

        help(sender);
    }

    private void claim(
            CommandSender sender,
            String[] args
    ) {
        if (!has(
                sender,
                "kfaction.admin.zone.claim"
        )) {
            noPermission(sender);
            return;
        }

        Player player =
                requirePlayer(sender);

        if (player == null) {
            return;
        }

        if (args.length < 3) {
            send(
                    sender,
                    "zone-admin.usage-claim",
                    "&cUsage: &e/kf zone claim <zone>"
            );
            return;
        }

        String zoneId =
                ZoneDefinition.normalizeId(
                        args[2]
                );

        if (!zoneService()
                .hasDefinition(
                        zoneId
                )) {
            unknownZone(
                    sender,
                    args[2]
            );
            return;
        }

        FLocation location =
                new FLocation(
                        player.getLocation()
                );

        OperationResult<String> result =
                zoneService()
                        .setZone(
                                location,
                                zoneId,
                                context(player)
                        );

        if (!result.isSuccessful()) {
            failure(
                    sender,
                    result
            );
            return;
        }

        ZoneDefinition definition =
                zoneService()
                        .getDefinition(
                                zoneId
                        );

        send(
                sender,
                "zone-admin.claim-success",
                "&a✔ Zone {zone_color}{zone_name} &aassignée au chunk &e{x}&7, &e{z}&a.",
                "{zone_color}",
                definition != null
                        ? definition.getColor()
                        : "&f",
                "{zone_name}",
                definition != null
                        ? definition.getDisplayName()
                        : zoneId,
                "{x}",
                location.getX(),
                "{z}",
                location.getZ()
        );
    }

    private void unclaim(
            CommandSender sender,
            String[] args
    ) {
        if (!has(
                sender,
                "kfaction.admin.zone.unclaim"
        )) {
            noPermission(sender);
            return;
        }

        Player player =
                requirePlayer(sender);

        if (player == null) {
            return;
        }

        FLocation location =
                new FLocation(
                        player.getLocation()
                );

        String current =
                zoneService()
                        .getZoneIdAt(
                                location
                        );

        if (current == null) {
            send(
                    sender,
                    "zone-admin.no-zone-here",
                    "&7Ce chunk n'a aucune Global Zone."
            );
            return;
        }

        String expected =
                args.length >= 3
                        && !"*".equals(
                                args[2]
                        )
                        ? ZoneDefinition.normalizeId(
                                args[2]
                        )
                        : current;

        if (expected == null) {
            send(
                    sender,
                    "zone-admin.invalid-id",
                    "&cID de zone invalide."
            );
            return;
        }

        OperationResult<String> result =
                zoneService()
                        .clearZone(
                                location,
                                expected,
                                context(player)
                        );

        if (!result.isSuccessful()) {
            failure(
                    sender,
                    result
            );
            return;
        }

        send(
                sender,
                "zone-admin.unclaim-success",
                "&a✔ Zone &e{zone} &aretirée du chunk &e{x}&7, &e{z}&a.",
                "{zone}",
                current,
                "{x}",
                location.getX(),
                "{z}",
                location.getZ()
        );
    }

    private void autoClaim(
            CommandSender sender,
            String[] args
    ) {
        if (!has(
                sender,
                "kfaction.admin.zone.auto"
        )) {
            noPermission(sender);
            return;
        }

        Player player =
                requirePlayer(sender);

        if (player == null) {
            return;
        }

        if (args.length < 3) {
            send(
                    sender,
                    "zone-admin.usage-auto",
                    "&cUsage: &e/kf zone auto <zone|off>"
            );
            return;
        }

        if (isOff(
                args[2]
        )) {
            plugin.getClaimManager()
                    .stopAdminAutoClaim(
                            player.getUniqueId()
                    );

            send(
                    sender,
                    "zone-admin.auto-claim-off",
                    "&7Auto-zone claim désactivé."
            );
            return;
        }

        String zoneId =
                ZoneDefinition.normalizeId(
                        args[2]
                );

        if (!zoneService()
                .hasDefinition(
                        zoneId
                )) {
            unknownZone(
                    sender,
                    args[2]
            );
            return;
        }

        boolean enabled =
                plugin.getClaimManager()
                        .toggleAdminAutoClaim(
                                player.getUniqueId(),
                                zoneId
                        );

        if (enabled) {
            plugin.getClaimManager()
                    .stopAdminAutoUnclaim(
                            player.getUniqueId()
                    );
        }

        if (enabled) {
            send(
                    sender,
                    "zone-admin.auto-claim-on",
                    "&a✔ Auto-zone activé: &e{zone}&a. Marchez de chunk en chunk.",
                    "{zone}",
                    zoneId
            );
        } else {
            send(
                    sender,
                    "zone-admin.auto-claim-off",
                    "&7Auto-zone claim désactivé."
            );
        }
    }

    private void autoUnclaim(
            CommandSender sender,
            String[] args
    ) {
        if (!has(
                sender,
                "kfaction.admin.zone.auto"
        )) {
            noPermission(sender);
            return;
        }

        Player player =
                requirePlayer(sender);

        if (player == null) {
            return;
        }

        if (args.length < 3) {
            send(
                    sender,
                    "zone-admin.usage-autounclaim",
                    "&cUsage: &e/kf zone autounclaim <zone|off>"
            );
            return;
        }

        if (isOff(
                args[2]
        )) {
            plugin.getClaimManager()
                    .stopAdminAutoUnclaim(
                            player.getUniqueId()
                    );

            send(
                    sender,
                    "zone-admin.auto-unclaim-off",
                    "&7Auto-zone unclaim désactivé."
            );
            return;
        }

        String zoneId =
                ZoneDefinition.normalizeId(
                        args[2]
                );

        if (!zoneService()
                .isKnownOrAssignedZoneId(
                        zoneId
                )) {
            unknownZone(
                    sender,
                    args[2]
            );
            return;
        }

        boolean enabled =
                plugin.getClaimManager()
                        .toggleAdminAutoUnclaim(
                                player.getUniqueId(),
                                zoneId
                        );

        if (enabled) {
            plugin.getClaimManager()
                    .stopAdminAutoClaim(
                            player.getUniqueId()
                    );
        }

        if (enabled) {
            send(
                    sender,
                    "zone-admin.auto-unclaim-on",
                    "&c✔ Auto-zone unclaim activé: &e{zone}&c. Seuls les chunks de ce type seront retirés.",
                    "{zone}",
                    zoneId
            );
        } else {
            send(
                    sender,
                    "zone-admin.auto-unclaim-off",
                    "&7Auto-zone unclaim désactivé."
            );
        }
    }

    private void info(
            CommandSender sender
    ) {
        if (!has(
                sender,
                "kfaction.admin.zone.info"
        )) {
            noPermission(sender);
            return;
        }

        Player player =
                requirePlayer(sender);

        if (player == null) {
            return;
        }

        FLocation location =
                new FLocation(
                        player.getLocation()
                );

        String zoneId =
                zoneService()
                        .getZoneIdAt(
                                location
                        );

        if (zoneId == null) {
            sender.sendMessage(
                    "§7Chunk §f"
                            + location.getX()
                            + "§7, §f"
                            + location.getZ()
                            + " §7→ aucune Global Zone."
            );
            return;
        }

        ZoneDefinition definition =
                zoneService()
                        .getDefinition(
                                zoneId
                        );

        renderDefinition(
                sender,
                definition,
                location
        );
    }

    private void list(
            CommandSender sender,
            String[] args
    ) {
        if (!has(
                sender,
                "kfaction.admin.zone.list"
        )) {
            noPermission(sender);
            return;
        }

        if (args.length >= 3) {
            String zoneId =
                    ZoneDefinition.normalizeId(
                            args[2]
                    );

            ZoneDefinition definition =
                    zoneService()
                            .getDefinition(
                                    zoneId
                            );

            if (definition == null) {
                unknownZone(
                        sender,
                        args[2]
                );
                return;
            }

            renderDefinition(
                    sender,
                    definition,
                    null
            );
            return;
        }

        List<ZoneDefinition> definitions =
                new ArrayList<ZoneDefinition>(
                        zoneService()
                                .getDefinitionList()
                );

        Collections.sort(
                definitions,
                new Comparator<ZoneDefinition>() {
                    @Override
                    public int compare(
                            ZoneDefinition first,
                            ZoneDefinition second
                    ) {
                        return first.getId()
                                .compareToIgnoreCase(
                                        second.getId()
                                );
                    }
                }
        );

        sender.sendMessage(
                "§6§m----------§r §6Global Zones V2.2 §6§m----------"
        );

        for (ZoneDefinition definition
                : definitions) {
            sender.sendMessage(
                    color(
                            definition.getColor()
                                    + definition.getMapSymbol()
                                    + " &f"
                                    + definition.getId()
                                    + " &8- "
                                    + definition.getColor()
                                    + definition.getDisplayName()
                                    + " &8| &7chunks=&f"
                                    + zoneService()
                                            .count(
                                                    definition.getId()
                                            )
                                    + " &8| &7pvp=&f"
                                    + definition.isPvpAllowed()
                    )
            );
        }

        if (!zoneService()
                .getOrphanZoneIds()
                .isEmpty()) {
            sender.sendMessage(
                    "§c⚠ Zones persistées sans config: §e"
                            + zoneService()
                                    .getOrphanZoneIds()
            );
        }
    }

    private void reload(
            CommandSender sender
    ) {
        if (!has(
                sender,
                "kfaction.admin.zone.reload"
        )) {
            noPermission(sender);
            return;
        }

        zoneService()
                .reloadFromDisk();

        send(
                sender,
                "zone-admin.reload-success",
                "&a✔ Définitions de zones rechargées: &e{count}",
                "{count}",
                zoneService()
                        .getDefinitions()
                        .size()
        );

        if (!zoneService()
                .getConfigurationIssues()
                .isEmpty()) {
            send(
                    sender,
                    "zone-admin.reload-issues",
                    "&e⚠ {count} problème(s) de configuration détecté(s).",
                    "{count}",
                    zoneService()
                            .getConfigurationIssues()
                            .size()
            );
        }
    }

    public List<String> tabComplete(
            CommandSender sender,
            String[] args
    ) {
        List<String> result =
                new ArrayList<String>();

        if (args == null) {
            return result;
        }

        if (args.length == 2) {
            String partial =
                    lower(
                            args[1]
                    );

            for (String value : new String[] {
                    "claim",
                    "unclaim",
                    "auto",
                    "autounclaim",
                    "info",
                    "list",
                    "reload"
            }) {
                if (value.startsWith(
                        partial
                )) {
                    result.add(value);
                }
            }

            return result;
        }

        if (args.length == 3) {
            String action =
                    lower(
                            args[1]
                    );

            if ("claim".equals(action)
                    || "unclaim".equals(action)
                    || "auto".equals(action)
                    || "autounclaim".equals(action)
                    || "list".equals(action)) {
                String partial =
                        lower(
                                args[2]
                        );

                if (("auto".equals(action)
                        || "autounclaim".equals(action))
                        && "off".startsWith(
                                partial
                        )) {
                    result.add(
                            "off"
                    );
                }

                for (String zoneId
                        : zoneService()
                                .getZoneIds()) {
                    if (zoneId.startsWith(
                            partial
                    )) {
                        result.add(
                                zoneId
                        );
                    }
                }

                if ("autounclaim".equals(action)) {
                    for (String zoneId
                            : zoneService()
                                    .getOrphanZoneIds()) {
                        if (zoneId.startsWith(
                                partial
                        )
                                && !result.contains(
                                        zoneId
                                )) {
                            result.add(
                                    zoneId
                            );
                        }
                    }
                }

                Collections.sort(result);
                return result;
            }
        }

        return result;
    }

    private void renderDefinition(
            CommandSender sender,
            ZoneDefinition definition,
            FLocation location
    ) {
        if (definition == null) {
            send(
                    sender,
                    "zone-admin.definition-missing",
                    "&cDéfinition de zone absente."
            );
            return;
        }

        sender.sendMessage(
                color(
                        "§6§m----------§r "
                                + definition.getColor()
                                + definition.getDisplayName()
                                + " §6§m----------"
                )
        );

        sender.sendMessage(
                "§7ID: §f"
                        + definition.getId()
                        + " §8| §7chunks: §f"
                        + zoneService()
                                .count(
                                        definition.getId()
                                )
        );

        sender.sendMessage(
                "§7PvP: §f"
                        + definition.isPvpAllowed()
                        + " §8| §7policy: §f"
                        + definition.getDefaultPolicy()
                                .name()
                        + " §8| §7configurée: §f"
                        + definition.isConfigured()
        );

        sender.sendMessage(
                "§7ALLOW: §f"
                        + definition.getAllowedActions()
        );

        sender.sendMessage(
                "§7DENY: §f"
                        + definition.getDeniedActions()
        );

        if (location != null) {
            sender.sendMessage(
                    "§7Chunk: §f"
                            + location.getWorldName()
                            + " "
                            + location.getX()
                            + ", "
                            + location.getZ()
            );
        }
    }

    private ZoneService zoneService() {
        return plugin.getClaimManager()
                .getZoneService();
    }

    private OperationContext context(
            Player player
    ) {
        return OperationContext.admin(
                player.getUniqueId(),
                player.getName()
        );
    }

    private Player requirePlayer(
            CommandSender sender
    ) {
        if (!(sender instanceof Player)) {
            send(
                    sender,
                    "zone-admin.player-only",
                    "&cCette action nécessite un joueur en jeu."
            );
            return null;
        }

        return (Player) sender;
    }

    private boolean hasAnyZonePermission(
            CommandSender sender
    ) {
        return sender.hasPermission(
                "kfaction.admin"
        )
                || sender.hasPermission(
                        "kfaction.admin.zone"
                )
                || sender.hasPermission(
                        "kfaction.admin.zone.claim"
                )
                || sender.hasPermission(
                        "kfaction.admin.zone.unclaim"
                )
                || sender.hasPermission(
                        "kfaction.admin.zone.auto"
                )
                || sender.hasPermission(
                        "kfaction.admin.zone.info"
                )
                || sender.hasPermission(
                        "kfaction.admin.zone.list"
                )
                || sender.hasPermission(
                        "kfaction.admin.zone.reload"
                );
    }

    private boolean has(
            CommandSender sender,
            String permission
    ) {
        return sender.hasPermission(
                "kfaction.admin"
        )
                || sender.hasPermission(
                        permission
                );
    }

    private static boolean isOff(
            String value
    ) {
        String normalized =
                lower(value);

        return "off".equals(normalized)
                || "stop".equals(normalized)
                || "false".equals(normalized);
    }

    private static String lower(
            String value
    ) {
        return value != null
                ? value.toLowerCase(
                        Locale.ROOT
                )
                : "";
    }

    private void send(
            CommandSender sender,
            String path,
            String fallback,
            Object... replacements
    ) {
        String message =
                fallback;

        if (plugin.getMessageManager() != null
                && plugin.getMessageManager()
                        .has(path)) {
            message =
                    plugin.getMessageManager()
                            .get(
                                    path,
                                    replacements
                            );
        } else if (replacements != null) {
            for (int index = 0;
                    index + 1 < replacements.length;
                    index += 2) {
                message =
                        message.replace(
                                String.valueOf(
                                        replacements[index]
                                ),
                                String.valueOf(
                                        replacements[index + 1]
                                )
                        );
            }

            message =
                    color(message);
        } else {
            message =
                    color(message);
        }

        sender.sendMessage(message);
    }

    private static String color(
            String value
    ) {
        return ChatColor.translateAlternateColorCodes(
                '&',
                value
        );
    }

    private void unknownZone(
            CommandSender sender,
            String value
    ) {
        send(
                sender,
                "zone-admin.unknown-zone",
                "&cZone inconnue: &e{zone}",
                "{zone}",
                value
        );

        send(
                sender,
                "zone-admin.available-zones",
                "&7Disponibles: &f{zones}",
                "{zones}",
                zoneService()
                        .getZoneIds()
        );
    }

    private void failure(
            CommandSender sender,
            OperationResult<?> result
    ) {
        send(
                sender,
                "zone-admin.failure",
                "&cÉchec zone: &7{reason}",
                "{reason}",
                result != null
                && result.getDetail() != null
                        ? result.getDetail()
                        : result != null
                                ? result.getStatus()
                                        .name()
                                : "UNKNOWN"
        );
    }

    private void noPermission(
            CommandSender sender
    ) {
        send(
                sender,
                "zone-admin.no-permission",
                "&cTu n'as pas la permission."
        );
    }

    private static void help(
            CommandSender sender
    ) {
        sender.sendMessage(
                "§6§m----------§r §6Global Zones V2.2 §6§m----------"
        );
        sender.sendMessage(
                "§e/kf zone claim <zone> §7- assigne le chunk"
        );
        sender.sendMessage(
                "§e/kf zone unclaim [zone] §7- retire la zone"
        );
        sender.sendMessage(
                "§e/kf zone auto <zone|off> §7- assigne en marchant"
        );
        sender.sendMessage(
                "§e/kf zone autounclaim <zone|off> §7- retire en marchant"
        );
        sender.sendMessage(
                "§e/kf zone info §7- zone du chunk courant"
        );
        sender.sendMessage(
                "§e/kf zone list [zone] §7- définitions et compteurs"
        );
        sender.sendMessage(
                "§e/kf zone reload §7- recharge uniquement zones.*"
        );
    }
}
