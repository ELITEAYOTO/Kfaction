package me.krunsh.kfaction.services;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.ClaimGroup;
import me.krunsh.kfaction.data.ClaimGroup.Rule;
import me.krunsh.kfaction.data.FLocation;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionRole;
import me.krunsh.kfaction.data.PermissionAction;
import me.krunsh.kfaction.data.Relation;
import me.krunsh.kfaction.permissions.FactionCapability;
import me.krunsh.kfaction.permissions.PermissionDecision;
import me.krunsh.kfaction.permissions.PermissionDecision.Reason;
import me.krunsh.kfaction.permissions.TerritoryAction;
import me.krunsh.kfaction.zones.GlobalZoneType;

/**
 * Permission Engine V2 - noyau de décision.
 *
 * Principes :
 * - les rôles sont lus depuis Faction (source canonique de membership) ;
 * - un check de permission ne crée JAMAIS de FPlayer ;
 * - les commands utilisent FactionCapability ;
 * - les protections monde utilisent TerritoryAction ;
 * - PermissionAction reste un pont de compatibilité V1.
 */
public final class PermissionService {

    private final Kfaction plugin;

    private volatile boolean raidAllowBuild;
    private volatile boolean raidAllowBreak;
    private volatile boolean raidAllowContainers;
    private volatile boolean raidAllowTnt;
    private volatile boolean raidAllowSpawners;

    private volatile boolean allowFriendlyFire;
    public PermissionService(Kfaction plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        raidAllowBuild =
                plugin.getConfigManager().getBoolean(
                        "territory.raid.allow-build",
                        true
                );

        raidAllowBreak =
                plugin.getConfigManager().getBoolean(
                        "territory.raid.allow-break",
                        true
                );

        raidAllowContainers =
                plugin.getConfigManager().getBoolean(
                        "territory.raid.allow-containers",
                        true
                );

        raidAllowTnt =
                plugin.getConfigManager().getBoolean(
                        "territory.raid.allow-tnt",
                        true
                );

        raidAllowSpawners =
                plugin.getConfigManager().getBoolean(
                        "territory.raid.allow-spawners",
                        true
                );

        /*
         * Nouveau chemin d'abord, ancien chemin comme fallback de migration.
         */
        boolean legacyFriendlyFire =
                plugin.getConfigManager().getBoolean(
                        "pvp.allow-friendly-fire",
                        false
                );

        allowFriendlyFire =
                plugin.getConfigManager().getBoolean(
                        "relations.friendly-fire.same-faction",
                        legacyFriendlyFire
                );

        if (plugin.getClaimManager() != null
                && plugin.getClaimManager()
                        .getZoneService() != null) {
            plugin.getClaimManager()
                    .getZoneService()
                    .reload();
        }
    }

    // ============================================================
    // Capabilities internes
    // ============================================================

    public boolean can(
            Player player,
            FactionCapability capability
    ) {
        return checkCapability(
                player,
                capability
        ).isAllowed();
    }

    public PermissionDecision checkCapability(
            Player player,
            FactionCapability capability
    ) {
        if (player == null) {
            return PermissionDecision.deny(
                    Reason.NO_PLAYER
            );
        }

        if (capability == null) {
            return PermissionDecision.deny(
                    Reason.INVALID_INPUT
            );
        }

        if (isAdminBypass(player)) {
            return PermissionDecision.allow(
                    Reason.ADMIN_BYPASS
            );
        }

        FPlayer fPlayer =
                plugin.getFPlayerManager()
                        .findLoaded(
                                player.getUniqueId()
                        );

        if (fPlayer == null) {
            return PermissionDecision.deny(
                    Reason.NO_PROFILE
            );
        }

        if (!fPlayer.hasFaction()) {
            return PermissionDecision.deny(
                    Reason.NO_FACTION
            );
        }

        Faction faction =
                plugin.getFactionManager()
                        .getFaction(
                                fPlayer.getFactionId()
                        );

        if (faction == null
                || faction.isSystemFaction()) {
            return PermissionDecision.deny(
                    Reason.FACTION_NOT_FOUND
            );
        }

        return checkCapability(
                faction,
                player.getUniqueId(),
                capability
        );
    }

