package me.krunsh.kfaction.permissions;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionRole;
import me.krunsh.kfaction.data.PermissionAction;
import me.krunsh.kfaction.data.Relation;

/**
 * Profils de permissions par défaut V2.
 *
 * Ils sont appliqués uniquement:
 * - à une nouvelle faction;
 * - lors d'un reset explicite via /f perms reset.
 *
 * Les factions déjà persistées conservent leurs ACL personnalisées.
 */
public final class PermissionDefaults {

    private final Kfaction plugin;

    private final Map<FactionRole, RoleProfile> roleProfiles;
    private final Map<Relation, EnumSet<TerritoryAction>> relationProfiles;

    public PermissionDefaults(Kfaction plugin) {
        this.plugin = plugin;
        this.roleProfiles =
                new EnumMap<FactionRole, RoleProfile>(
                        FactionRole.class
                );
        this.relationProfiles =
                new EnumMap<Relation, EnumSet<TerritoryAction>>(
                        Relation.class
                );

        reload();
    }

    public void reload() {
        roleProfiles.clear();
        relationProfiles.clear();

        loadRole(
                FactionRole.RECRUIT,
                capabilities(
                        FactionCapability.USE_HOME,
                        FactionCapability.USE_WARP
                ),
                territory(
                        TerritoryAction.CONTAINER_OPEN
                )
        );

        loadRole(
                FactionRole.MEMBER,
                capabilities(
                        FactionCapability.USE_HOME,
                        FactionCapability.USE_WARP,
                        FactionCapability.DEPOSIT_MONEY
                ),
                territory(
                        TerritoryAction.BLOCK_PLACE,
                        TerritoryAction.BLOCK_BREAK,
                        TerritoryAction.SWITCH,
                        TerritoryAction.CONTAINER_OPEN
                )
        );

        loadRole(
                FactionRole.OFFICER,
                capabilities(
                        FactionCapability.USE_HOME,
                        FactionCapability.USE_WARP,
                        FactionCapability.DEPOSIT_MONEY,
                        FactionCapability.INVITE,
                        FactionCapability.CLAIM,
                        FactionCapability.AUTO_CLAIM
                ),
                territory(
                        TerritoryAction.BLOCK_PLACE,
                        TerritoryAction.BLOCK_BREAK,
                        TerritoryAction.SWITCH,
                        TerritoryAction.CONTAINER_OPEN
                )
        );

        loadRole(
                FactionRole.MODERATOR,
                capabilities(
                        FactionCapability.USE_HOME,
                        FactionCapability.USE_WARP,
                        FactionCapability.DEPOSIT_MONEY,
                        FactionCapability.INVITE,
                        FactionCapability.CLAIM,
                        FactionCapability.AUTO_CLAIM,
                        FactionCapability.KICK,
                        FactionCapability.SET_HOME,
                        FactionCapability.SET_WARP,
                        FactionCapability.DELETE_WARP
                ),
                territory(
                        TerritoryAction.BLOCK_PLACE,
                        TerritoryAction.BLOCK_BREAK,
                        TerritoryAction.SWITCH,
                        TerritoryAction.CONTAINER_OPEN,
                        TerritoryAction.SPAWNER_INTERACT,
                        TerritoryAction.TNT_PLACE,
                        TerritoryAction.TNT_IGNITE
                )
        );

        loadRole(
                FactionRole.COLEADER,
                capabilities(
                        FactionCapability.USE_HOME,
                        FactionCapability.USE_WARP,
                        FactionCapability.DEPOSIT_MONEY,
                        FactionCapability.INVITE,
                        FactionCapability.CLAIM,
                        FactionCapability.AUTO_CLAIM,
                        FactionCapability.KICK,
                        FactionCapability.SET_HOME,
                        FactionCapability.SET_WARP,
                        FactionCapability.DELETE_WARP,
                        FactionCapability.UNCLAIM,
                        FactionCapability.PROMOTE,
                        FactionCapability.DEMOTE,
                        FactionCapability.WITHDRAW_MONEY,
                        FactionCapability.RELATION_ALLY,
                        FactionCapability.RELATION_ENEMY,
                        FactionCapability.RELATION_TRUCE,
                        FactionCapability.RELATION_NEUTRAL,
                        FactionCapability.RENAME,
                        FactionCapability.EDIT_DESCRIPTION,
                        FactionCapability.EDIT_TAG,
                        FactionCapability.EDIT_PERMISSIONS,
                        FactionCapability.VIEW_LOGS,
                        FactionCapability.FACTION_CHEST,
                        FactionCapability.FLY
                ),
                territory(
                        TerritoryAction.BLOCK_PLACE,
                        TerritoryAction.BLOCK_BREAK,
                        TerritoryAction.SWITCH,
                        TerritoryAction.CONTAINER_OPEN,
                        TerritoryAction.SPAWNER_PLACE,
                        TerritoryAction.SPAWNER_BREAK,
                        TerritoryAction.SPAWNER_INTERACT,
                        TerritoryAction.TNT_PLACE,
                        TerritoryAction.TNT_IGNITE,
                        TerritoryAction.ITEM_FRAME,
                        TerritoryAction.ARMOR_STAND
                )
        );

        // Leader = tout via Faction.hasPermission(), aucune ACL stockée requise.

        loadRelation(
                Relation.ALLY,
                territory(
                        TerritoryAction.SWITCH
                )
        );

        loadRelation(
                Relation.TRUCE,
                territory()
        );

        loadRelation(
                Relation.NEUTRAL,
                territory()
        );

        loadRelation(
                Relation.ENEMY,
                territory()
        );
    }

