package me.krunsh.kfaction.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.api.v2.FactionView;
import me.krunsh.kfaction.api.v2.KfactionApiV2;
import me.krunsh.kfaction.api.v2.KfactionApis;
import me.krunsh.kfaction.api.v2.MemberView;
import me.krunsh.kfaction.api.v2.PlayerView;
import me.krunsh.kfaction.diagnostics.DiagnosticCheck;
import me.krunsh.kfaction.diagnostics.DiagnosticReport;
import me.krunsh.kfaction.diagnostics.DiagnosticScope;
import me.krunsh.kfaction.diagnostics.DiagnosticService;
import me.krunsh.kfaction.diagnostics.DiagnosticSeverity;
import me.krunsh.kfaction.diagnostics.VersionSnapshot;
import me.krunsh.kfaction.progression.FactionProgressState;
import me.krunsh.kfaction.api.event.FactionDisbandEvent;
import me.krunsh.kfaction.audit.AuditCategory;
import me.krunsh.kfaction.audit.AuditOutcome;
import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.core.operation.OperationResult;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionRole;
import me.krunsh.kfaction.services.FactionLifecycleService;
import me.krunsh.kfaction.services.MembershipService;
import me.krunsh.kfaction.services.MembershipService.ChangeReason;
import me.krunsh.kfaction.services.RoleService;

/**
 * Routeur /kfaction.
 *
 * Les mutations staff critiques passent progressivement par les services V2.
 */
