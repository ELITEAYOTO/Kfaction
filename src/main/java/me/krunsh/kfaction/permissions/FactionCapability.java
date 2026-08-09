package me.krunsh.kfaction.permissions;

import java.util.LinkedHashSet;
import java.util.Set;

import me.krunsh.kfaction.data.PermissionAction;

/**
 * Permission applicative V2 d'une faction.
 *
 * Le primaryLegacyAction est la représentation persistée V2 actuelle.
 * Les compatibilityAliases servent uniquement à lire correctement les
 * permissions historiques pendant la migration.
 */
public enum FactionCapability {

    INVITE("invite", PermissionAction.INVITE),
    KICK("kick", PermissionAction.KICK),
    PROMOTE("promote", PermissionAction.PROMOTE),
    DEMOTE("demote", PermissionAction.DEMOTE),
    BAN("ban", PermissionAction.BAN),
    UNBAN("unban", PermissionAction.UNBAN),

    CLAIM("claim", PermissionAction.CLAIM),
    UNCLAIM("unclaim", PermissionAction.UNCLAIM),

    AUTO_CLAIM(
            "auto_claim",
            PermissionAction.AUTOCLAIM,
            PermissionAction.CLAIM
    ),

    SET_HOME("set_home", PermissionAction.SETHOME),
    USE_HOME("use_home", PermissionAction.HOME),

    SET_WARP(
            "set_warp",
            PermissionAction.SETWARP,
            PermissionAction.SETHOME
    ),

    DELETE_WARP(
            "delete_warp",
            PermissionAction.DELWARP,
            PermissionAction.SETHOME
    ),

    USE_WARP(
            "use_warp",
            PermissionAction.WARP,
            PermissionAction.HOME
    ),

    DEPOSIT_MONEY(
            "deposit_money",
            PermissionAction.DEPOSIT
    ),

    WITHDRAW_MONEY(
            "withdraw_money",
            PermissionAction.WITHDRAW
    ),

    PAY_FACTION(
            "pay_faction",
            PermissionAction.PAY
    ),

    RELATION_ALLY(
            "relation_ally",
            PermissionAction.RELATION_ALLY,
            PermissionAction.ALLY
    ),

    RELATION_ENEMY(
            "relation_enemy",
            PermissionAction.RELATION_ENEMY,
            PermissionAction.ENEMY
    ),

    RELATION_NEUTRAL(
            "relation_neutral",
            PermissionAction.RELATION_NEUTRAL,
            PermissionAction.NEUTRAL
    ),

    RELATION_TRUCE(
            "relation_truce",
            PermissionAction.RELATION_TRUCE,
            PermissionAction.TRUCE
    ),

    RENAME("rename", PermissionAction.RENAME),
    EDIT_DESCRIPTION(
            "edit_description",
            PermissionAction.DESCRIPTION
    ),
    EDIT_TAG("edit_tag", PermissionAction.TAG),
    EDIT_PERMISSIONS(
            "edit_permissions",
            PermissionAction.PERMS
    ),

    /**
     * Permission dédiée V2.
     *
     * Pendant la migration, elle réutilise PERMS en stockage afin que les
     * factions existantes qui avaient déjà le droit de gérer leurs ACL ne
     * soient pas bloquées.
     */
    MANAGE_CLAIM_GROUPS(
            "manage_claim_groups",
            PermissionAction.PERMS
    ),

    VIEW_LOGS("view_logs", PermissionAction.VIEW_LOGS),

    DISBAND("disband", PermissionAction.DISBAND),

    TRANSFER_LEADERSHIP(
            "transfer_leadership",
            PermissionAction.TRANSFER
    ),

    FLY("fly", PermissionAction.FLY),
    FACTION_CHEST("faction_chest", PermissionAction.CHEST),

    TNT_DEPOSIT(
            "tnt_deposit",
            PermissionAction.TNT_DEPOSIT
    ),

    TNT_WITHDRAW(
            "tnt_withdraw",
            PermissionAction.TNT_WITHDRAW
    ),

    TNT_FILL(
            "tnt_fill",
            PermissionAction.TNT_FILL
    );

    private final String configKey;
    private final PermissionAction primaryLegacyAction;
    private final PermissionAction[] acceptedLegacyActions;

    FactionCapability(
            String configKey,
            PermissionAction primaryLegacyAction,
            PermissionAction... compatibilityAliases
    ) {
        this.configKey = configKey;
        this.primaryLegacyAction = primaryLegacyAction;

        Set<PermissionAction> accepted =
                new LinkedHashSet<PermissionAction>();

        if (primaryLegacyAction != null) {
            accepted.add(primaryLegacyAction);
        }

        if (compatibilityAliases != null) {
            for (PermissionAction alias : compatibilityAliases) {
                if (alias != null) {
                    accepted.add(alias);
                }
            }
        }

        this.acceptedLegacyActions =
                accepted.toArray(
                        new PermissionAction[accepted.size()]
                );
    }

    public String getConfigKey() {
        return configKey;
    }

    /**
     * Action persistée lorsqu'on applique les defaults V2.
     */
    public PermissionAction getLegacyAction() {
        return primaryLegacyAction;
    }

    /**
     * Actions acceptées en lecture.
     *
     * Exemple:
     * SET_WARP accepte SETWARP, mais aussi l'ancien SETHOME qui était
     * utilisé par erreur par SetWarpCommand V1.
     */
    public PermissionAction[] getAcceptedLegacyActions() {
        return acceptedLegacyActions.clone();
    }

    public static FactionCapability fromLegacy(
            PermissionAction action
    ) {
        if (action == null) {
            return null;
        }

        // Priorité aux mappings primaires pour éviter les ambiguïtés.
        for (FactionCapability capability : values()) {
            if (capability.primaryLegacyAction == action) {
                return capability;
            }
        }

        for (FactionCapability capability : values()) {
            for (PermissionAction accepted
                    : capability.acceptedLegacyActions) {
                if (accepted == action) {
                    return capability;
                }
            }
        }

        return null;
    }

    public static FactionCapability fromConfigKey(
            String key
    ) {
        if (key == null) {
            return null;
        }

        for (FactionCapability capability : values()) {
            if (capability.configKey.equalsIgnoreCase(key)
                    || capability.name().equalsIgnoreCase(key)) {
                return capability;
            }
        }

        return null;
    }
}