    private void loadRole(
            FactionRole role,
            EnumSet<FactionCapability> fallbackCapabilities,
            EnumSet<TerritoryAction> fallbackTerritory
    ) {
        String base =
                "permissions.defaults.roles."
                        + role.name();

        EnumSet<FactionCapability> configuredCapabilities =
                plugin.getConfigManager().contains(
                        base + ".capabilities"
                )
                        ? parseCapabilities(
                                plugin.getConfigManager()
                                        .getStringList(
                                                base + ".capabilities"
                                        ),
                                base + ".capabilities"
                        )
                        : fallbackCapabilities;

        EnumSet<TerritoryAction> configuredTerritory =
                plugin.getConfigManager().contains(
                        base + ".territory-actions"
                )
                        ? parseTerritoryActions(
                                plugin.getConfigManager()
                                        .getStringList(
                                                base + ".territory-actions"
                                        ),
                                base + ".territory-actions"
                        )
                        : fallbackTerritory;

        roleProfiles.put(
                role,
                new RoleProfile(
                        configuredCapabilities,
                        configuredTerritory
                )
        );
    }

    private void loadRelation(
            Relation relation,
            EnumSet<TerritoryAction> fallback
    ) {
        String path =
                "permissions.defaults.relations."
                        + relation.name()
                        + ".territory-actions";

        EnumSet<TerritoryAction> actions =
                plugin.getConfigManager().contains(path)
                        ? parseTerritoryActions(
                                plugin.getConfigManager()
                                        .getStringList(path),
                                path
                        )
                        : fallback;

        relationProfiles.put(
                relation,
                actions
        );
    }

    public void applyAll(
            Faction faction
    ) {
        if (faction == null
                || faction.isSystemFaction()) {
            return;
        }

        for (FactionRole role : FactionRole.values()) {
            if (role != FactionRole.LEADER) {
                resetRole(
                        faction,
                        role
                );
            }
        }

        for (Relation relation : relationProfiles.keySet()) {
            resetRelation(
                    faction,
                    relation
            );
        }
    }