public class KfactionAdminCommand
        implements CommandExecutor, TabCompleter {

    private final Kfaction plugin;
    private final AdminCommand delegateCommand;

    private final RoleService roleService;
    private final MembershipService membershipService;
    private final FactionLifecycleService lifecycleService;
    private final AdminAuditCommand auditCommand;
    private final AdminZoneCommand zoneCommand;
    private final DiagnosticService diagnosticService;

    public KfactionAdminCommand(Kfaction plugin) {
        this.plugin = plugin;
        this.delegateCommand = new AdminCommand(plugin);
        this.roleService = new RoleService(plugin);
        this.membershipService =
                new MembershipService(plugin);
        this.lifecycleService =
                new FactionLifecycleService(plugin);

        this.auditCommand =
                new AdminAuditCommand(plugin);

        this.zoneCommand =
                new AdminZoneCommand(plugin);

        this.diagnosticService =
                new DiagnosticService(plugin);
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        String[] delegateArgs =
                stripLegacyAdminPrefix(args);

        boolean zoneRequest =
                delegateArgs.length > 0
                        && ("zone".equalsIgnoreCase(
                                delegateArgs[0]
                        )
                        || "zones".equalsIgnoreCase(
                                delegateArgs[0]
                        ));

        if (!sender.hasPermission("kfaction.admin")
                && !zoneRequest) {
            sender.sendMessage(
                    "§c✖ Tu n'as pas la permission d'utiliser cette commande."
            );
            return true;
        }

        if (delegateArgs.length > 0) {
            String sub =
                    delegateArgs[0].toLowerCase();

            if (plugin.getLogManager() != null) {
                plugin.getLogManager()
                        .audit(
                                adminContext(sender),
                                AuditCategory.ADMIN,
                                "ADMIN_COMMAND_"
                                        + sub.toUpperCase(
                                                java.util.Locale.ROOT
                                        ),
                                AuditOutcome.INFO,
                                null,
                                null,
                                null,
                                "label=" + label
                        );
            }

            switch (sub) {
                case "doctor":
                case "diag":
                case "diagnostics":
                    handleDoctor(
                            sender,
                            delegateArgs
                    );
                    return true;

                case "version":
                case "ver":
                case "about":
                    handleVersion(sender);
                    return true;

                case "setrole":
                    handleSetRole(sender, delegateArgs);
                    return true;

                case "forceleader":
                    handleForceLeader(sender, delegateArgs);
                    return true;

                case "forcejoin":
                    handleForceJoin(sender, delegateArgs);
                    return true;

                case "forceleave":
                    handleForceLeave(sender, delegateArgs);
                    return true;

                case "disband":
                    handleDisband(sender, delegateArgs);
                    return true;

                case "inspect":
                    handleInspectV2(
                            sender,
                            delegateArgs
                    );
                    return true;

                case "audit":
                    auditCommand.execute(
                            sender,
                            delegateArgs
                    );
                    return true;

                case "zone":
                case "zones":
                    zoneCommand.execute(
                            sender,
                            delegateArgs
                    );
                    return true;

                default:
                    break;
            }
        }

        delegateCommand.execute(
                sender,
                delegateArgs
        );

        if (delegateArgs.length == 0
                || "help".equalsIgnoreCase(delegateArgs[0])
                || "?".equals(delegateArgs[0])) {
            appendV2Help(sender);
        }

        return true;
    }

    // ============================================================
    // Diagnostics V2
    // ============================================================

    private void handleDoctor(
            CommandSender sender,
            String[] args
    ) {
        if (!sender.hasPermission("kfaction.admin.doctor")) {
            send(sender, "general.no-permission");
            return;
        }

        DiagnosticScope scope =
                DiagnosticScope.ALL;

        boolean full = false;

        for (int index = 1;
                index < args.length;
                index++) {
            String token =
                    args[index];

            if ("full".equalsIgnoreCase(token)
                    || "deep".equalsIgnoreCase(token)
                    || "complet".equalsIgnoreCase(token)) {
                full = true;
                continue;
            }

            DiagnosticScope parsed =
                    DiagnosticScope.parse(token);

            if (parsed == null) {
                sender.sendMessage(
                        "§cScope invalide: §e"
                                + token
                );
                sender.sendMessage(
                        "§7Scopes: all, runtime, storage, audit, integrations, indexes, progression, zones"
                );
                return;
            }

            scope = parsed;
        }

        DiagnosticReport report =
                diagnosticService.run(
                        scope,
                        full
                );

        renderDoctor(
                sender,
                report
        );
    }

    private void renderDoctor(
            CommandSender sender,
            DiagnosticReport report
    ) {
        sender.sendMessage(
                "§6§m----------§r §6Kfaction Doctor V2 §6§m----------"
        );

        sender.sendMessage(
                "§7Scope: §f"
                        + report.getScope().name()
                        + " §8| §7Mode: §f"
                        + (report.isFull()
                                ? "FULL"
                                : "SAFE")
        );

        for (DiagnosticCheck check
                : report.getChecks()) {
            if (check == null) {
                continue;
            }

            sender.sendMessage(
                    severityPrefix(
                            check.getSeverity()
                    )
                            + " §f"
                            + check.getTitle()
                            + (check.hasDetail()
                                    ? " §8- §7"
                                            + check.getDetail()
                                    : "")
            );

            if (check.hasSuggestion()
                    && (report.isFull()
                            || check.getSeverity()
                                    .isProblem())) {
                sender.sendMessage(
                        "   §8↳ §e"
                                + check.getSuggestion()
                );
            }
        }

        sender.sendMessage("");

        sender.sendMessage(
                "§7Résumé: "
                        + "§a"
                        + report.count(
                                DiagnosticSeverity.OK
                        )
                        + " OK "
                        + "§b"
                        + report.count(
                                DiagnosticSeverity.INFO
                        )
                        + " INFO "
                        + "§e"
                        + report.count(
                                DiagnosticSeverity.WARNING
                        )
                        + " WARN "
                        + "§c"
                        + report.count(
                                DiagnosticSeverity.ERROR
                        )
                        + " ERROR"
        );

        sender.sendMessage(
                "§7Durée: §f"
                        + report.getDurationMillis()
                        + " ms §8| §7État global: "
                        + severityPrefix(
                                report.getOverallSeverity()
                        )
                        + " "
                        + report.getOverallSeverity()
                                .name()
        );

        sender.sendMessage(
                "§6§m----------------------------------------"
        );
    }

    private void handleVersion(
            CommandSender sender
    ) {
        if (!sender.hasPermission("kfaction.admin.version")) {
            send(sender, "general.no-permission");
            return;
        }

        VersionSnapshot version =
                diagnosticService.captureVersion();

        sender.sendMessage(
                "§6§m----------§r §6Kfaction Version §6§m----------"
        );

        sender.sendMessage(
                "§7Plugin: §f"
                        + version.getPluginVersion()
                        + " §8| §7API: §f"
                        + version.getApiVersion()
        );

        sender.sendMessage(
                "§7Java: §f"
                        + version.getJavaVersion()
        );

        sender.sendMessage(
                "§7Serveur: §f"
                        + version.getServerVersion()
        );

        sender.sendMessage(
                "§7Storage: §f"
                        + version.getStorageType()
                        + " §8| §7connecté: "
                        + (version.isStorageConnected()
                                ? "§aoui"
                                : "§cnon")
        );

        sender.sendMessage(
                "§7Schemas: §fpayload="
                        + version.getStoragePayloadSchema()
                        + "§7, db=§f"
                        + (version.getStorageDatabaseSchema() >= 0
                                ? version.getStorageDatabaseSchema()
                                : "n/a")
        );

        sender.sendMessage(
                "§7Mémoire: §f"
                        + formatBytes(
                                version.getUsedMemoryBytes()
                        )
                        + " / "
                        + formatBytes(
                                version.getMaxMemoryBytes()
                        )
        );

        sender.sendMessage(
                "§7Uptime JVM: §f"
                        + formatDuration(
                                version.getUptimeMillis()
                        )
        );

        sender.sendMessage(
                "§7Data: §f"
                        + version.getDataFolder()
        );

        sender.sendMessage(
                "§6§m---------------------------------------"
        );
    }

    private void handleInspectV2(
            CommandSender sender,
            String[] args
    ) {
        if (!sender.hasPermission("kfaction.admin.inspect")) {
            send(sender, "general.no-permission");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(
                    "§c/kfaction inspect <faction|joueur>"
            );
            return;
        }

        KfactionApiV2 api =
                KfactionApis.get();

        if (api == null) {
            sender.sendMessage(
                    "§cAPI V2 indisponible. Utilise §e/kf doctor runtime§c."
            );
            return;
        }

        FactionView faction =
                api.findFaction(
                        args[1]
                );

        if (faction != null) {
            inspectFactionV2(
                    sender,
                    api,
                    faction
            );
            return;
        }

        OfflinePlayer target =
                Bukkit.getOfflinePlayer(
                        args[1]
                );

        PlayerView player =
                api.getPlayer(
                        target.getUniqueId()
                );

        if (player == null) {
            sender.sendMessage(
                    "§cAucun profil Kfaction existant pour §e"
                            + safeName(target)
                            + "§c. Aucun profil n'a été créé."
            );
            return;
        }

        inspectPlayerV2(
                sender,
                api,
                target,
                player
        );
    }

    private void inspectFactionV2(
            CommandSender sender,
            KfactionApiV2 api,
            FactionView faction
    ) {
        sender.sendMessage(
                "§6=== Inspection Faction V2: §e"
                        + faction.getName()
                        + " §6==="
        );

        sender.sendMessage(
                "§7ID / Tag: §f"
                        + faction.getId()
                        + " §8/ §f"
                        + faction.getTag()
        );

        sender.sendMessage(
                "§7Leader: §f"
                        + resolveLeaderName(
                                faction
                        )
        );

        sender.sendMessage(
                "§7Membres: §f"
                        + faction.getMemberCount()
                        + " §8(§a"
                        + faction.getOnlineMemberCount()
                        + " online§8) §7/ max §f"
                        + faction.getMaxMembers()
        );

        sender.sendMessage(
                "§7Claims: §f"
                        + faction.getClaimCount()
                        + " §7/ max §f"
                        + faction.getMaxClaims()
                        + " §8| §7groups: §f"
                        + faction.getClaimGroupCount()
        );

        sender.sendMessage(
                "§7Power: §f"
                        + String.format(
                                java.util.Locale.US,
                                "%.1f / %.1f",
                                faction.getPower(),
                                faction.getMaxPower()
                        )
        );

        sender.sendMessage(
                "§7Banque: §f"
                        + String.format(
                                java.util.Locale.US,
                                "%.2f",
                                faction.getBankBalance()
                        )
                        + " §8(§7minor="
                        + faction.getBankMinor()
                        + "§8)"
        );

        sender.sendMessage(
                "§7Warps: §f"
                        + faction.getWarpCount()
                        + " §7/ max §f"
                        + faction.getMaxWarps()
        );

        sender.sendMessage(
                "§7Relations: §d"
                        + faction.getAllyCount()
                        + " ally §8| §c"
                        + faction.getEnemyCount()
                        + " enemy §8| §e"
                        + faction.getTruceCount()
                        + " truce"
        );

        sender.sendMessage(
                "§7Flags: "
                        + "raidable="
                        + boolColor(
                                faction.isRaidable()
                        )
                        + " chest="
                        + boolColor(
                                faction.isChestUnlocked()
                        )
                        + " fly="
                        + boolColor(
                                faction.isFactionFlyEnabled()
                        )
                        + " antisethome="
                        + boolColor(
                                faction.isAntiSethomeEnabled()
                        )
        );

        Faction liveFaction =
                plugin.getFactionManager()
                        .getFaction(
                                faction.getId()
                        );

        if (liveFaction != null
                && liveFaction.getProgressionState() != null) {
            FactionProgressState progression =
                    liveFaction.getProgressionState();

            sender.sendMessage(
                    "§7Progression raw: §flevel="
                            + liveFaction.getLevel()
                            + " §8| §7started=§f"
                            + progression.getLevelStarted()
                            + " §8| §7schema=§f"
                            + progression.getSchemaVersion()
                            + "/"
                            + FactionProgressState.CURRENT_SCHEMA_VERSION
            );

            if (!progression.getPendingRewards()
                    .isEmpty()) {
                sender.sendMessage(
                        "§ePending rewards: §f"
                                + progression.getPendingRewards()
                );
            }

            if (progression.getPendingTransition() != null) {
                sender.sendMessage(
                        "§ePending transition: §f"
                                + progression.getPendingTransition()
                );
            }
        }

        sender.sendMessage(
                "§7Créée: §f"
                        + formatTimestamp(
                                faction.getCreatedAt()
                        )
                        + " §8| §7activité: §f"
                        + formatTimestamp(
                                faction.getLastActivity()
                        )
        );
    }

    private void inspectPlayerV2(
            CommandSender sender,
            KfactionApiV2 api,
            OfflinePlayer target,
            PlayerView player
    ) {
        sender.sendMessage(
                "§6=== Inspection Joueur V2: §e"
                        + safeName(target)
                        + " §6==="
        );

        sender.sendMessage(
                "§7UUID: §f"
                        + player.getUuid()
        );

        sender.sendMessage(
                "§7En ligne: "
                        + boolColor(
                                player.isOnline()
                        )
        );

        sender.sendMessage(
                "§7Faction: §f"
                        + (player.hasFaction()
                                ? player.getFactionId()
                                : "aucune")
        );

        sender.sendMessage(
                "§7Rôle: §f"
                        + (player.getRoleDisplayName() != null
                                ? player.getRoleDisplayName()
                                : "aucun")
                        + " §8("
                        + (player.getRole() != null
                                ? player.getRole()
                                : "none")
                        + "§8)"
        );

        sender.sendMessage(
                "§7Power: §f"
                        + String.format(
                                java.util.Locale.US,
                                "%.1f / %.1f",
                                player.getPower(),
                                player.getMaxPower()
                        )
        );

        sender.sendMessage(
                "§7K/D: §f"
                        + player.getKills()
                        + "/"
                        + player.getDeaths()
                        + " §8| §7chat=§f"
                        + (player.getChatMode() != null
                                ? player.getChatMode()
                                : "PUBLIC")
        );

        sender.sendMessage(
                "§7Flags: bypass="
                        + boolColor(
                                player.isBypassing()
                        )
                        + " mapAuto="
                        + boolColor(
                                player.isMapAutoEnabled()
                        )
        );

        if (player.hasFaction()) {
            FactionView faction =
                    api.getPlayerFaction(
                            player.getUuid()
                    );

            if (faction != null) {
                sender.sendMessage(
                        "§7Faction résolue: §e"
                                + faction.getName()
                                + " §8["
                                + faction.getTag()
                                + "§8]"
                );
            } else {
                sender.sendMessage(
                        "§c⚠ factionId présent mais faction introuvable via API V2."
                );
            }
        }

        sender.sendMessage(
                "§7First join: §f"
                        + formatTimestamp(
                                player.getFirstJoin()
                        )
                        + " §8| §7last seen: §f"
                        + formatTimestamp(
                                player.getLastSeen()
                        )
        );
    }

    private void appendV2Help(
            CommandSender sender
    ) {
        sender.sendMessage("");
        sender.sendMessage(
                "§6§lDiagnostics V2:"
        );
        sender.sendMessage(
                "§e/kf doctor [scope] [full] §7- diagnostic read-only"
        );
        sender.sendMessage(
                "§e/kf version §7- versions/runtime/storage"
        );
        sender.sendMessage(
                "§e/kf inspect <faction|joueur> §7- inspection API V2"
        );
        sender.sendMessage(
                "§e/kf zone ... §7- Global Zones dynamiques V2.2"
        );
    }

    private void handleSetRole(
            CommandSender sender,
            String[] args
    ) {
        if (!sender.hasPermission(
                "kfaction.admin.setrole"
        )) {
            send(sender, "general.no-permission");
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(
                    "§c/kfaction setrole <joueur> <role>"
            );
            sender.sendMessage(
                    "§6Rôles: RECRUIT, MEMBER, OFFICER, "
                            + "MODERATOR, COLEADER, LEADER"
            );
            return;
        }

        OfflinePlayer target =
                Bukkit.getOfflinePlayer(args[1]);

        FPlayer fPlayer =
                plugin.getFPlayerManager()
                        .find(target.getUniqueId());

        if (fPlayer == null || !fPlayer.hasFaction()) {
            send(sender, "admin.player-no-faction");
            return;
        }

        Faction faction =
                plugin.getFactionManager()
                        .getFaction(fPlayer.getFactionId());

        if (faction == null) {
            send(sender, "general.error");
            return;
        }

        FactionRole role =
                FactionRole.parse(args[2]);

        if (role == null) {
            sender.sendMessage(
                    "§cRôle invalide. Rôles valides: "
                            + "RECRUIT, MEMBER, OFFICER, MODERATOR, "
                            + "COLEADER, LEADER"
            );
            return;
        }

        OperationResult<FactionRole> result =
                role == FactionRole.LEADER
                        ? roleService.transferLeadership(
                                faction,
                                target.getUniqueId(),
                                adminContext(sender)
                        )
                        : roleService.setRole(
                                faction,
                                target.getUniqueId(),
                                role,
                                adminContext(sender)
                        );

        if (!result.isSuccessful()) {
            sender.sendMessage(
                    "§cImpossible de modifier le rôle: "
                            + result.getStatus().name()
                            + formatDetail(result)
            );
            return;
        }

        send(
                sender,
                "admin.setrole-success",
                "{player}", safeName(target),
                "{role}", role.getDisplayName()
        );
    }

    private void handleForceLeader(
            CommandSender sender,
            String[] args
    ) {
        if (!sender.hasPermission(
                "kfaction.admin.forceleader"
        )) {
            send(sender, "general.no-permission");
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(
                    "§c/kfaction forceleader <faction> <joueur>"
            );
            return;
        }

        Faction faction =
                plugin.getFactionManager()
                        .getFactionByName(args[1]);

        if (faction == null) {
            send(sender, "admin.faction-not-found");
            return;
        }

        OfflinePlayer target =
                Bukkit.getOfflinePlayer(args[2]);

        if (!faction.isMember(target.getUniqueId())) {
            send(sender, "admin.player-not-member");
            return;
        }

        OperationResult<FactionRole> result =
                roleService.transferLeadership(
                        faction,
                        target.getUniqueId(),
                        adminContext(sender)
                );

        if (!result.isSuccessful()) {
            sender.sendMessage(
                    "§cImpossible de transférer le leadership: "
                            + result.getStatus().name()
                            + formatDetail(result)
            );
            return;
        }

        send(
                sender,
                "admin.forceleader-success",
                "{player}", safeName(target),
                "{faction}", faction.getName()
        );
    }

    /**
     * /kfaction forcejoin <joueur> <faction>
     *
     * Le déplacement entre deux factions est prévalidé avant suppression.
     * L'ancienne faction n'est dissoute qu'après le join réussi.
     */
    private void handleForceJoin(
            CommandSender sender,
            String[] args
    ) {
        if (!sender.hasPermission(
                "kfaction.admin.forcejoin"
        )) {
            send(sender, "general.no-permission");
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(
                    "§c/kfaction forcejoin <joueur> <faction>"
            );
            return;
        }

        OfflinePlayer target =
                Bukkit.getOfflinePlayer(args[1]);

        Faction targetFaction =
                plugin.getFactionManager()
                        .getFactionByName(args[2]);

        if (targetFaction == null
                || targetFaction.isSystemFaction()) {
            send(sender, "admin.faction-not-found");
            return;
        }

        OperationContext context =
                adminContext(sender);

        FPlayer fPlayer =
                plugin.getFPlayerManager()
                        .find(target.getUniqueId());

        if (fPlayer == null) {
            fPlayer = plugin
                    .getFPlayerManager()
                    .getOrCreate(target.getUniqueId());
        }

        if (fPlayer == null) {
            send(sender, "admin.player-not-found");
            return;
        }

        String oldFactionId = fPlayer.getFactionId();

        if (targetFaction.getId()
                .equals(oldFactionId)
                && targetFaction.isMember(
                        target.getUniqueId()
                )) {
            send(
                    sender,
                    "admin.forcejoin-success",
                    "{player}", safeName(target),
                    "{faction}", targetFaction.getName()
            );
            return;
        }

        Faction oldFaction =
                oldFactionId != null
                        ? plugin.getFactionManager()
                                .getFaction(oldFactionId)
                        : null;

        FactionRole oldRole =
                oldFaction != null
                        ? oldFaction.getRole(
                                target.getUniqueId()
                        )
                        : null;

        /*
         * Réparer un ancien FPlayer pointant vers une faction inexistante.
         */
        if (oldFactionId != null && oldFaction == null) {
            fPlayer.leaveFaction();
            plugin.getFPlayerManager()
                    .notifyFactionChange(
                            target.getUniqueId(),
                            oldFactionId,
                            null
                    );
            plugin.getStorageManager()
                    .markDirty(fPlayer);
        }

        if (oldFaction != null) {
            OperationResult<Void> removeResult =
                    membershipService.remove(
                            oldFaction,
                            target.getUniqueId(),
                            ChangeReason.ADMIN_LEAVE,
                            context,
                            true
                    );

            if (!removeResult.isSuccessful()) {
                sender.sendMessage(
                        "§cImpossible de retirer le joueur "
                                + "de son ancienne faction: "
                                + removeResult.getStatus().name()
                );
                return;
            }
        }

        OperationResult<FactionRole> joinResult =
                membershipService.join(
                        targetFaction,
                        target.getUniqueId(),
                        FactionRole.RECRUIT,
                        ChangeReason.ADMIN_JOIN,
                        context,
                        true
                );

        if (!joinResult.isSuccessful()) {
            /*
             * Rollback best-effort vers l'ancienne faction si celle-ci
             * existait encore.
             */
            if (oldFaction != null && oldRole != null) {
                membershipService.join(
                        oldFaction,
                        target.getUniqueId(),
                        oldRole,
                        ChangeReason.ADMIN_JOIN,
                        context,
                        true
                );
            }

            sender.sendMessage(
                    "§cForcejoin échoué: "
                            + joinResult.getStatus().name()
                            + formatDetail(joinResult)
            );
            return;
        }

        if (oldFaction != null
                && oldFaction.getMemberCount() == 0) {
            lifecycleService.disband(
                    oldFaction,
                    context
            );
        }

        send(
                sender,
                "admin.forcejoin-success",
                "{player}", safeName(target),
                "{faction}", targetFaction.getName()
        );
    }

    private void handleForceLeave(
            CommandSender sender,
            String[] args
    ) {
        if (!sender.hasPermission(
                "kfaction.admin.forceleave"
        )) {
            send(sender, "general.no-permission");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(
                    "§c/kfaction forceleave <joueur>"
            );
            return;
        }

        OfflinePlayer target =
                Bukkit.getOfflinePlayer(args[1]);

        FPlayer fPlayer =
                plugin.getFPlayerManager()
                        .find(target.getUniqueId());

        if (fPlayer == null || !fPlayer.hasFaction()) {
            send(sender, "admin.player-no-faction");
            return;
        }

        Faction faction =
                plugin.getFactionManager()
                        .getFaction(fPlayer.getFactionId());

        if (faction == null) {
            /*
             * Réparation d'une référence fantôme.
             */
            String oldFactionId = fPlayer.getFactionId();
            fPlayer.leaveFaction();

            plugin.getFPlayerManager()
                    .notifyFactionChange(
                            target.getUniqueId(),
                            oldFactionId,
                            null
                    );

            plugin.getStorageManager()
                    .markDirty(fPlayer);

            sender.sendMessage(
                    "§eRéférence de faction inexistante nettoyée pour §f"
                            + safeName(target)
                            + "§e."
            );
            return;
        }

        String factionName = faction.getName();

        OperationResult<Void> result =
                membershipService.remove(
                        faction,
                        target.getUniqueId(),
                        ChangeReason.ADMIN_LEAVE,
                        adminContext(sender),
                        true
                );

        if (!result.isSuccessful()) {
            sender.sendMessage(
                    "§cForceleave échoué: "
                            + result.getStatus().name()
                            + formatDetail(result)
            );
            return;
        }

        if (faction.getMemberCount() == 0) {
            lifecycleService.disband(
                    faction,
                    adminContext(sender)
            );
        }

        send(
                sender,
                "admin.forceleave-success",
                "{player}", safeName(target),
                "{faction}", factionName
        );
    }

    private void handleDisband(
            CommandSender sender,
            String[] args
    ) {
        if (!sender.hasPermission(
                "kfaction.admin.disband"
        )) {
            send(sender, "general.no-permission");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(
                    "§c/kfaction disband <faction>"
            );
            return;
        }

        Faction faction =
                plugin.getFactionManager()
                        .getFactionByName(args[1]);

        if (faction == null) {
            send(sender, "admin.faction-not-found");
            return;
        }

        if (faction.isSystemFaction()) {
            send(sender, "admin.cannot-disband-system");
            return;
        }

        Player disbander =
                sender instanceof Player
                        ? (Player) sender
                        : null;

        FactionDisbandEvent event =
                new FactionDisbandEvent(
                        faction,
                        disbander,
                        FactionDisbandEvent.DisbandReason.ADMIN
                );

        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            sender.sendMessage(
                    "§cLa dissolution a été annulée par un autre plugin."
            );
            return;
        }

        String name = faction.getName();
        int memberCount = faction.getMemberCount();
        int claimCount = faction.getClaimCount();

        OperationResult<Integer> result =
                lifecycleService.disband(
                        faction,
                        adminContext(sender)
                );

        if (!result.isSuccessful()) {
            sender.sendMessage(
                    "§cDisband admin échoué: "
                            + result.getStatus().name()
                            + formatDetail(result)
            );
            return;
        }

        send(
                sender,
                "admin.disband-success",
                "{faction}", name,
                "{members}", String.valueOf(memberCount),
                "{claims}", String.valueOf(claimCount)
        );
    }

    /**
     * Empêche inspect d'un joueur inconnu de créer un profil fantôme.
     * Une cible faction reste déléguée à AdminCommand.
     */
    private static String resolveLeaderName(
            FactionView faction
    ) {
        if (faction == null
                || faction.getLeader() == null) {
            return "aucun";
        }

        UUID leader =
                faction.getLeader();

        for (MemberView member
                : faction.getMembers()) {
            if (member != null
                    && leader.equals(
                            member.getUuid()
                    )) {
                return member.getName() != null
                        ? member.getName()
                        : leader.toString();
            }
        }

        return leader.toString();
    }

    private static String severityPrefix(
            DiagnosticSeverity severity
    ) {
        if (severity == null) {
            return "§7?";
        }

        switch (severity) {
            case OK:
                return "§a✔";

            case INFO:
                return "§bℹ";

            case WARNING:
                return "§e⚠";

            case ERROR:
            default:
                return "§c✖";
        }
    }

    private static String boolColor(
            boolean value
    ) {
        return value
                ? "§aoui§7"
                : "§cnon§7";
    }

    private static String formatBytes(
            long bytes
    ) {
        if (bytes < 1024L) {
            return bytes + " B";
        }

        double kib =
                bytes / 1024.0D;

        if (kib < 1024.0D) {
            return String.format(
                    java.util.Locale.US,
                    "%.1f KiB",
                    kib
            );
        }

        double mib =
                kib / 1024.0D;

        if (mib < 1024.0D) {
            return String.format(
                    java.util.Locale.US,
                    "%.1f MiB",
                    mib
            );
        }

        return String.format(
                java.util.Locale.US,
                "%.2f GiB",
                mib / 1024.0D
        );
    }

    private static String formatDuration(
            long millis
    ) {
        long seconds =
                Math.max(
                        0L,
                        millis / 1000L
                );

        long days =
                seconds / 86400L;

        seconds %= 86400L;

        long hours =
                seconds / 3600L;

        seconds %= 3600L;

        long minutes =
                seconds / 60L;

        seconds %= 60L;

        StringBuilder result =
                new StringBuilder();

        if (days > 0L) {
            result.append(days)
                    .append("d ");
        }

        if (hours > 0L
                || days > 0L) {
            result.append(hours)
                    .append("h ");
        }

        if (minutes > 0L
                || hours > 0L
                || days > 0L) {
            result.append(minutes)
                    .append("m ");
        }

        result.append(seconds)
                .append("s");

        return result.toString();
    }

    private static String formatTimestamp(
            long timestamp
    ) {
        if (timestamp <= 0L) {
            return "n/a";
        }

        return new java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss"
        ).format(
                new java.util.Date(
                        timestamp
                )
        );
    }

    private OperationContext adminContext(
            CommandSender sender
    ) {
        if (sender instanceof Player) {
            Player player = (Player) sender;

            return OperationContext.admin(
                    player.getUniqueId(),
                    player.getName()
            );
        }

        return OperationContext.admin(
                sender.getName()
        );
    }

    private String[] stripLegacyAdminPrefix(
            String[] args
    ) {
        if (args.length > 0
                && args[0].equalsIgnoreCase("admin")) {
            String[] stripped =
                    new String[args.length - 1];

            System.arraycopy(
                    args,
                    1,
                    stripped,
                    0,
                    stripped.length
            );

            return stripped;
        }

        return args;
    }

    private void send(
            CommandSender sender,
            String key,
            Object... replacements
    ) {
        sender.sendMessage(
                plugin.getMessageManager().get(
                        key,
                        replacements
                )
        );
    }

    private static String safeName(
            OfflinePlayer player
    ) {
        String name = player.getName();

        return name != null
                ? name
                : player.getUniqueId().toString();
    }

    private static String formatDetail(
            OperationResult<?> result
    ) {
        if (result.getDetail() != null) {
            return " (" + result.getDetail() + ")";
        }

        if (result.getMessageKey() != null) {
            return " [" + result.getMessageKey() + "]";
        }

        return "";
    }

    private boolean hasAnyZonePermission(
            CommandSender sender
    ) {
        return sender.hasPermission(
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

    // ============================================================
    // Tab completion
    // ============================================================

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {
        boolean fullAdmin =
                sender.hasPermission(
                        "kfaction.admin"
                );

        boolean zonePermission =
                hasAnyZonePermission(
                        sender
                );

        if (!fullAdmin
                && !zonePermission) {
            return new ArrayList<String>();
        }

        String[] delegateArgs =
                stripLegacyAdminPrefix(args);

        if (!fullAdmin) {
            if (delegateArgs.length == 1) {
                List<String> result =
                        new ArrayList<String>();

                addIfMatches(
                        result,
                        delegateArgs[0],
                        "zone"
                );

                return result;
            }

            if (delegateArgs.length > 1
                    && ("zone".equalsIgnoreCase(
                            delegateArgs[0]
                    )
                    || "zones".equalsIgnoreCase(
                            delegateArgs[0]
                    ))) {
                return zoneCommand.tabComplete(
                        sender,
                        delegateArgs
                );
            }

            return new ArrayList<String>();
        }

        if (delegateArgs.length > 0) {
            String sub =
                    delegateArgs[0].toLowerCase();

            if ("doctor".equals(sub)
                    || "diag".equals(sub)
                    || "diagnostics".equals(sub)) {
                List<String> result =
                        completeDoctor(
                                delegateArgs
                        );

                if (result != null) {
                    return result;
                }
            }

            if ("setrole".equals(sub)) {
                List<String> result =
                        completeSetRole(delegateArgs);

                if (result != null) {
                    return result;
                }
            }

            if ("forceleader".equals(sub)) {
                List<String> result =
                        completeForceLeader(delegateArgs);

                if (result != null) {
                    return result;
                }
            }

            if ("forcejoin".equals(sub)) {
                List<String> result =
                        completeForceJoin(delegateArgs);

                if (result != null) {
                    return result;
                }
            }

            if ("forceleave".equals(sub)
                    && delegateArgs.length == 2) {
                return completeKnownPlayers(
                        delegateArgs[1]
                );
            }

            if ("audit".equals(sub)) {
                return auditCommand.tabComplete(
                        sender,
                        delegateArgs
                );
            }

            if ("zone".equals(sub)
                    || "zones".equals(sub)) {
                return zoneCommand.tabComplete(
                        sender,
                        delegateArgs
                );
            }
        }

        if (delegateArgs.length == 1) {
            List<String> delegated =
                    delegateCommand.tabComplete(
                            sender,
                            delegateArgs
                    );

            List<String> result =
                    delegated != null
                            ? new ArrayList<String>(
                                    delegated
                            )
                            : new ArrayList<String>();

            addIfMatches(
                    result,
                    delegateArgs[0],
                    "doctor"
            );

            addIfMatches(
                    result,
                    delegateArgs[0],
                    "version"
            );

            addIfMatches(
                    result,
                    delegateArgs[0],
                    "audit"
            );

            addIfMatches(
                    result,
                    delegateArgs[0],
                    "zone"
            );

            return result;
        }

        List<String> delegated =
                delegateCommand.tabComplete(
                        sender,
                        delegateArgs
                );

        return delegated != null
                ? delegated
                : new ArrayList<String>();
    }

    private List<String> completeDoctor(
            String[] args
    ) {
        if (args.length == 2) {
            List<String> result =
                    new ArrayList<String>();

            for (String value : new String[] {
                    "all",
                    "runtime",
                    "storage",
                    "audit",
                    "integrations",
                    "indexes",
                    "progression",
                    "zones",
                    "full"
            }) {
                addIfMatches(
                        result,
                        args[1],
                        value
                );
            }

            return result;
        }

        if (args.length == 3) {
            List<String> result =
                    new ArrayList<String>();

            addIfMatches(
                    result,
                    args[2],
                    "full"
            );

            return result;
        }

        return null;
    }

    private static void addIfMatches(
            List<String> result,
            String input,
            String candidate
    ) {
        if (result == null
                || candidate == null) {
            return;
        }

        String prefix =
                input != null
                        ? input.toLowerCase()
                        : "";

        if (candidate.toLowerCase()
                .startsWith(prefix)
                && !result.contains(candidate)) {
            result.add(candidate);
        }
    }

    private List<String> completeSetRole(
            String[] args
    ) {
        if (args.length == 2) {
            return completeKnownPlayers(args[1]);
        }

        if (args.length == 3) {
            String input = args[2].toLowerCase();
            List<String> result = new ArrayList<>();

            for (FactionRole role
                    : FactionRole.values()) {
                String name =
                        role.name().toLowerCase();

                if (name.startsWith(input)) {
                    result.add(role.name());
                }
            }

            return result;
        }

        return null;
    }

    private List<String> completeForceLeader(
            String[] args
    ) {
        if (args.length == 2) {
            return completeFactions(args[1]);
        }

        if (args.length == 3) {
            Faction faction =
                    plugin.getFactionManager()
                            .getFactionByName(args[1]);

            if (faction == null) {
                return new ArrayList<>();
            }

            String input = args[2].toLowerCase();
            List<String> result = new ArrayList<>();

            for (UUID memberId : faction.getMembers()) {
                FPlayer fPlayer =
                        plugin.getFPlayerManager()
                                .findLoaded(memberId);

                String name =
                        fPlayer != null
                                ? fPlayer.getLastKnownName()
                                : null;

                if (name == null || name.isEmpty()) {
                    OfflinePlayer offline =
                            Bukkit.getOfflinePlayer(memberId);
                    name = offline.getName();
                }

                if (name != null
                        && name.toLowerCase()
                                .startsWith(input)) {
                    result.add(name);
                }
            }

            return result;
        }

        return null;
    }

    private List<String> completeForceJoin(
            String[] args
    ) {
        if (args.length == 2) {
            return completeKnownPlayers(args[1]);
        }

        if (args.length == 3) {
            return completeFactions(args[2]);
        }

        return null;
    }

    private List<String> completeKnownPlayers(
            String prefix
    ) {
        String input = prefix.toLowerCase();
        List<String> result = new ArrayList<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName()
                    .toLowerCase()
                    .startsWith(input)) {
                result.add(player.getName());
            }
        }

        for (FPlayer fPlayer
                : plugin.getFPlayerManager()
                        .getAllPlayers()) {
            String name = fPlayer.getLastKnownName();

            if (name != null
                    && !name.isEmpty()
                    && name.toLowerCase()
                            .startsWith(input)
                    && !result.contains(name)) {
                result.add(name);
            }
        }

        return result;
    }

    private List<String> completeFactions(
            String prefix
    ) {
        String input = prefix.toLowerCase();
        List<String> result = new ArrayList<>();

        for (Faction faction
                : plugin.getFactionManager()
                        .getPlayerFactions()) {
            if (faction.getName()
                    .toLowerCase()
                    .startsWith(input)) {
                result.add(faction.getName());
            }
        }

        return result;
    }
}
