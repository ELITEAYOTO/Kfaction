package me.krunsh.kfaction.api.v2;

import java.util.UUID;

import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.data.FactionRole;

/**
 * Parcours joueur validés. Une GUI appelle cette façade, jamais une commande
 * `/f` et jamais une mutation trusted de {@link KfactionApiV2}.
 */
public interface KfactionPlayerActions {

    ApiResult<FactionView> createFaction(UUID actorId, String name, OperationContext context);
    ApiResult<FactionInviteView> invitePlayer(UUID actorId, UUID targetId, OperationContext context);
    ApiResult<MemberView> acceptInvite(UUID actorId, String factionId, OperationContext context);
    ApiResult<Void> declineInvite(UUID actorId, String factionId, OperationContext context);
    ApiResult<Void> leaveFaction(UUID actorId, OperationContext context);
    ApiResult<Void> kickMember(UUID actorId, UUID targetId, OperationContext context);
    ApiResult<MemberView> changeMemberRole(UUID actorId, UUID targetId, FactionRole role, OperationContext context);
    ApiResult<MemberView> transferLeadership(UUID actorId, UUID targetId, OperationContext context);
    ApiResult<Integer> disbandFaction(UUID actorId, OperationContext context);

    ApiResult<ClaimResultView> claim(UUID actorId, ChunkView chunk, OperationContext context);
    ApiResult<ClaimResultView> claimRadius(UUID actorId, ChunkView center, int radius, OperationContext context);
    ApiResult<ClaimResultView> claimFill(UUID actorId, ChunkView start, OperationContext context);
    ApiResult<UnclaimResultView> unclaim(UUID actorId, ChunkView chunk, OperationContext context);
    ApiResult<UnclaimResultView> unclaimRadius(UUID actorId, ChunkView center, int radius, OperationContext context);
    ApiResult<UnclaimResultView> unclaimAll(UUID actorId, OperationContext context);

    ApiResult<RelationView> requestRelation(UUID actorId, String targetFactionId, String relation, OperationContext context);
    ApiResult<RelationView> acceptRelation(UUID actorId, String sourceFactionId, String relation, OperationContext context);
    ApiResult<Void> declineRelation(UUID actorId, String sourceFactionId, String relation, OperationContext context);
    ApiResult<RelationView> setNeutral(UUID actorId, String targetFactionId, OperationContext context);

    ApiResult<Long> depositBank(UUID actorId, long amountMinor, OperationContext context);
    ApiResult<Long> withdrawBank(UUID actorId, long amountMinor, OperationContext context);

    ApiResult<PositionView> setHome(UUID actorId, PositionView position, OperationContext context);
    ApiResult<Void> teleportHome(UUID actorId, OperationContext context);
    ApiResult<WarpView> createWarp(UUID actorId, String name, PositionView position, OperationContext context);
    ApiResult<WarpView> deleteWarp(UUID actorId, String name, OperationContext context);
    ApiResult<Void> teleportWarp(UUID actorId, String name, String password, OperationContext context);

    ApiResult<Boolean> setRolePermission(UUID actorId, FactionRole role, String permissionKey,
                                         boolean allowed, OperationContext context);
    ApiResult<Boolean> setRelationPermission(UUID actorId, String relation, String permissionKey,
                                             boolean allowed, OperationContext context);

    ApiResult<ProgressionView> selectQuestCategory(UUID actorId, String categoryId, OperationContext context);
    ApiResult<ProgressionView> claimProgressionReward(UUID actorId, int level, OperationContext context);
}
