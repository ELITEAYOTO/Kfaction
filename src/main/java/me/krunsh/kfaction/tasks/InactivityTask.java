package me.krunsh.kfaction.tasks;

import java.util.ArrayList;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.scheduler.BukkitRunnable;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.api.event.FactionDisbandEvent;
import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.core.operation.OperationResult;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionRole;
import me.krunsh.kfaction.services.FactionLifecycleService;
import me.krunsh.kfaction.services.RoleService;

/**
 * Vérification périodique de l'inactivité.
 *
 * IMPORTANT V2 :
 * cette tâche tourne désormais sur le thread Bukkit principal car elle
 * effectue des mutations de domaine et utilise l'API Bukkit OfflinePlayer.
 */
public class InactivityTask extends BukkitRunnable {

    private final Kfaction plugin;
    private final RoleService roleService;
    private final FactionLifecycleService lifecycleService;

    private final long leaderInactivityThreshold;
    private final long memberInactivityThreshold;
    private final boolean disbandIfNoReplacement;

    public InactivityTask(Kfaction plugin) {
        this.plugin = plugin;
        this.roleService = new RoleService(plugin);
        this.lifecycleService =
                new FactionLifecycleService(plugin);

        int leaderDays =
                plugin.getConfigManager()
                        .getInt(
                                "inactivity.leader-days",
                                14
                        );

        int memberDays =
                plugin.getConfigManager()
                        .getInt(
                                "inactivity.member-days",
                                30
                        );

        this.disbandIfNoReplacement =
                plugin.getConfigManager()
                        .getBoolean(
                                "inactivity.disband-if-no-replacement",
                                false
                        );

        this.leaderInactivityThreshold =
                leaderDays * 24L * 60L * 60L * 1000L;

        this.memberInactivityThreshold =
                memberDays * 24L * 60L * 60L * 1000L;

        plugin.getLogger().info(
                "InactivityTask initialisée - Leader: "
                        + leaderDays
                        + " jours, Membre: "
                        + memberDays
                        + " jours"
        );
    }

    @Override
    public void run() {
        if (plugin.getConfigManager()
                .getBoolean("debug", false)) {
            plugin.getLogger().info(
                    "[InactivityTask] Vérification..."
            );
        }

        long now = System.currentTimeMillis();
        int transferCount = 0;
        int disbandCount = 0;

        /*
         * Snapshot obligatoire : une dissolution retire une faction du manager
         * pendant l'itération.
         */
        for (Faction faction : new ArrayList<>(
                plugin.getFactionManager()
                        .getPlayerFactions()
        )) {
            if (faction.isPermanent()) {
                continue;
            }

            UUID leaderId = faction.getLeader();
            if (leaderId == null) {
                continue;
            }

            OfflinePlayer leader =
                    Bukkit.getOfflinePlayer(leaderId);

            long lastPlayed = leader.getLastPlayed();

            if (lastPlayed != 0L
                    && now - lastPlayed
                    <= leaderInactivityThreshold) {
                continue;
            }

            UUID newLeader =
                    findActiveReplacement(
                            faction,
                            now
                    );

            if (newLeader != null) {
                OfflinePlayer replacement =
                        Bukkit.getOfflinePlayer(newLeader);

                OperationResult<FactionRole> result =
                        roleService.transferLeadership(
                                faction,
                                newLeader,
                                OperationContext.task()
                        );

                if (result.isSuccessful()) {
                    plugin.getLogger().info(
                            "[Inactivité] Leadership de "
                                    + faction.getName()
                                    + " transféré de "
                                    + safeName(leader)
                                    + " à "
                                    + safeName(replacement)
                    );

                    transferCount++;

                    if (replacement.isOnline()
                            && replacement.getPlayer() != null) {
                        plugin.getMessageManager().send(
                                replacement.getPlayer(),
                                "inactivity.leadership-transferred",
                                "{faction}", faction.getName()
                        );
                    }
                }

                continue;
            }

            if (!disbandIfNoReplacement) {
                continue;
            }

            FactionDisbandEvent event =
                    new FactionDisbandEvent(
                            faction,
                            null,
                            FactionDisbandEvent.DisbandReason.INACTIVITY
                    );

            Bukkit.getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                plugin.debug(
                        "Disband d'inactivité annulé pour "
                                + faction.getName()
                );
                continue;
            }

            OperationResult<Integer> result =
                    lifecycleService.disband(
                            faction,
                            OperationContext.task()
                    );

            if (result.isSuccessful()) {
                plugin.getLogger().info(
                        "[Inactivité] Faction "
                                + faction.getName()
                                + " dissoute pour inactivité totale"
                );
                disbandCount++;
            }
        }

        if (plugin.getConfigManager()
                .getBoolean("debug", false)
                && (transferCount > 0
                        || disbandCount > 0)) {
            plugin.getLogger().info(
                    "[InactivityTask] Terminé - "
                            + transferCount
                            + " transferts, "
                            + disbandCount
                            + " dissolutions"
            );
        }
    }

    private UUID findActiveReplacement(
            Faction faction,
            long now
    ) {
        UUID bestCandidate = null;
        int bestPriority = -1;
        long bestLastPlayed = 0L;

        for (UUID memberId : faction.getMembers()) {
            if (memberId.equals(faction.getLeader())) {
                continue;
            }

            OfflinePlayer member =
                    Bukkit.getOfflinePlayer(memberId);

            long lastPlayed = member.getLastPlayed();

            if (lastPlayed == 0L
                    || now - lastPlayed
                    > memberInactivityThreshold) {
                continue;
            }

            FactionRole role =
                    faction.getRole(memberId);

            int priority =
                    role != null
                            ? role.getPriority()
                            : 0;

            if (priority > bestPriority
                    || (priority == bestPriority
                            && lastPlayed > bestLastPlayed)) {
                bestCandidate = memberId;
                bestPriority = priority;
                bestLastPlayed = lastPlayed;
            }
        }

        return bestCandidate;
    }

    private static String safeName(OfflinePlayer player) {
        return player.getName() != null
                ? player.getName()
                : player.getUniqueId().toString();
    }

    public void start() {
        /*
         * runTaskTimer = SYNCHRONE.
         * Ancienne version : runTaskTimerAsynchronously.
         */
        this.runTaskTimer(
                plugin,
                6000L,
                72000L
        );

        plugin.getLogger().info(
                "InactivityTask démarrée "
                        + "(vérification toutes les heures, thread principal)"
        );
    }
}