    public PermissionDecision checkCapability(
            Faction faction,
            UUID memberId,
            FactionCapability capability
    ) {
        if (faction == null
                || memberId == null
                || capability == null) {
            return PermissionDecision.deny(
                    Reason.INVALID_INPUT
            );
        }

        FactionRole role =
                faction.getRole(memberId);

        if (role == null
                || !faction.isMember(memberId)) {
            return PermissionDecision.deny(
                    Reason.MEMBERSHIP_MISMATCH,
                    faction.getId(),
                    role,
                    null
            );
        }

        PermissionAction[] accepted =
                capability.getAcceptedLegacyActions();

        if (accepted.length == 0) {
            return PermissionDecision.deny(
                    Reason.UNSUPPORTED_ACTION,
                    faction.getId(),
                    role,
                    null
            );
        }

        if (hasAnyPermission(
                faction,
                role,
                accepted
        )) {
            return PermissionDecision.allow(
                    Reason.OWN_ROLE,
                    faction.getId(),
                    role,
                    Relation.MEMBER
            );
        }

        return PermissionDecision.deny(
                Reason.ROLE_DENIED,
                faction.getId(),
                role,
                Relation.MEMBER
        );
    }

    // ============================================================
    // Territoire
    // ============================================================

    public boolean canTerritory(
            Player player,
            Location location,
            TerritoryAction action
    ) {
        return checkTerritory(
                player,
                location,
                action
        ).isAllowed();
    }

    public PermissionDecision checkTerritory(
            Player player,
            Location location,
            TerritoryAction action
    ) {
        if (player == null) {
            return PermissionDecision.deny(
                    Reason.NO_PLAYER
            );
        }

        if (location == null || action == null) {
            return PermissionDecision.deny(
                    Reason.INVALID_INPUT
            );
        }

        if (isAdminBypass(player)) {
            return PermissionDecision.allow(
                    Reason.ADMIN_BYPASS
            );
        }

        FLocation factionLocation =
                new FLocation(location);

        String globalZoneId =
                plugin.getClaimManager()
                        .getZoneService()
                        .getZoneIdAt(
                                factionLocation
                        );

        if (globalZoneId != null) {
            boolean allowed =
                    plugin.getClaimManager()
                            .getZoneService()
                            .isActionAllowed(
                                    globalZoneId,
                                    action
                            );

            Reason reason;

            if (GlobalZoneType.SAFEZONE
                    .getConfigKey()
                    .equals(globalZoneId)) {
                reason =
                        allowed
                                ? Reason.SAFEZONE_RULE
                                : Reason.ZONE_DENIED;
            } else if (GlobalZoneType.WARZONE
                    .getConfigKey()
                    .equals(globalZoneId)) {
                reason =
                        allowed
                                ? Reason.WARZONE_RULE
                                : Reason.ZONE_DENIED;
            } else {
                reason =
                        allowed
                                ? Reason.GLOBAL_ZONE_RULE
                                : Reason.ZONE_DENIED;
            }

            String legacyFactionId = null;

            GlobalZoneType legacy =
                    GlobalZoneType.parse(
                            globalZoneId
                    );

            if (legacy != null) {
                legacyFactionId =
                        legacy.getLegacyFactionId();
            }

            return allowed
                    ? PermissionDecision.allow(
                            reason,
                            legacyFactionId,
                            null,
                            null
                    )
                    : PermissionDecision.deny(
                            reason,
                            legacyFactionId,
                            null,
                            null
                    );
        }

        Faction territory =
                plugin.getClaimManager()
                        .getFactionAt(
                                factionLocation
                        );

        ClaimGroup claimGroup =
                territory != null
                        && !territory.isSystemFaction()
                        ? territory.getClaimGroupAt(
                                factionLocation
                        )
                        : null;

        return checkTerritoryInternal(
                player,
                territory,
                claimGroup,
                action
        );
    }

    /**
     * Variante sans Location utilisée par l'adaptateur PermissionManager V1.
     *
     * Sans location il est impossible de résoudre un Claim Group: cette
     * méthode conserve donc l'ACL générale de faction.
     */
    public PermissionDecision checkTerritory(
            Player player,
            Faction territory,
            TerritoryAction action
    ) {
        return checkTerritoryInternal(
                player,
                territory,
                null,
                action
        );
    }

