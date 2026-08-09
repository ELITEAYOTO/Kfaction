package me.krunsh.kfaction.services;

import java.util.Locale;

import org.bukkit.Bukkit;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.audit.AuditCategory;
import me.krunsh.kfaction.audit.AuditOutcome;
import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.core.operation.OperationResult;
import me.krunsh.kfaction.core.operation.OperationResult.Status;
import me.krunsh.kfaction.data.ClaimGroup;
import me.krunsh.kfaction.data.ClaimGroup.Rule;
import me.krunsh.kfaction.data.FLocation;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionRole;
import me.krunsh.kfaction.data.Relation;
import me.krunsh.kfaction.permissions.TerritoryAction;

/**
 * Service applicatif des Claim Groups V2.
 */
public final class ClaimGroupService {

    private final Kfaction plugin;

    public ClaimGroupService(Kfaction plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException(
                    "plugin cannot be null"
            );
        }

        this.plugin = plugin;
    }

    public OperationResult<ClaimGroup> create(
            Faction faction,
            String rawId,
            OperationContext context
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return failure(
                    Status.UNAVAILABLE,
                    "Claim Group mutation must run on Bukkit primary thread"
            );
        }

        if (!isValidFaction(faction)
                || context == null) {
            return failure(
                    Status.INVALID_INPUT,
                    "Faction/context invalide"
            );
        }

        String id =
                normalizeId(rawId);

        int maxLength =
                clamp(
                        plugin.getConfigManager()
                                .getInt(
                                        "claim-groups.id-max-length",
                                        16
                                ),
                        3,
                        32
                );

        if (id == null
                || id.length() > maxLength
                || !id.matches("^[a-z0-9_-]+$")) {
            return failure(
                    Status.INVALID_INPUT,
                    "ID invalide: a-z, 0-9, _ et -, maximum "
                            + maxLength
                            + " caractères"
            );
        }

        if (faction.getClaimGroup(id) != null) {
            return failure(
                    Status.CONFLICT,
                    "Ce Claim Group existe déjà"
            );
        }

        int maxGroups =
                clamp(
                        plugin.getConfigManager()
                                .getInt(
                                        "claim-groups.max-per-faction",
                                        16
                                ),
                        1,
                        128
                );

        if (faction.getClaimGroupCount()
                >= maxGroups) {
            return failure(
                    Status.LIMIT_REACHED,
                    "Limite de Claim Groups atteinte: "
                            + maxGroups
            );
        }

        ClaimGroup group =
                new ClaimGroup(id);

        if (!faction.addClaimGroup(group)) {
            return failure(
                    Status.FAILED,
                    "Impossible d'ajouter le Claim Group"
            );
        }

        dirty(faction);

        audit(
                faction,
                context,
                "create group=" + id
        );

        return OperationResult.success(group);
    }

    public OperationResult<Integer> delete(
            Faction faction,
            String rawId,
            OperationContext context
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return failure(
                    Status.UNAVAILABLE,
                    "Claim Group mutation must run on Bukkit primary thread"
            );
        }

        if (!isValidFaction(faction)
                || context == null) {
            return failure(
                    Status.INVALID_INPUT,
                    "Faction/context invalide"
            );
        }

        String id =
                normalizeId(rawId);

        ClaimGroup existing =
                faction.getClaimGroup(id);

        if (existing == null) {
            return failure(
                    Status.NOT_FOUND,
                    "Claim Group introuvable"
            );
        }

        int assigned =
                faction.countClaimsInGroup(id);

        if (!faction.removeClaimGroup(id)) {
            return failure(
                    Status.FAILED,
                    "Impossible de supprimer le Claim Group"
            );
        }

        dirty(faction);

        audit(
                faction,
                context,
                "delete group="
                        + id
                        + " unassigned="
                        + assigned
        );

        return OperationResult.success(
                Integer.valueOf(assigned)
        );
    }

    public OperationResult<ClaimGroup> assign(
            Faction faction,
            String rawId,
            FLocation location,
            OperationContext context
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return failure(
                    Status.UNAVAILABLE,
                    "Claim Group mutation must run on Bukkit primary thread"
            );
        }

        if (!isValidFaction(faction)
                || location == null
                || context == null) {
            return failure(
                    Status.INVALID_INPUT,
                    "Faction/chunk/context invalide"
            );
        }

        String id =
                normalizeId(rawId);

        ClaimGroup group =
                faction.getClaimGroup(id);

        if (group == null) {
            return failure(
                    Status.NOT_FOUND,
                    "Claim Group introuvable"
            );
        }

        if (!faction.hasClaim(location)) {
            return failure(
                    Status.FORBIDDEN,
                    "Ce chunk n'appartient pas à votre faction"
            );
        }

        String current =
                faction.getClaimGroupId(
                        location
                );

        if (id.equals(current)) {
            return OperationResult.noChange(
                    "claim-group.already-assigned"
            );
        }

        if (!faction.assignClaimToGroup(
                location,
                id
        )) {
            return failure(
                    Status.FAILED,
                    "Impossible d'affecter le chunk"
            );
        }

        dirty(faction);

        audit(
                faction,
                context,
                "assign group="
                        + id
                        + " chunk="
                        + location.getKey()
        );

        return OperationResult.success(group);
    }

    public OperationResult<String> unassign(
            Faction faction,
            FLocation location,
            OperationContext context
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return failure(
                    Status.UNAVAILABLE,
                    "Claim Group mutation must run on Bukkit primary thread"
            );
        }

        if (!isValidFaction(faction)
                || location == null
                || context == null) {
            return failure(
                    Status.INVALID_INPUT,
                    "Faction/chunk/context invalide"
            );
        }

        if (!faction.hasClaim(location)) {
            return failure(
                    Status.FORBIDDEN,
                    "Ce chunk n'appartient pas à votre faction"
            );
        }

        String previous =
                faction.getClaimGroupId(
                        location
                );

        if (previous == null) {
            return OperationResult.noChange(
                    "claim-group.not-assigned"
            );
        }

        faction.unassignClaimGroup(
                location
        );

        dirty(faction);

        audit(
                faction,
                context,
                "unassign group="
                        + previous
                        + " chunk="
                        + location.getKey()
        );

        return OperationResult.success(previous);
    }

    public OperationResult<Rule> setRoleRule(
            Faction faction,
            String rawId,
            FactionRole role,
            TerritoryAction action,
            Rule rule,
            OperationContext context
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return failure(
                    Status.UNAVAILABLE,
                    "Claim Group mutation must run on Bukkit primary thread"
            );
        }

        if (!isValidFaction(faction)
                || role == null
                || action == null
                || rule == null
                || context == null) {
            return failure(
                    Status.INVALID_INPUT,
                    "Paramètres de règle invalides"
            );
        }

        if (role == FactionRole.LEADER) {
            return failure(
                    Status.FORBIDDEN,
                    "Le Leader conserve toujours tous les droits"
            );
        }

        ClaimGroup group =
                faction.getClaimGroup(
                        normalizeId(rawId)
                );

        if (group == null) {
            return failure(
                    Status.NOT_FOUND,
                    "Claim Group introuvable"
            );
        }

        Rule previous =
                group.getRoleRule(
                        role,
                        action
                );

        if (previous == rule) {
            return OperationResult.noChange(
                    "claim-group.rule-unchanged"
            );
        }

        group.setRoleRule(
                role,
                action,
                rule
        );

        faction.updateActivity();
        dirty(faction);

        audit(
                faction,
                context,
                "rule group="
                        + group.getId()
                        + " role="
                        + role.name()
                        + " action="
                        + action.name()
                        + " "
                        + previous.name()
                        + "->"
                        + rule.name()
        );

        return OperationResult.success(rule);
    }

    public OperationResult<Rule> setRelationRule(
            Faction faction,
            String rawId,
            Relation relation,
            TerritoryAction action,
            Rule rule,
            OperationContext context
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return failure(
                    Status.UNAVAILABLE,
                    "Claim Group mutation must run on Bukkit primary thread"
            );
        }

        if (!isValidFaction(faction)
                || relation == null
                || action == null
                || rule == null
                || context == null) {
            return failure(
                    Status.INVALID_INPUT,
                    "Paramètres de règle invalides"
            );
        }

        if (relation == Relation.MEMBER) {
            return failure(
                    Status.INVALID_INPUT,
                    "Utilisez une règle de rôle pour MEMBER"
            );
        }

        ClaimGroup group =
                faction.getClaimGroup(
                        normalizeId(rawId)
                );

        if (group == null) {
            return failure(
                    Status.NOT_FOUND,
                    "Claim Group introuvable"
            );
        }

        Rule previous =
                group.getRelationRule(
                        relation,
                        action
                );

        if (previous == rule) {
            return OperationResult.noChange(
                    "claim-group.rule-unchanged"
            );
        }

        group.setRelationRule(
                relation,
                action,
                rule
        );

        faction.updateActivity();
        dirty(faction);

        audit(
                faction,
                context,
                "rule group="
                        + group.getId()
                        + " relation="
                        + relation.name()
                        + " action="
                        + action.name()
                        + " "
                        + previous.name()
                        + "->"
                        + rule.name()
        );

        return OperationResult.success(rule);
    }

    private void dirty(Faction faction) {
        plugin.getStorageManager()
                .markDirty(faction);
    }

    private void audit(
            Faction faction,
            OperationContext context,
            String details
    ) {
        String action =
                claimGroupAction(
                        details
                );

        if (plugin.getLogManager() != null) {
            plugin.getLogManager()
                    .audit(
                            context,
                            AuditCategory.CLAIM_GROUP,
                            action,
                            AuditOutcome.SUCCESS,
                            faction.getId(),
                            null,
                            null,
                            details
                    );
        }

        plugin.getLogger().info(
                "[ClaimGroup] faction="
                        + faction.getId()
                        + " actor="
                        + (context.hasActorName()
                                ? context.getActorName()
                                : "SYSTEM")
                        + " source="
                        + context.getSource()
                        + " correlation="
                        + context.getCorrelationId()
                        + " "
                        + details
        );
    }

    private static String claimGroupAction(
            String details
    ) {
        String normalized =
                details != null
                        ? details.trim()
                                .toLowerCase(
                                        java.util.Locale.ROOT
                                )
                        : "";

        if (normalized.startsWith("create ")) {
            return "CLAIM_GROUP_CREATE";
        }

        if (normalized.startsWith("delete ")) {
            return "CLAIM_GROUP_DELETE";
        }

        if (normalized.startsWith("assign ")) {
            return "CLAIM_GROUP_ASSIGN";
        }

        if (normalized.startsWith("unassign ")) {
            return "CLAIM_GROUP_UNASSIGN";
        }

        if (normalized.startsWith("rule ")) {
            return "CLAIM_GROUP_RULE";
        }

        return "CLAIM_GROUP_CHANGE";
    }

    private static boolean isValidFaction(
            Faction faction
    ) {
        return faction != null
                && !faction.isSystemFaction();
    }

    private static String normalizeId(String value) {
        return value == null
                ? null
                : value.trim()
                        .toLowerCase(Locale.ROOT);
    }

    private static int clamp(
            int value,
            int min,
            int max
    ) {
        return Math.max(
                min,
                Math.min(
                        max,
                        value
                )
        );
    }

    private static <T> OperationResult<T> failure(
            Status status,
            String detail
    ) {
        return OperationResult.failure(
                status,
                "claim-group.failed",
                detail
        );
    }
}
