package me.krunsh.kfaction.services;

import java.util.UUID;

import org.bukkit.Bukkit;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.core.operation.OperationResult;
import me.krunsh.kfaction.core.operation.OperationResult.Status;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionLog.LogType;
import me.krunsh.kfaction.data.FactionRole;

/**
 * Service de transition V2 pour toutes les mutations de rang.
 *
 * Tant que Faction et FPlayer stockent tous les deux le rôle, ce service
 * garantit qu'ils restent synchronisés.
 *
 * À terme, le nouveau Domain Model supprimera cette double source de vérité.
 */
public final class RoleService {

    private final Kfaction plugin;

    public RoleService(Kfaction plugin) {
        this.plugin = plugin;
    }

    public OperationResult<FactionRole> promote(
            Faction faction,
            UUID targetId,
            OperationContext context
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return OperationResult.failure(
                    Status.UNAVAILABLE,
                    "role.main-thread-required"
            );
        }

        OperationResult<FactionRole> validation = validateTarget(faction, targetId);
        if (validation != null) {
            return validation;
        }

        FactionRole oldRole = faction.getRole(targetId);
        if (oldRole == null) {
            return OperationResult.failure(
                    Status.CONFLICT,
                    "role.missing-current-role"
            );
        }

        if (!oldRole.canBePromotedNormally()) {
            return OperationResult.failure(
                    Status.LIMIT_REACHED,
                    "role.max-rank"
            );
        }

        if (!faction.promote(targetId)) {
            return OperationResult.failure(
                    Status.FAILED,
                    "role.promote-failed"
            );
        }

        FactionRole newRole = faction.getRole(targetId);
        synchronizePlayerRole(targetId, newRole);
        persistFaction(faction);
        logRoleChange(faction, targetId, oldRole, newRole, context);
        refreshPermissionContexts(targetId);