    private PermissionDecision checkTerritoryInternal(
            Player player,
            Faction territory,
            ClaimGroup claimGroup,
            TerritoryAction action
    ) {
        if (player == null) {
            return PermissionDecision.deny(
                    Reason.NO_PLAYER
            );
        }

        if (territory == null || action == null) {
            return PermissionDecision.deny(
                    Reason.INVALID_INPUT
            );
        }

        if (isAdminBypass(player)) {
            return PermissionDecision.allow(
                    Reason.ADMIN_BYPASS
            );
        }

        if (territory.isWilderness()) {
            return PermissionDecision.allow(
                    Reason.WILDERNESS,
                    territory.getId(),
                    null,
                    Relation.NEUTRAL
            );
        }

        if (territory.isSafezone()) {
            boolean allowed =
                    canActionInSafezone(action);

            return allowed
                    ? PermissionDecision.allow(
                            Reason.SAFEZONE_RULE,
                            territory.getId(),
                            null,
                            null
                    )
                    : PermissionDecision.deny(
                            Reason.ZONE_DENIED,
                            territory.getId(),
                            null,
                            null
                    );
        }

        if (territory.isWarzone()) {
            boolean allowed =
                    canActionInWarzone(action);

            return allowed
                    ? PermissionDecision.allow(
                            Reason.WARZONE_RULE,
                            territory.getId(),
                            null,
                            null
                    )
                    : PermissionDecision.deny(
                            Reason.ZONE_DENIED,
                            territory.getId(),
                            null,
                            null
                    );
        }

        /*
         * Sans Claim Group, ENTER garde la compatibilité passive.
         * Avec un groupe, une règle explicite ALLOW/DENY peut désormais
         * prendre la main; INHERIT retombe ensuite sur PASSIVE_MOVEMENT.
         */
        if (action == TerritoryAction.ENTER
                && claimGroup == null) {
            return PermissionDecision.allow(
                    Reason.PASSIVE_MOVEMENT,
                    territory.getId(),
                    null,
                    null
            );
        }

        FPlayer fPlayer =
                plugin.getFPlayerManager()
                        .findLoaded(
                                player.getUniqueId()
                        );

        if (fPlayer == null) {
            return checkForeignTerritory(
                    territory,
                    claimGroup,
                    null,
                    Relation.NEUTRAL,
                    action
            );
        }

        Faction playerFaction = null;

        if (fPlayer.hasFaction()) {
            playerFaction =
                    plugin.getFactionManager()
                            .getFaction(
                                    fPlayer.getFactionId()
                            );
        }

        if (playerFaction == null) {
            return checkForeignTerritory(
                    territory,
                    claimGroup,
                    null,
                    Relation.NEUTRAL,
                    action
            );
        }

        if (playerFaction.getId()
                .equals(territory.getId())) {
            FactionRole canonicalRole =
                    territory.getRole(
                            player.getUniqueId()
                    );

            if (canonicalRole == null
                    || !territory.isMember(
                            player.getUniqueId()
                    )) {
                return PermissionDecision.deny(
                        Reason.MEMBERSHIP_MISMATCH,
                        territory.getId(),
                        canonicalRole,
                        Relation.MEMBER
                );
            }

            if (claimGroup != null) {
                Rule groupRule =
                        claimGroup.getRoleRule(
                                canonicalRole,
                                action
                        );

                if (groupRule == Rule.ALLOW) {
                    return PermissionDecision.allow(
                            Reason.CLAIM_GROUP_ALLOW,
                            territory.getId(),
                            canonicalRole,
                            Relation.MEMBER,
                            claimGroup.getId()
                    );
                }

                if (groupRule == Rule.DENY) {
                    return PermissionDecision.deny(
                            Reason.CLAIM_GROUP_DENY,
                            territory.getId(),
                            canonicalRole,
                            Relation.MEMBER,
                            claimGroup.getId()
                    );
                }
            }

            PermissionAction[] accepted =
                    action.getAcceptedLegacyActions();

            if (accepted.length == 0) {
                return ownFallbackDecision(
                        territory,
                        canonicalRole,
                        action
                );
            }

            if (hasAnyPermission(
                    territory,
                    canonicalRole,
                    accepted
            )) {
                return PermissionDecision.allow(
                        Reason.OWN_ROLE,
                        territory.getId(),
                        canonicalRole,
                        Relation.MEMBER
                );
            }

            return PermissionDecision.deny(
                    Reason.ROLE_DENIED,
                    territory.getId(),
                    canonicalRole,
                    Relation.MEMBER
            );
        }

        Relation relation =
                resolveEffectiveRelation(
                        territory,
                        playerFaction
                );

        return checkForeignTerritory(
                territory,
                claimGroup,
                playerFaction,
                relation,
                action
        );
    }

