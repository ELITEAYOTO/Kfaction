package me.krunsh.kfaction.api.v2;

import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.data.FLocation;
import me.krunsh.kfaction.data.FactionRole;
import me.krunsh.kfaction.permissions.TerritoryAction;
import me.krunsh.kfaction.zones.GlobalZoneType;

/**
 * Contrat public Kfaction API 2.x.
 *
 * Règle:
 * - lectures -> snapshots immuables;
 * - mutations -> ApiResult;
 * - jamais de Faction/FPlayer live;
 * - jamais de Manager interne exposé.
 */
public interface KfactionApiV2 {

    String API_VERSION = "2.2.0";
    int API_MAJOR = 2;

    String getApiVersion();

    int getApiMajor();

    int getDefaultMaxMembers();

    int getDefaultMaxWarps();

    // ============================================================
    // Read API
    // ============================================================

    FactionView getFaction(
            String factionId
    );

    FactionView findFaction(
            String idNameOrTag
    );

    List<FactionView> getFactions();

    PlayerView getPlayer(
            UUID playerId
    );

    FactionView getPlayerFaction(
            UUID playerId
    );

    String getRelation(
            String firstFactionId,
            String secondFactionId
    );

    TerritoryView getTerritory(
            FLocation location,
            UUID viewerId
    );

    ZoneView getGlobalZoneAt(
            FLocation location
    );

    List<ZoneView> getGlobalZones();

    ProgressionView getProgression(
            String factionId
    );

    List<QuestView> getProgressionQuests(
            String factionId
    );

    List<RewardLevelView> getRewardLevels(
            String factionId
    );

    GraceView getGrace();

    PermissionView checkTerritory(
            Player player,
            Location location,
            TerritoryAction action
    );

    boolean canPvp(
            Player attacker,
            Player defender
    );

    /**
     * Compatibilité d'intégration pour les placeholders legacy perm_*.
     *
     * @return Boolean.TRUE/FALSE si role+permission valides,
     *         null si entrée invalide/inconnue.
     */
    Boolean getRolePermission(
            String factionId,
            String role,
            String permissionKey
    );

    /**
     * Compatibilité d'intégration pour ACL relation legacy.
     */
    Boolean getRelationPermission(
            String factionId,
            String relation,
            String permissionKey
    );

    // ============================================================
    // Trusted mutation API
    // ============================================================
    //
    // Ces opérations font respecter les invariants du service métier.
    // Elles n'imitent PAS une commande joueur complète
    // (coût économique de /f create, texte GUI, etc.).

    ApiResult<MemberView> joinMember(
            String factionId,
            UUID playerId,
            FactionRole role,
            OperationContext context
    );

    ApiResult<Void> removeMember(
            String factionId,
            UUID playerId,
            OperationContext context
    );

    ApiResult<MemberView> setRole(
            String factionId,
            UUID playerId,
            FactionRole role,
            OperationContext context
    );

    ApiResult<MemberView> transferLeadership(
            String factionId,
            UUID playerId,
            OperationContext context
    );

    ApiResult<Integer> disbandFaction(
            String factionId,
            OperationContext context
    );

    ApiResult<ClaimResultView> claimSingle(
            Player actor,
            String factionId,
            FLocation location,
            OperationContext context
    );

    ApiResult<ClaimResultView> claimRadius(
            Player actor,
            String factionId,
            FLocation center,
            int radius,
            OperationContext context
    );

    ApiResult<ClaimResultView> claimFill(
            Player actor,
            String factionId,
            FLocation start,
            OperationContext context
    );

    ApiResult<UnclaimResultView> unclaimSingle(
            Player actor,
            String factionId,
            FLocation location,
            OperationContext context
    );

    ApiResult<UnclaimResultView> unclaimRadius(
            Player actor,
            String factionId,
            FLocation center,
            int radius,
            OperationContext context
    );

    ApiResult<UnclaimResultView> unclaimAll(
            Player actor,
            String factionId,
            OperationContext context
    );

    ApiResult<String> setGlobalZoneById(
            FLocation location,
            String zoneId,
            OperationContext context
    );

    ApiResult<String> setGlobalZone(
            FLocation location,
            GlobalZoneType type,
            OperationContext context
    );

    ApiResult<String> clearGlobalZoneById(
            FLocation location,
            String expectedZoneId,
            OperationContext context
    );

    ApiResult<String> clearGlobalZone(
            FLocation location,
            GlobalZoneType expectedType,
            OperationContext context
    );

    ApiResult<Boolean> reloadProgression(
            OperationContext context
    );
}
