package me.krunsh.kfaction.services;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.core.operation.OperationResult;
import me.krunsh.kfaction.core.operation.OperationResult.Status;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionLog.LogType;
import me.krunsh.kfaction.data.FactionRole;
import me.krunsh.kfaction.managers.FPlayerManager;

/**
 * Service de transition V2 pour l'appartenance à une faction.
 *
 * Toute mutation maintient ensemble :
 * - Faction.members/memberRoles ;
 * - FPlayer.factionId/role ;
 * - l'index FPlayerManager faction -> UUID ;
 * - le dirty tracking ;
 * - les logs ;
 * - le nametag Kchat du joueur concerné.
 *
 * Faction reste temporairement la source canonique pendant la migration.
 */
public final class MembershipService {

    public enum ChangeReason {
        CREATE,
        JOIN,
        LEAVE,
        KICK,
        ADMIN_JOIN,
        ADMIN_LEAVE,
        DISBAND
    }

    private final Kfaction plugin;

    public MembershipService(Kfaction plugin) {
        this.plugin = plugin;
    }

    public OperationResult<FactionRole> join(
            Faction faction,
            UUID playerId,
            FactionRole requestedRole,
            ChangeReason reason,
            OperationContext context,
            boolean bypassMemberLimit
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return OperationResult.failure(
                    Status.UNAVAILABLE,
                    "membership.main-thread-required"
            );
        }

        if (faction == null || playerId == null || requestedRole == null) {
            return OperationResult.failure(
                    Status.INVALID_INPUT,
                    "membership.invalid-input"
            );
        }

        if (faction.isSystemFaction()) {
            return OperationResult.failure(
                    Status.FORBIDDEN,
                    "membership.system-faction"
            );
        }

        if (requestedRole == FactionRole.LEADER
                && faction.getLeader() != null
                && !playerId.equals(faction.getLeader())) {
            return OperationResult.failure(
                    Status.CONFLICT,
                    "membership.leader-already-exists"
            );
        }

        FPlayerManager playerManager = plugin.getFPlayerManager();
        FPlayer fPlayer = playerManager.find(playerId);

        if (fPlayer == null) {
            // Une vraie mutation peut créer explicitement le profil manquant.
            fPlayer = playerManager.getOrCreate(playerId);
        }

        if (fPlayer == null) {
            return OperationResult.failure(
                    Status.UNAVAILABLE,
                    "membership.player-profile-unavailable"
            );
        }

        /*
         * Réparation d'une ancienne désynchronisation :
         * Faction contient déjà le membre mais FPlayer ne pointe pas dessus.
         */
        if (faction.isMember(playerId)) {
            FactionRole canonicalRole = faction.getRole(playerId);
            if (canonicalRole == null) {
                canonicalRole = requestedRole;
                faction.setRole(playerId, canonicalRole);
            }

            String oldFactionId = fPlayer.getFactionId();
            if (!faction.getId().equals(oldFactionId)
                    || fPlayer.getRole() != canonicalRole) {
                if (oldFactionId != null
                        && !oldFactionId.equals(faction.getId())) {
                    Faction oldFaction = plugin
                            .getFactionManager()
                            .getFaction(oldFactionId);

                    if (oldFaction != null && oldFaction.isMember(playerId)) {
                        return OperationResult.failure(
                                Status.CONFLICT,
                                "membership.player-in-another-faction"
                        );
                    }
                }

                fPlayer.joinFaction(faction.getId(), canonicalRole);
                playerManager.notifyFactionChange(
                        playerId,
                        oldFactionId,
                        faction.getId()
                );
                plugin.getStorageManager().markDirty(fPlayer);
                plugin.getStorageManager().markDirty(faction);
                refreshNametag(playerId);
                refreshPermissionContexts(playerId);
            }

            return OperationResult.noChange("membership.already-member");
        }