    private PermissionDecision checkForeignTerritory(
            Faction territory,
            ClaimGroup claimGroup,
            Faction actorFaction,
            Relation relation,
            TerritoryAction action
    ) {
        if (relation == Relation.ENEMY
                && plugin.getPermissionManager() != null
                && plugin.getPermissionManager()
                        .getGraceService()
                        .blocksTerritoryAction(
                                relation,
                                action
                        )) {
            return PermissionDecision.deny(
                    Reason.GRACE_PROTECTION,
                    territory.getId(),
                    null,
                    relation
            );
        }

        if (relation == Relation.ENEMY
                && plugin.getClaimManager()
                        .isRaidable(territory)) {
            boolean allowed =
                    canActionWhenRaiding(action);

            return allowed
                    ? PermissionDecision.allow(
                            Reason.RAID_RULE,
                            territory.getId(),
                            null,
                            relation
                    )
                    : PermissionDecision.deny(
                            Reason.RELATION_DENIED,
                            territory.getId(),
                            null,
                            relation
                    );
        }

        if (claimGroup != null) {
            Rule groupRule =
                    claimGroup.getRelationRule(
                            relation,
                            action
                    );

            if (groupRule == Rule.ALLOW) {
                return PermissionDecision.allow(
                        Reason.CLAIM_GROUP_ALLOW,
                        territory.getId(),
                        null,
                        relation,
                        claimGroup.getId()
                );
            }

            if (groupRule == Rule.DENY) {
                return PermissionDecision.deny(
                        Reason.CLAIM_GROUP_DENY,
                        territory.getId(),
                        null,
                        relation,
                        claimGroup.getId()
                );
            }
        }

        PermissionAction[] accepted =
                action.getAcceptedLegacyActions();

        if (accepted.length == 0) {
            return foreignFallbackDecision(
                    territory,
                    relation,
                    action
            );
        }

        if (hasAnyPermission(
                territory,
                relation,
                accepted
        )) {
            return PermissionDecision.allow(
                    Reason.RELATION_ACL,
                    territory.getId(),
                    null,
                    relation
            );
        }

        return PermissionDecision.deny(
                Reason.RELATION_DENIED,
                territory.getId(),
                null,
                relation
        );
    }

    private PermissionDecision ownFallbackDecision(
            Faction territory,
            FactionRole role,
            TerritoryAction action
    ) {
        switch (action) {
            case ENTER:
            case TELEPORT_IN:
            case ENDER_PEARL:
            case VEHICLE:
                return PermissionDecision.allow(
                        Reason.PASSIVE_MOVEMENT,
                        territory.getId(),
                        role,
                        Relation.MEMBER
                );
            default:
                return PermissionDecision.deny(
                        Reason.UNSUPPORTED_ACTION,
                        territory.getId(),
                        role,
                        Relation.MEMBER
                );
        }
    }

    private PermissionDecision foreignFallbackDecision(
            Faction territory,
            Relation relation,
            TerritoryAction action
    ) {
        if (action == TerritoryAction.ENTER) {
            return PermissionDecision.allow(
                    Reason.PASSIVE_MOVEMENT,
                    territory.getId(),
                    null,
                    relation
            );
        }

        return PermissionDecision.deny(
                Reason.UNSUPPORTED_ACTION,
                territory.getId(),
                null,
                relation
        );
    }

    private boolean canActionInSafezone(
            TerritoryAction action
    ) {
        return plugin.getClaimManager()
                .getZoneService()
                .isActionAllowed(
                        GlobalZoneType.SAFEZONE
                                .getConfigKey(),
                        action
                );
    }

    private boolean canActionInWarzone(
            TerritoryAction action
    ) {
        return plugin.getClaimManager()
                .getZoneService()
                .isActionAllowed(
                        GlobalZoneType.WARZONE
                                .getConfigKey(),
                        action
                );
    }

    private boolean canActionWhenRaiding(
            TerritoryAction action
    ) {
        switch (action) {
            case BLOCK_PLACE:
            case BUCKET_EMPTY:
                return raidAllowBuild;

            case BLOCK_BREAK:
            case BUCKET_FILL:
                return raidAllowBreak;

            case CONTAINER_OPEN:
            case CONTAINER_DEPOSIT:
            case CONTAINER_WITHDRAW:
            case HOPPER:
            case FURNACE:
            case BREWING:
            case ANVIL:
            case ENCHANT:
                return raidAllowContainers;

            case TNT_PLACE:
            case TNT_IGNITE:
            case FLINT_AND_STEEL:
                return raidAllowTnt;

            case SPAWNER_PLACE:
            case SPAWNER_BREAK:
            case SPAWNER_INTERACT:
                return raidAllowSpawners;

            default:
                return false;
        }
    }

