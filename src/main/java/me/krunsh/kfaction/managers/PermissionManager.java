package me.krunsh.kfaction.managers;

import java.util.UUID;

import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionRole;
import me.krunsh.kfaction.data.PermissionAction;
import me.krunsh.kfaction.data.Relation;
import me.krunsh.kfaction.permissions.FactionCapability;
import me.krunsh.kfaction.permissions.PermissionDecision;
import me.krunsh.kfaction.permissions.PermissionDefaults;
import me.krunsh.kfaction.permissions.TerritoryAction;
import me.krunsh.kfaction.services.GraceService;
import me.krunsh.kfaction.services.PermissionService;

/**
 * Façade publique du Permission Engine V2.
 */
public final class PermissionManager {

    private final Kfaction plugin;
    private final PermissionService service;
    private final PermissionDefaults defaults;
    private final GraceService graceService;

    public PermissionManager(Kfaction plugin) {
        this.plugin = plugin;
        this.service =
                new PermissionService(plugin);
        this.defaults =
                new PermissionDefaults(plugin);

        this.graceService =
                new GraceService(plugin);
    }

    public void initialize() {
        graceService.initialize();
        service.reload();
        defaults.reload();

        plugin.getLogger().info(
                "PermissionManager V2 initialisé "
                        + "(capabilities + territory + defaults + grace)"
        );
    }

    public void reload() {
        graceService.reload();
        service.reload();
        defaults.reload();
    }

    public void shutdown() {
        graceService.shutdown();
    }

    public PermissionService getService() {
        return service;
    }

    public PermissionDefaults getDefaults() {
        return defaults;
    }

    public GraceService getGraceService() {
        return graceService;
    }

    // ============================================================
    // API V2
    // ============================================================

    public boolean can(
            Player player,
            FactionCapability capability
    ) {
        return service.can(
                player,
                capability
        );
    }

    public PermissionDecision check(
            Player player,
            FactionCapability capability
    ) {
        return service.checkCapability(
                player,
                capability
        );
    }

    public PermissionDecision check(
            Faction faction,
            UUID memberId,
            FactionCapability capability
    ) {
        return service.checkCapability(
                faction,
                memberId,
                capability
        );
    }

    public boolean canTerritory(
            Player player,
            org.bukkit.Location location,
            TerritoryAction action
    ) {
        return service.canTerritory(
                player,
                location,
                action
        );
    }

    // ============================================================
    // Defaults V2
    // ============================================================

    /**
     * À appeler sur une faction fraîchement créée avant sa première save.
     */
    public void applyDefaultsToNewFaction(
            Faction faction
    ) {
        defaults.applyAll(faction);
    }

    public boolean resetRoleDefaults(
            Faction faction,
            FactionRole role
    ) {
        if (faction == null
                || faction.isSystemFaction()
                || role == null
                || role == FactionRole.LEADER) {
            return false;
        }

        defaults.resetRole(
                faction,
                role
        );

        plugin.getStorageManager()
                .markDirty(faction);

        return true;
    }

    public boolean resetRelationDefaults(
            Faction faction,
            Relation relation
    ) {
        if (faction == null
                || faction.isSystemFaction()
                || relation == null
                || relation == Relation.MEMBER) {
            return false;
        }

        defaults.resetRelation(
                faction,
                relation
        );

        plugin.getStorageManager()
                .markDirty(faction);

        return true;
    }

    public boolean resetAllDefaults(
            Faction faction
    ) {
        if (faction == null
                || faction.isSystemFaction()) {
            return false;
        }

        defaults.applyAll(faction);

        plugin.getStorageManager()
                .markDirty(faction);

        return true;
    }

    // ============================================================
    // Compatibilité V1
    // ============================================================

    public boolean canDo(
            Player player,
            PermissionAction action
    ) {
        if (player == null || action == null) {
            return false;
        }

        FactionCapability capability =
                FactionCapability.fromLegacy(action);

        if (capability != null) {
            return can(
                    player,
                    capability
            );
        }

        FPlayer fPlayer =
                plugin.getFPlayerManager()
                        .findLoaded(
                                player.getUniqueId()
                        );

        if (fPlayer == null
                || !fPlayer.hasFaction()) {
            return false;
        }

        Faction faction =
                plugin.getFactionManager()
                        .getFaction(
                                fPlayer.getFactionId()
                        );

        if (faction == null) {
            return false;
        }

        FactionRole role =
                faction.getRole(
                        player.getUniqueId()
                );

        return canDo(
                faction,
                role,
                action
        );
    }

    public boolean canDo(
            Faction faction,
            FactionRole role,
            PermissionAction action
    ) {
        if (faction == null
                || role == null
                || action == null) {
            return false;
        }

        return faction.hasPermission(
                role,
                action
        );
    }

    public boolean canDoRelation(
            Faction faction,
            Relation relation,
            PermissionAction action
    ) {
        if (faction == null
                || relation == null
                || action == null) {
            return false;
        }

        return faction.hasPermission(
                relation,
                action
        );
    }

    public boolean canDoAt(
            Player player,
            Faction targetFaction,
            PermissionAction action
    ) {
        TerritoryAction territoryAction =
                TerritoryAction.fromLegacy(
                        action
                );

        if (territoryAction == null) {
            return false;
        }

        return service.checkTerritory(
                player,
                targetFaction,
                territoryAction
        ).isAllowed();
    }
}