        /*
         * Autre réparation V1 :
         * FPlayer pointe vers cette faction mais la faction ne contient plus
         * le joueur. On nettoie le miroir avant d'effectuer le vrai join.
         */
        if (fPlayer.hasFaction()
                && faction.getId().equals(fPlayer.getFactionId())) {
            String staleFactionId = fPlayer.getFactionId();
            fPlayer.leaveFaction();

            playerManager.notifyFactionChange(
                    playerId,
                    staleFactionId,
                    null
            );

            plugin.getStorageManager().markDirty(fPlayer);
        }

        if (fPlayer.hasFaction()) {
            return OperationResult.failure(
                    Status.CONFLICT,
                    "membership.player-in-another-faction"
            );
        }

        if (!bypassMemberLimit) {
            int baseLimit = plugin
                    .getConfigManager()
                    .getInt("factions.members.max-per-faction", 50);

            int effectiveLimit = Math.max(
                    1,
                    baseLimit + Math.max(0, faction.getExtraMembers())
            );

            if (faction.getMemberCount() >= effectiveLimit) {
                return OperationResult.failure(
                        Status.LIMIT_REACHED,
                        "membership.member-limit"
                );
            }
        }

        if (!faction.addMember(playerId, requestedRole)) {
            return OperationResult.failure(
                    Status.FAILED,
                    "membership.faction-add-failed"
            );
        }

        FactionRole effectiveRole = faction.getRole(playerId);
        if (effectiveRole == null) {
            // Rollback défensif.
            faction.removeMember(playerId);
            return OperationResult.failure(
                    Status.FAILED,
                    "membership.role-missing-after-add"
            );
        }

        fPlayer.joinFaction(faction.getId(), effectiveRole);
        fPlayer.clearPendingInvite(); // ancien stockage V1
        faction.removeInvite(playerId);

        playerManager.notifyFactionChange(
                playerId,
                null,
                faction.getId()
        );

        plugin.getStorageManager().markDirty(faction);
        plugin.getStorageManager().markDirty(fPlayer);

        logJoin(
                faction,
                playerId,
                effectiveRole,
                reason,
                context
        );

        refreshNametag(playerId);
        refreshPermissionContexts(playerId);