    private Relation resolveEffectiveRelation(
            Faction territory,
            Faction actorFaction
    ) {
        if (territory == null || actorFaction == null) {
            return Relation.NEUTRAL;
        }

        Relation territoryPerspective =
                territory.getRelationTo(
                        actorFaction
                );

        Relation actorPerspective =
                actorFaction.getRelationTo(
                        territory
                );

        /*
         * ENEMY peut être unilatéral dans le V1.
         * Pour le raid, une déclaration ennemie d'un côté doit donc être visible.
         */
        if (territoryPerspective == Relation.ENEMY
                || actorPerspective == Relation.ENEMY) {
            return Relation.ENEMY;
        }

        if (territoryPerspective != Relation.NEUTRAL) {
            return territoryPerspective;
        }

        return actorPerspective != null
                ? actorPerspective
                : Relation.NEUTRAL;
    }

    private boolean hasAnyPermission(
            Faction faction,
            FactionRole role,
            PermissionAction[] accepted
    ) {
        if (faction == null
                || role == null
                || accepted == null) {
            return false;
        }

        for (PermissionAction action : accepted) {
            if (action != null
                    && faction.hasPermission(
                            role,
                            action
                    )) {
                return true;
            }
        }

        return false;
    }

    private boolean hasAnyPermission(
            Faction faction,
            Relation relation,
            PermissionAction[] accepted
    ) {
        if (faction == null
                || relation == null
                || accepted == null) {
            return false;
        }

        for (PermissionAction action : accepted) {
            if (action != null
                    && faction.hasPermission(
                            relation,
                            action
                    )) {
                return true;
            }
        }

        return false;
    }

    // ============================================================
    // PvP
    // ============================================================

    public boolean canPvP(
            Player attacker,
            Player defender
    ) {
        if (attacker == null || defender == null) {
            return false;
        }

        String defenderZone =
                plugin.getClaimManager()
                        .getZoneService()
                        .getZoneIdAt(
                                new FLocation(
                                        defender.getLocation()
                                )
                        );

        String attackerZone =
                plugin.getClaimManager()
                        .getZoneService()
                        .getZoneIdAt(
                                new FLocation(
                                        attacker.getLocation()
                                )
                        );

        if (defenderZone != null
                || attackerZone != null) {
            boolean defenderAllows =
                    defenderZone == null
                            || plugin.getClaimManager()
                                    .getZoneService()
                                    .isPvpAllowed(
                                            defenderZone
                                    );

            boolean attackerAllows =
                    attackerZone == null
                            || plugin.getClaimManager()
                                    .getZoneService()
                                    .isPvpAllowed(
                                            attackerZone
                                    );

            return defenderAllows
                    && attackerAllows;
        }

        Faction attackerFaction =
                getLoadedPlayerFaction(attacker);

        Faction defenderFaction =
                getLoadedPlayerFaction(defender);

        if (attackerFaction == null
                || defenderFaction == null) {
            return true;
        }

        if (attackerFaction.getId()
                .equals(defenderFaction.getId())) {
            return allowFriendlyFire;
        }

        Relation relation =
                resolveEffectiveRelation(
                        defenderFaction,
                        attackerFaction
                );

        if (relation == Relation.ENEMY
                && plugin.getPermissionManager() != null
                && plugin.getPermissionManager()
                        .getGraceService()
                        .blocksEnemyPvp()) {
            return false;
        }

        return relation.isPvPAllowed();
    }

    public double getDamageMultiplier(
            Player attacker,
            Player defender
    ) {
        Faction attackerFaction =
                getLoadedPlayerFaction(attacker);

        Faction defenderFaction =
                getLoadedPlayerFaction(defender);

        if (attackerFaction == null
                || defenderFaction == null) {
            return 1.0D;
        }

        Relation relation =
                resolveEffectiveRelation(
                        defenderFaction,
                        attackerFaction
                );

        return relation.getDamageMultiplier();
    }

    // ============================================================
    // Helpers
    // ============================================================

    public boolean isAdminBypass(Player player) {
        if (player == null) {
            return false;
        }

        if (player.hasPermission(
                "kfaction.admin.bypass"
        )) {
            return true;
        }

        FPlayer fPlayer =
                plugin.getFPlayerManager()
                        .findLoaded(
                                player.getUniqueId()
                        );

        return fPlayer != null
                && fPlayer.isBypassing();
    }

    private Faction getLoadedPlayerFaction(
            Player player
    ) {
        if (player == null) {
            return null;
        }

        FPlayer fPlayer =
                plugin.getFPlayerManager()
                        .findLoaded(
                                player.getUniqueId()
                        );

        if (fPlayer == null
                || !fPlayer.hasFaction()) {
            return null;
        }

        return plugin.getFactionManager()
                .getFaction(
                        fPlayer.getFactionId()
                );
    }
}