    public void resetRole(
            Faction faction,
            FactionRole role
    ) {
        if (faction == null
                || faction.isSystemFaction()
                || role == null
                || role == FactionRole.LEADER) {
            return;
        }

        for (PermissionAction action
                : PermissionAction.values()) {
            faction.setPermission(
                    role,
                    action,
                    false
            );
        }

        RoleProfile profile =
                roleProfiles.get(role);

        if (profile == null) {
            return;
        }

        for (FactionCapability capability
                : profile.capabilities) {
            PermissionAction action =
                    capability.getLegacyAction();

            if (action != null) {
                faction.setPermission(
                        role,
                        action,
                        true
                );
            }
        }

        for (TerritoryAction territoryAction
                : profile.territoryActions) {
            PermissionAction action =
                    territoryAction.getLegacyAction();

            if (action != null) {
                faction.setPermission(
                        role,
                        action,
                        true
                );
            }
        }
    }

    public void resetRelation(
            Faction faction,
            Relation relation
    ) {
        if (faction == null
                || faction.isSystemFaction()
                || relation == null
                || relation == Relation.MEMBER) {
            return;
        }

        for (PermissionAction action
                : PermissionAction.values()) {
            faction.setPermission(
                    relation,
                    action,
                    false
            );
        }

        EnumSet<TerritoryAction> profile =
                relationProfiles.get(relation);

        if (profile == null) {
            return;
        }

        for (TerritoryAction territoryAction : profile) {
            PermissionAction action =
                    territoryAction.getLegacyAction();

            if (action != null) {
                faction.setPermission(
                        relation,
                        action,
                        true
                );
            }
        }
    }

    private EnumSet<FactionCapability> parseCapabilities(
            List<String> values,
            String path
    ) {
        EnumSet<FactionCapability> result =
                EnumSet.noneOf(
                        FactionCapability.class
                );

        if (values == null) {
            return result;
        }

        for (String value : values) {
            FactionCapability capability =
                    FactionCapability.fromConfigKey(
                            value
                    );

            if (capability == null) {
                plugin.getLogger().warning(
                        "Permission capability inconnue dans "
                                + path
                                + ": "
                                + value
                );
                continue;
            }

            result.add(capability);
        }

        return result;
    }

    private EnumSet<TerritoryAction> parseTerritoryActions(
            List<String> values,
            String path
    ) {
        EnumSet<TerritoryAction> result =
                EnumSet.noneOf(
                        TerritoryAction.class
                );

        if (values == null) {
            return result;
        }

        for (String value : values) {
            TerritoryAction action =
                    TerritoryAction.fromConfigKey(
                            value
                    );

            if (action == null) {
                plugin.getLogger().warning(
                        "TerritoryAction inconnue dans "
                                + path
                                + ": "
                                + value
                );
                continue;
            }

            if (!action.hasLegacyMapping()) {
                plugin.getLogger().warning(
                        "TerritoryAction non persistable ignorée dans "
                                + path
                                + ": "
                                + value
                );
                continue;
            }

            result.add(action);
        }

        return result;
    }

    private static EnumSet<FactionCapability> capabilities(
            FactionCapability... values
    ) {
        EnumSet<FactionCapability> result =
                EnumSet.noneOf(
                        FactionCapability.class
                );

        if (values != null) {
            for (FactionCapability value : values) {
                if (value != null) {
                    result.add(value);
                }
            }
        }

        return result;
    }

    private static EnumSet<TerritoryAction> territory(
            TerritoryAction... values
    ) {
        EnumSet<TerritoryAction> result =
                EnumSet.noneOf(
                        TerritoryAction.class
                );

        if (values != null) {
            for (TerritoryAction value : values) {
                if (value != null) {
                    result.add(value);
                }
            }
        }

        return result;
    }

    private static final class RoleProfile {

        private final EnumSet<FactionCapability> capabilities;
        private final EnumSet<TerritoryAction> territoryActions;

        private RoleProfile(
                EnumSet<FactionCapability> capabilities,
                EnumSet<TerritoryAction> territoryActions
        ) {
            this.capabilities =
                    capabilities.clone();

            this.territoryActions =
                    territoryActions.clone();
        }
    }
}