        return OperationResult.success(newRole);
    }

    public OperationResult<FactionRole> demote(
            Faction faction,
            UUID targetId,
            OperationContext context
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return OperationResult.failure(
                    Status.UNAVAILABLE,
                    "role.main-thread-required"
            );
        }

        OperationResult<FactionRole> validation = validateTarget(faction, targetId);
        if (validation != null) {
            return validation;
        }

        FactionRole oldRole = faction.getRole(targetId);
        if (oldRole == null) {
            return OperationResult.failure(
                    Status.CONFLICT,
                    "role.missing-current-role"
            );
        }

        if (!oldRole.canBeDemotedNormally()) {
            return OperationResult.failure(
                    Status.LIMIT_REACHED,
                    "role.min-rank"
            );
        }

        if (!faction.demote(targetId)) {
            return OperationResult.failure(
                    Status.FAILED,
                    "role.demote-failed"
            );
        }

        FactionRole newRole = faction.getRole(targetId);
        synchronizePlayerRole(targetId, newRole);
        persistFaction(faction);
        logRoleChange(faction, targetId, oldRole, newRole, context);
        refreshPermissionContexts(targetId);

        return OperationResult.success(newRole);
    }

    /**
     * Affectation directe d'un rôle non-LEADER.
     *
     * Utilisé par les raccourcis /f mod, /f coleader et plus tard les outils
     * admin. Le transfert de leadership passe obligatoirement par
     * transferLeadership().
     */
    public OperationResult<FactionRole> setRole(
            Faction faction,
            UUID targetId,
            FactionRole newRole,
            OperationContext context
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return OperationResult.failure(
                    Status.UNAVAILABLE,
                    "role.main-thread-required"
            );
        }

        OperationResult<FactionRole> validation = validateTarget(faction, targetId);
        if (validation != null) {
            return validation;
        }

        if (newRole == null) {
            return OperationResult.failure(
                    Status.INVALID_INPUT,
                    "role.invalid"
            );
        }

        if (newRole == FactionRole.LEADER) {
            return OperationResult.failure(
                    Status.INVALID_INPUT,
                    "role.use-leadership-transfer"
            );
        }

        if (faction.isLeader(targetId)) {
            return OperationResult.failure(
                    Status.FORBIDDEN,
                    "role.cannot-change-leader"
            );
        }

        FactionRole oldRole = faction.getRole(targetId);
        if (oldRole == newRole) {
            return OperationResult.noChange("role.no-change");
        }

        faction.setRole(targetId, newRole);

        FactionRole effectiveRole = faction.getRole(targetId);
        if (effectiveRole != newRole) {
            return OperationResult.failure(
                    Status.FAILED,
                    "role.set-failed"
            );
        }

        synchronizePlayerRole(targetId, newRole);
        persistFaction(faction);
        logRoleChange(faction, targetId, oldRole, newRole, context);
        refreshPermissionContexts(targetId);

        return OperationResult.success(newRole);
    }

    /**
     * Transfert du leadership au niveau du modèle actuel.
     *
     * Ancien leader -> COLEADER
     * Nouveau leader -> LEADER
     */
    public OperationResult<FactionRole> transferLeadership(
            Faction faction,
            UUID newLeaderId,
            OperationContext context
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return OperationResult.failure(
                    Status.UNAVAILABLE,
                    "role.main-thread-required"
            );
        }

        OperationResult<FactionRole> validation = validateTarget(faction, newLeaderId);
        if (validation != null) {
            return validation;
        }

        UUID oldLeaderId = faction.getLeader();
        if (oldLeaderId == null) {
            return OperationResult.failure(
                    Status.CONFLICT,
                    "role.no-current-leader"
            );
        }

        if (oldLeaderId.equals(newLeaderId)) {
            return OperationResult.noChange("role.already-leader");
        }

        FactionRole targetOldRole = faction.getRole(newLeaderId);

        faction.setLeader(newLeaderId);

        if (!newLeaderId.equals(faction.getLeader())) {
            return OperationResult.failure(
                    Status.FAILED,
                    "role.leadership-transfer-failed"
            );
        }

        synchronizePlayerRole(newLeaderId, FactionRole.LEADER);
        synchronizePlayerRole(oldLeaderId, FactionRole.COLEADER);
        persistFaction(faction);

        logRoleChange(
                faction,
                newLeaderId,
                targetOldRole,
                FactionRole.LEADER,
                context
        );

        if (context != null && context.hasActor()) {
            plugin.getLogManager().log(
                    faction.getId(),
                    LogType.MEMBER_DEMOTE,
                    context.getActorId(),
                    safeActorName(context),
                    oldLeaderId,
                    resolvePlayerName(oldLeaderId),
                    "leadership-transfer: LEADER->COLEADER"
            );
        }

        refreshPermissionContexts(newLeaderId);
        refreshPermissionContexts(oldLeaderId);

        return OperationResult.success(FactionRole.LEADER);
    }

    private OperationResult<FactionRole> validateTarget(
            Faction faction,
            UUID targetId
    ) {
        if (faction == null || targetId == null) {
            return OperationResult.failure(
                    Status.INVALID_INPUT,
                    "role.invalid-target"
            );
        }

        if (faction.isSystemFaction()) {
            return OperationResult.failure(
                    Status.FORBIDDEN,
                    "role.system-faction"
            );
        }

        if (!faction.isMember(targetId)) {
            return OperationResult.failure(
                    Status.NOT_FOUND,
                    "role.not-member"
            );
        }

        return null;
    }

    private void synchronizePlayerRole(UUID playerId, FactionRole role) {
        if (playerId == null || role == null) {
            return;
        }

        /*
         * Transition V1/V2 :
         * getFPlayer(UUID) crée encore une entrée si elle n'existe pas.
         * Ce comportement sera supprimé dans le lot Domain/FPlayer suivant.
         */
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(playerId);
        if (fPlayer == null) {
            return;
        }

        fPlayer.setRole(role);
        plugin.getStorageManager().markDirty(fPlayer);
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

    private void persistFaction(Faction faction) {
        plugin.getStorageManager().markDirty(faction);
    }

    private void logRoleChange(
            Faction faction,
            UUID targetId,
            FactionRole oldRole,
            FactionRole newRole,
            OperationContext context
    ) {
        if (context == null || !context.hasActor()) {
            return;
        }

        LogType type = LogType.MEMBER_PROMOTE;

        if (oldRole != null && newRole != null
                && newRole.getPriority() < oldRole.getPriority()) {
            type = LogType.MEMBER_DEMOTE;
        }

        plugin.getLogManager().log(
                faction.getId(),
                type,
                context.getActorId(),
                safeActorName(context),
                targetId,
                resolvePlayerName(targetId),
                "role=" + roleName(oldRole)
                        + "->" + roleName(newRole)
                        + ";source=" + context.getSource().name()
                        + ";correlation=" + context.getCorrelationId()
        );
    }

    private String resolvePlayerName(UUID playerId) {
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(playerId);
        if (fPlayer != null && fPlayer.getLastKnownName() != null
                && !fPlayer.getLastKnownName().trim().isEmpty()) {
            return fPlayer.getLastKnownName();
        }
        return playerId.toString();
    }

    private static String safeActorName(OperationContext context) {
        return context.getActorName() != null
                ? context.getActorName()
                : context.getActorId().toString();
    }

    private static String roleName(FactionRole role) {
        return role == null ? "null" : role.name();
    }
}