        return OperationResult.success(effectiveRole);
    }

    public OperationResult<Void> remove(
            Faction faction,
            UUID playerId,
            ChangeReason reason,
            OperationContext context,
            boolean allowLeaderRemoval
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return OperationResult.failure(
                    Status.UNAVAILABLE,
                    "membership.main-thread-required"
            );
        }

        if (faction == null || playerId == null) {
            return OperationResult.failure(
                    Status.INVALID_INPUT,
                    "membership.invalid-input"
            );
        }

        if (faction.isSystemFaction()) {
            return OperationResult.failure(
                    Status.FORBIDDEN,
                    "membership.system-faction"
            );
        }

        if (!faction.isMember(playerId)) {
            return OperationResult.failure(
                    Status.NOT_FOUND,
                    "membership.not-member"
            );
        }

        boolean wasLeader = faction.isLeader(playerId);
        if (wasLeader && !allowLeaderRemoval) {
            return OperationResult.failure(
                    Status.FORBIDDEN,
                    "membership.cannot-remove-leader"
            );
        }

        FactionRole oldRole = faction.getRole(playerId);

        if (!faction.removeMember(playerId)) {
            return OperationResult.failure(
                    Status.FAILED,
                    "membership.faction-remove-failed"
            );
        }

        FPlayerManager playerManager = plugin.getFPlayerManager();
        FPlayer fPlayer = playerManager.find(playerId);

        if (fPlayer != null
                && faction.getId().equals(fPlayer.getFactionId())) {
            String oldFactionId = fPlayer.getFactionId();
            fPlayer.leaveFaction();

            playerManager.notifyFactionChange(
                    playerId,
                    oldFactionId,
                    null
            );

            plugin.getStorageManager().markDirty(fPlayer);
        }

        /*
         * Faction.removeMember() peut promouvoir automatiquement un nouveau
         * leader si le leader a été retiré par une opération admin.
         * On resynchronise immédiatement le miroir FPlayer correspondant.
         */
        if (wasLeader && faction.getLeader() != null) {
            synchronizeCanonicalMember(
                    faction,
                    faction.getLeader()
            );
        }

        plugin.getStorageManager().markDirty(faction);

        logRemoval(
                faction,
                playerId,
                oldRole,
                reason,
                context
        );

        refreshNametag(playerId);
        refreshPermissionContexts(playerId);

        return OperationResult.success();
    }

    private void synchronizeCanonicalMember(
            Faction faction,
            UUID playerId
    ) {
        FactionRole role = faction.getRole(playerId);
        if (role == null) {
            return;
        }

        FPlayerManager manager = plugin.getFPlayerManager();
        FPlayer fPlayer = manager.find(playerId);

        if (fPlayer == null) {
            fPlayer = manager.getOrCreate(playerId);
        }

        if (fPlayer == null) {
            return;
        }

        String oldFactionId = fPlayer.getFactionId();

        fPlayer.joinFaction(faction.getId(), role);
        manager.notifyFactionChange(
                playerId,
                oldFactionId,
                faction.getId()
        );

        plugin.getStorageManager().markDirty(fPlayer);
        refreshNametag(playerId);
        refreshPermissionContexts(playerId);
    }

    private void logJoin(
            Faction faction,
            UUID targetId,
            FactionRole role,
            ChangeReason reason,
            OperationContext context
    ) {
        if (context == null) {
            return;
        }

        plugin.getLogManager().log(
                faction.getId(),
                LogType.MEMBER_JOIN,
                context.getActorId(),
                actorName(context),
                targetId,
                targetName(targetId),
                "reason=" + safeReason(reason)
                        + ";role=" + role.name()
                        + ";source=" + context.getSource().name()
                        + ";correlation=" + context.getCorrelationId()
        );
    }

    private void logRemoval(
            Faction faction,
            UUID targetId,
            FactionRole oldRole,
            ChangeReason reason,
            OperationContext context
    ) {
        if (context == null) {
            return;
        }

        LogType type = reason == ChangeReason.KICK
                ? LogType.MEMBER_KICK
                : LogType.MEMBER_LEAVE;

        plugin.getLogManager().log(
                faction.getId(),
                type,
                context.getActorId(),
                actorName(context),
                targetId,
                targetName(targetId),
                "reason=" + safeReason(reason)
                        + ";oldRole=" + (oldRole != null ? oldRole.name() : "null")
                        + ";source=" + context.getSource().name()
                        + ";correlation=" + context.getCorrelationId()
        );
    }

    private String targetName(UUID playerId) {
        FPlayer fPlayer = plugin
                .getFPlayerManager()
                .findLoaded(playerId);

        if (fPlayer != null
                && fPlayer.getLastKnownName() != null
                && !fPlayer.getLastKnownName().trim().isEmpty()) {
            return fPlayer.getLastKnownName();
        }

        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            return player.getName();
        }

        return playerId.toString();
    }

    private static String actorName(OperationContext context) {
        if (context.getActorName() != null) {
            return context.getActorName();
        }

        if (context.getActorId() != null) {
            return context.getActorId().toString();
        }

        return context.getSource().name();
    }

    private static String safeReason(ChangeReason reason) {
        return reason != null ? reason.name() : "UNKNOWN";
    }

    private void refreshPermissionContexts(
            UUID playerId
    ) {
        if (playerId == null
                || plugin.getHookManager() == null) {
            return;
        }

        plugin.getHookManager()
                .refreshPermissionContexts(
                        playerId
                );
    }

    private void refreshNametag(UUID playerId) {
        if (!plugin.getHookManager().hasKchat()) {
            return;
        }

        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            plugin.getHookManager()
                    .getKchatHook()
                    .updatePlayerNametag(player);
        }
    }
}
