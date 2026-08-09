package me.krunsh.kfaction.permissions;

import me.krunsh.kfaction.data.FactionRole;
import me.krunsh.kfaction.data.Relation;

/**
 * Résultat explicable d'une décision de permission.
 *
 * Le Claim Group est optionnel: null signifie que la décision vient de l'ACL
 * générale de faction/zone.
 */
public final class PermissionDecision {

    public enum Reason {
        ADMIN_BYPASS,
        WILDERNESS,
        SAFEZONE_RULE,
        WARZONE_RULE,
        GLOBAL_ZONE_RULE,

        CLAIM_GROUP_ALLOW,
        CLAIM_GROUP_DENY,
        GRACE_PROTECTION,

        OWN_ROLE,
        RELATION_ACL,
        RAID_RULE,
        PASSIVE_MOVEMENT,

        NO_PLAYER,
        NO_PROFILE,
        NO_FACTION,
        FACTION_NOT_FOUND,
        MEMBERSHIP_MISMATCH,
        UNSUPPORTED_ACTION,
        ROLE_DENIED,
        RELATION_DENIED,
        ZONE_DENIED,
        INVALID_INPUT
    }

    private final boolean allowed;
    private final Reason reason;
    private final String territoryFactionId;
    private final FactionRole role;
    private final Relation relation;
    private final String claimGroupId;

    private PermissionDecision(
            boolean allowed,
            Reason reason,
            String territoryFactionId,
            FactionRole role,
            Relation relation,
            String claimGroupId
    ) {
        this.allowed = allowed;
        this.reason = reason;
        this.territoryFactionId = territoryFactionId;
        this.role = role;
        this.relation = relation;
        this.claimGroupId = claimGroupId;
    }

    public static PermissionDecision allow(
            Reason reason
    ) {
        return new PermissionDecision(
                true,
                reason,
                null,
                null,
                null,
                null
        );
    }

    public static PermissionDecision allow(
            Reason reason,
            String territoryFactionId,
            FactionRole role,
            Relation relation
    ) {
        return allow(
                reason,
                territoryFactionId,
                role,
                relation,
                null
        );
    }

    public static PermissionDecision allow(
            Reason reason,
            String territoryFactionId,
            FactionRole role,
            Relation relation,
            String claimGroupId
    ) {
        return new PermissionDecision(
                true,
                reason,
                territoryFactionId,
                role,
                relation,
                claimGroupId
        );
    }

    public static PermissionDecision deny(
            Reason reason
    ) {
        return new PermissionDecision(
                false,
                reason,
                null,
                null,
                null,
                null
        );
    }

    public static PermissionDecision deny(
            Reason reason,
            String territoryFactionId,
            FactionRole role,
            Relation relation
    ) {
        return deny(
                reason,
                territoryFactionId,
                role,
                relation,
                null
        );
    }

    public static PermissionDecision deny(
            Reason reason,
            String territoryFactionId,
            FactionRole role,
            Relation relation,
            String claimGroupId
    ) {
        return new PermissionDecision(
                false,
                reason,
                territoryFactionId,
                role,
                relation,
                claimGroupId
        );
    }

    public boolean isAllowed() {
        return allowed;
    }

    public boolean isDenied() {
        return !allowed;
    }

    public Reason getReason() {
        return reason;
    }

    public String getTerritoryFactionId() {
        return territoryFactionId;
    }

    public FactionRole getRole() {
        return role;
    }

    public Relation getRelation() {
        return relation;
    }

    public String getClaimGroupId() {
        return claimGroupId;
    }

    public boolean hasClaimGroup() {
        return claimGroupId != null;
    }

    @Override
    public String toString() {
        return "PermissionDecision{" +
                "allowed=" + allowed +
                ", reason=" + reason +
                ", territoryFactionId='" + territoryFactionId + '\'' +
                ", role=" + role +
                ", relation=" + relation +
                ", claimGroupId='" + claimGroupId + '\'' +
                '}';
    }
}
