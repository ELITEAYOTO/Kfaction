package me.krunsh.kfaction.permissions;

import java.util.LinkedHashSet;
import java.util.Set;

import me.krunsh.kfaction.data.PermissionAction;

/**
 * Action effectuée dans un territoire.
 *
 * Les aliases assurent la compatibilité avec les anciennes permissions
 * SPAWNER/TNT pendant la migration V2.
 */
public enum TerritoryAction {

    ENTER("enter", null),

    BLOCK_PLACE(
            "block_place",
            PermissionAction.BUILD
    ),

    BLOCK_BREAK(
            "block_break",
            PermissionAction.DESTROY
    ),

    /**
     * Actions environnementales utilisées principalement par Global Zones.
     *
     * Elles n'ont volontairement pas de mapping PermissionAction V1:
     * leur politique est portée directement par ZoneDefinition.
     */
    PISTON("piston", null),
    FLUID_FLOW("fluid_flow", null),
    FIRE_SPREAD("fire_spread", null),
    EXPLOSION_BLOCK_DAMAGE("explosion_block_damage", null),
    ENTITY_GRIEF("entity_grief", null),
    WITHER_SPAWN("wither_spawn", null),

    SWITCH(
            "switch",
            PermissionAction.SWITCH
    ),

    REDSTONE(
            "redstone",
            PermissionAction.SWITCH
    ),

    CONTAINER_OPEN(
            "container_open",
            PermissionAction.CONTAINER
    ),

    CONTAINER_DEPOSIT(
            "container_deposit",
            PermissionAction.CONTAINER
    ),

    CONTAINER_WITHDRAW(
            "container_withdraw",
            PermissionAction.CONTAINER
    ),

    HOPPER(
            "hopper",
            PermissionAction.CONTAINER
    ),

    FURNACE(
            "furnace",
            PermissionAction.CONTAINER
    ),

    BREWING(
            "brewing",
            PermissionAction.CONTAINER
    ),

    ANVIL(
            "anvil",
            PermissionAction.CONTAINER
    ),

    ENCHANT(
            "enchant",
            PermissionAction.CONTAINER
    ),

    ITEM_FRAME(
            "item_frame",
            PermissionAction.ITEM_FRAME
    ),

    ARMOR_STAND(
            "armor_stand",
            PermissionAction.ARMOR_STAND
    ),

    SPAWNER_PLACE(
            "spawner_place",
            PermissionAction.SPAWNER_PLACE,
            PermissionAction.SPAWNER
    ),

    SPAWNER_BREAK(
            "spawner_break",
            PermissionAction.SPAWNER_BREAK,
            PermissionAction.SPAWNER
    ),

    SPAWNER_INTERACT(
            "spawner_interact",
            PermissionAction.SPAWNER_INTERACT,
            PermissionAction.SPAWNER
    ),

    TNT_PLACE(
            "tnt_place",
            PermissionAction.TNT_PLACE,
            PermissionAction.TNT
    ),

    TNT_IGNITE(
            "tnt_ignite",
            PermissionAction.TNT_IGNITE,
            PermissionAction.TNT
    ),

    BUCKET_EMPTY(
            "bucket_empty",
            PermissionAction.BUILD
    ),

    BUCKET_FILL(
            "bucket_fill",
            PermissionAction.DESTROY
    ),

    FLINT_AND_STEEL(
            "flint_and_steel",
            PermissionAction.TNT_IGNITE,
            PermissionAction.TNT
    ),

    ENDER_PEARL("ender_pearl", null),
    VEHICLE("vehicle", PermissionAction.SWITCH),

    FLY("fly", PermissionAction.FLY),

    TELEPORT_IN("teleport_in", null),

    SET_HOME(
            "set_home",
            PermissionAction.SETHOME
    ),

    COMMAND_USE("command_use", null);

    private final String configKey;
    private final PermissionAction primaryLegacyAction;
    private final PermissionAction[] acceptedLegacyActions;

    TerritoryAction(
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

    public PermissionAction getLegacyAction() {
        return primaryLegacyAction;
    }

    public PermissionAction[] getAcceptedLegacyActions() {
        return acceptedLegacyActions.clone();
    }

    public boolean hasLegacyMapping() {
        return acceptedLegacyActions.length > 0;
    }

    public static TerritoryAction fromLegacy(
            PermissionAction action
    ) {
        if (action == null) {
            return null;
        }

        for (TerritoryAction territoryAction : values()) {
            if (territoryAction.primaryLegacyAction == action) {
                return territoryAction;
            }
        }

        for (TerritoryAction territoryAction : values()) {
            for (PermissionAction accepted
                    : territoryAction.acceptedLegacyActions) {
                if (accepted == action) {
                    return territoryAction;
                }
            }
        }

        return null;
    }

    public static TerritoryAction fromConfigKey(
            String key
    ) {
        if (key == null) {
            return null;
        }

        for (TerritoryAction action : values()) {
            if (action.configKey.equalsIgnoreCase(key)
                    || action.name().equalsIgnoreCase(key)) {
                return action;
            }
        }

        return null;
    }
}
