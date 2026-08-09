package me.krunsh.kfaction.api.v2;

import java.util.Collections;
import java.util.EnumSet;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.api.event.FactionCreateEvent;
import me.krunsh.kfaction.api.v2.event.FactionField;
import me.krunsh.kfaction.api.v2.event.FactionSnapshotChangedEvent;
import me.krunsh.kfaction.api.v2.event.PlayerFactionChangedEvent;
import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.core.operation.OperationResult;
import me.krunsh.kfaction.data.FLocation;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionRole;
import me.krunsh.kfaction.data.FactionWarp;
import me.krunsh.kfaction.data.PermissionAction;
import me.krunsh.kfaction.data.Relation;
import me.krunsh.kfaction.data.StoredLocation;
import me.krunsh.kfaction.economy.EconomyTransactionResult;
import me.krunsh.kfaction.economy.MoneyAmount;
import me.krunsh.kfaction.hooks.VaultHook;
import me.krunsh.kfaction.managers.RelationManager.RelationResult;
import me.krunsh.kfaction.services.EconomyService;
import me.krunsh.kfaction.services.FactionLifecycleService;
import me.krunsh.kfaction.services.HomeWarpService;
import me.krunsh.kfaction.services.MembershipService;
import me.krunsh.kfaction.services.MembershipService.ChangeReason;
import me.krunsh.kfaction.services.RoleService;

/** Implémentation officielle des parcours joueur 2.3. */
public final class KfactionPlayerActionsProvider implements KfactionPlayerActions {

    private final Kfaction plugin;
    private final KfactionApiProvider api;
    private final MembershipService membership;
    private final RoleService roles;
    private final FactionLifecycleService lifecycle;

    public KfactionPlayerActionsProvider(Kfaction plugin, KfactionApiProvider api) {
        if (plugin == null || api == null) {
            throw new IllegalArgumentException("plugin and api are required");
        }
        this.plugin = plugin;
        this.api = api;
        this.membership = new MembershipService(plugin);
        this.roles = new RoleService(plugin);
        this.lifecycle = new FactionLifecycleService(plugin);
    }

    @Override
    public ApiResult<FactionView> createFaction(UUID actorId, String name, OperationContext context) {
        ApiResult<Player> gate = actor(actorId, context);
        if (gate.isFailure()) return copy(gate);
        Player actor = gate.getValue();
        FPlayer fPlayer = plugin.getFPlayerManager().getOrCreate(actorId);
        if (fPlayer == null) return failed("create.player-unavailable");
        if (fPlayer.hasFaction()) return conflict("create.already-in-faction");
        if (!plugin.getFactionManager().isValidName(name)) return invalid("create.invalid-name");
        if (!plugin.getFactionManager().isNameAvailable(name)) return conflict("create.name-taken");

        double cost = Math.max(0.0D, plugin.getConfigManager().getDouble(
                "economy.faction-create-cost", 0.0D));
        VaultHook vault = plugin.getHookManager() != null && plugin.getHookManager().hasVault()
                ? plugin.getHookManager().getVaultHook() : null;
        if (cost > 0.0D && (vault == null || !vault.isEnabled())) {
            return unavailable("create.economy-unavailable");
        }
        if (cost > 0.0D && !vault.has(actor, cost)) {
            return forbidden("create.not-enough-money");
        }

        FactionCreateEvent legacy = new FactionCreateEvent(actor, name);
        Bukkit.getPluginManager().callEvent(legacy);
        if (legacy.isCancelled()) return cancelled("create.cancelled", legacy.getCancelReason());

        boolean charged = cost <= 0.0D || vault.withdraw(actor, cost);
        if (!charged) return failed("create.transaction-failed");

        Faction faction = plugin.getFactionManager().createFaction(name, actorId);
        if (faction == null) {
            if (cost > 0.0D) vault.deposit(actor, cost);
            return failed("create.failed");
        }
        legacy.setFaction(faction);
        changed(faction, context, EnumSet.of(FactionField.SNAPSHOT, FactionField.MEMBERS), actorId);
        Bukkit.getPluginManager().callEvent(new PlayerFactionChangedEvent(
                actorId, null, faction.getId(), faction.getRevision(), context));
        return ApiResult.success(api.getFaction(faction.getId()));
    }

    @Override
    public ApiResult<FactionInviteView> invitePlayer(UUID actorId, UUID targetId, OperationContext context) {
        ApiResult<Player> gate = actor(actorId, context);
        if (gate.isFailure()) return copy(gate);
        if (targetId == null || targetId.equals(actorId)) return invalid("invite.invalid-target");
        Faction faction = actorFaction(actorId);
        if (faction == null) return notFound("invite.no-faction");
        if (!faction.hasPermission(actorId, PermissionAction.INVITE)) return forbidden("invite.forbidden");
        FPlayer target = plugin.getFPlayerManager().getOrCreate(targetId);
        if (target == null) return notFound("invite.target-not-found");
        if (target.hasFaction()) return conflict("invite.target-has-faction");

        faction.addInvite(targetId);
        plugin.getStorageManager().markDirty(faction);
        long created = faction.getInvitesSnapshot().get(targetId);
        long lifetime = Math.max(1L, plugin.getConfigManager().getLong(
                "factions.invite-expiration", 300L)) * 1000L;
        changed(faction, context, EnumSet.of(FactionField.MEMBERS), actorId, targetId);
        return ApiResult.success(new FactionInviteView(
                faction.getId(), targetId, created, created + lifetime, FactionInviteView.Status.PENDING));
    }

    @Override
    public ApiResult<MemberView> acceptInvite(UUID actorId, String factionId, OperationContext context) {
        ApiResult<Player> gate = actor(actorId, context);
        if (gate.isFailure()) return copy(gate);
        if (actorFaction(actorId) != null) return conflict("join.already-in-faction");
        Faction faction = plugin.getFactionManager().getFaction(factionId);
        if (faction == null || faction.isSystemFaction()) return notFound("join.faction-not-found");
        long expiration = Math.max(1L, plugin.getConfigManager().getLong(
                "factions.invite-expiration", 300L)) * 1000L;
        if (!faction.isOpen() && !faction.hasInvite(actorId, expiration)) {
            return forbidden("join.invite-required");
        }
        OperationResult<FactionRole> result = membership.join(
                faction, actorId, FactionRole.RECRUIT, ChangeReason.JOIN, context, false);
        if (!result.isSuccessful()) return from(result);
        faction.removeInvite(actorId);
        plugin.getStorageManager().markDirty(faction);
        changed(faction, context, EnumSet.of(FactionField.MEMBERS), actorId);
        Bukkit.getPluginManager().callEvent(new PlayerFactionChangedEvent(
                actorId, null, faction.getId(), faction.getRevision(), context));
        return ApiResult.success(member(faction.getId(), actorId));
    }

    @Override
    public ApiResult<Void> declineInvite(UUID actorId, String factionId, OperationContext context) {
        ApiResult<Player> gate = actor(actorId, context);
        if (gate.isFailure()) return copy(gate);
        Faction faction = plugin.getFactionManager().getFaction(factionId);
        if (faction == null) return notFound("invite.faction-not-found");
        if (!faction.getInvitesSnapshot().containsKey(actorId)) return noChange("invite.not-pending");
        faction.removeInvite(actorId);
        faction.updateActivity();
        plugin.getStorageManager().markDirty(faction);
        changed(faction, context, EnumSet.of(FactionField.MEMBERS), actorId);
        return ApiResult.success(null);
    }

    @Override
    public ApiResult<Void> leaveFaction(UUID actorId, OperationContext context) {
        ApiResult<Player> gate = actor(actorId, context);
        if (gate.isFailure()) return copy(gate);
        Faction faction = actorFaction(actorId);
        if (faction == null) return notFound("leave.no-faction");
        if (faction.isLeader(actorId)) return forbidden("leave.leader-must-transfer");
        String oldId = faction.getId();
        OperationResult<Void> result = membership.remove(
                faction, actorId, ChangeReason.LEAVE, context, false);
        if (!result.isSuccessful()) return from(result);
        changed(faction, context, EnumSet.of(FactionField.MEMBERS), actorId);
        Bukkit.getPluginManager().callEvent(new PlayerFactionChangedEvent(
                actorId, oldId, null, faction.getRevision(), context));
        return ApiResult.success(null);
    }

    @Override
    public ApiResult<Void> kickMember(UUID actorId, UUID targetId, OperationContext context) {
        ApiResult<Player> gate = actor(actorId, context);
        if (gate.isFailure()) return copy(gate);
        Faction faction = actorFaction(actorId);
        if (faction == null) return notFound("kick.no-faction");
        if (targetId == null || !faction.isMember(targetId)) return notFound("kick.target-not-member");
        if (!faction.hasPermission(actorId, PermissionAction.KICK)) return forbidden("kick.forbidden");
        FactionRole actorRole = faction.getRole(actorId);
        FactionRole targetRole = faction.getRole(targetId);
        if (faction.isLeader(targetId) || actorRole == null || targetRole == null
                || !actorRole.isHigherThan(targetRole)) return forbidden("kick.hierarchy");
        OperationResult<Void> result = membership.remove(
                faction, targetId, ChangeReason.KICK, context, false);
        if (!result.isSuccessful()) return from(result);
        changed(faction, context, EnumSet.of(FactionField.MEMBERS), actorId, targetId);
        Bukkit.getPluginManager().callEvent(new PlayerFactionChangedEvent(
                targetId, faction.getId(), null, faction.getRevision(), context));
        return ApiResult.success(null);
    }

    @Override
    public ApiResult<MemberView> changeMemberRole(UUID actorId, UUID targetId,
                                                   FactionRole role, OperationContext context) {
        ApiResult<Player> gate = actor(actorId, context);
        if (gate.isFailure()) return copy(gate);
        Faction faction = actorFaction(actorId);
        if (faction == null) return notFound("role.no-faction");
        if (targetId == null || role == null || !faction.isMember(targetId)) {
            return invalid("role.invalid-target");
        }
        if (role == FactionRole.LEADER) return forbidden("role.use-transfer");
        FactionRole actorRole = faction.getRole(actorId);
        FactionRole oldRole = faction.getRole(targetId);
        if (actorRole == null || oldRole == null || !actorRole.isHigherThan(oldRole)
                || !actorRole.isHigherThan(role)) return forbidden("role.hierarchy");
        PermissionAction required = role.getPriority() > oldRole.getPriority()
                ? PermissionAction.PROMOTE : PermissionAction.DEMOTE;
        if (!faction.hasPermission(actorId, required)) return forbidden("role.forbidden");
        OperationResult<FactionRole> result = roles.setRole(faction, targetId, role, context);
        if (!result.isSuccessful()) return from(result);
        changed(faction, context, EnumSet.of(FactionField.MEMBERS), actorId, targetId);
        return ApiResult.success(member(faction.getId(), targetId));
    }

    @Override
    public ApiResult<MemberView> transferLeadership(UUID actorId, UUID targetId, OperationContext context) {
        ApiResult<Player> gate = actor(actorId, context);
        if (gate.isFailure()) return copy(gate);
        Faction faction = actorFaction(actorId);
        if (faction == null) return notFound("leader.no-faction");
        if (!faction.isLeader(actorId)) return forbidden("leader.not-leader");
        OperationResult<FactionRole> result = roles.transferLeadership(faction, targetId, context);
        if (!result.isSuccessful()) return from(result);
        changed(faction, context, EnumSet.of(FactionField.MEMBERS), actorId, targetId);
        return ApiResult.success(member(faction.getId(), targetId));
    }

    @Override
    public ApiResult<Integer> disbandFaction(UUID actorId, OperationContext context) {
        ApiResult<Player> gate = actor(actorId, context);
        if (gate.isFailure()) return copy(gate);
        Faction faction = actorFaction(actorId);
        if (faction == null) return notFound("disband.no-faction");
        if (!faction.isLeader(actorId)) return forbidden("disband.not-leader");
        long revision = faction.getRevision() + 1L;
        String factionId = faction.getId();
        OperationResult<Integer> result = lifecycle.disband(faction, context);
        if (!result.isSuccessful()) return from(result);
        Bukkit.getPluginManager().callEvent(new FactionSnapshotChangedEvent(
                factionId, revision, EnumSet.allOf(FactionField.class),
                Collections.singleton(actorId), context));
        return ApiResult.from(result, result.getValue());
    }

    @Override
    public ApiResult<ClaimResultView> claim(UUID actorId, ChunkView chunk, OperationContext context) {
        ApiResult<Player> gate = actor(actorId, context);
        if (gate.isFailure()) return copy(gate);
        Faction faction = actorFaction(actorId);
        if (faction == null) return notFound("claim.no-faction");
        if (!faction.hasPermission(actorId, PermissionAction.CLAIM)) return forbidden("claim.forbidden");
        ApiResult<ClaimResultView> result = api.claimSingle(
                gate.getValue(), faction.getId(), location(chunk), context);
        if (result.isSuccess()) changed(faction, context, EnumSet.of(FactionField.TERRITORY), actorId);
        return result;
    }

    @Override
    public ApiResult<ClaimResultView> claimRadius(UUID actorId, ChunkView center,
                                                  int radius, OperationContext context) {
        ApiResult<Player> gate = actor(actorId, context);
        if (gate.isFailure()) return copy(gate);
        Faction faction = actorFaction(actorId);
        if (faction == null) return notFound("claim.no-faction");
        if (!faction.hasPermission(actorId, PermissionAction.CLAIM)) return forbidden("claim.forbidden");
        ApiResult<ClaimResultView> result = api.claimRadius(
                gate.getValue(), faction.getId(), location(center), radius, context);
        if (result.isSuccess()) changed(faction, context, EnumSet.of(FactionField.TERRITORY), actorId);
        return result;
    }

    @Override
    public ApiResult<ClaimResultView> claimFill(UUID actorId, ChunkView start, OperationContext context) {
        ApiResult<Player> gate = actor(actorId, context);
        if (gate.isFailure()) return copy(gate);
        Faction faction = actorFaction(actorId);
        if (faction == null) return notFound("claim.no-faction");
        if (!faction.hasPermission(actorId, PermissionAction.CLAIM)) return forbidden("claim.forbidden");
        ApiResult<ClaimResultView> result = api.claimFill(
                gate.getValue(), faction.getId(), location(start), context);
        if (result.isSuccess()) changed(faction, context, EnumSet.of(FactionField.TERRITORY), actorId);
        return result;
    }

    @Override
    public ApiResult<UnclaimResultView> unclaim(UUID actorId, ChunkView chunk, OperationContext context) {
        ApiResult<Player> gate = actor(actorId, context);
        if (gate.isFailure()) return copy(gate);
        Faction faction = actorFaction(actorId);
        if (faction == null) return notFound("unclaim.no-faction");
        if (!faction.hasPermission(actorId, PermissionAction.UNCLAIM)) return forbidden("unclaim.forbidden");
        ApiResult<UnclaimResultView> result = api.unclaimSingle(
                gate.getValue(), faction.getId(), location(chunk), context);
        if (result.isSuccess()) changed(faction, context, EnumSet.of(FactionField.TERRITORY), actorId);
        return result;
    }

    @Override
    public ApiResult<UnclaimResultView> unclaimRadius(UUID actorId, ChunkView center,
                                                      int radius, OperationContext context) {
        ApiResult<Player> gate = actor(actorId, context);
        if (gate.isFailure()) return copy(gate);
        Faction faction = actorFaction(actorId);
        if (faction == null) return notFound("unclaim.no-faction");
        if (!faction.hasPermission(actorId, PermissionAction.UNCLAIM)) return forbidden("unclaim.forbidden");
        ApiResult<UnclaimResultView> result = api.unclaimRadius(
                gate.getValue(), faction.getId(), location(center), radius, context);
        if (result.isSuccess()) changed(faction, context, EnumSet.of(FactionField.TERRITORY), actorId);
        return result;
    }

    @Override
    public ApiResult<UnclaimResultView> unclaimAll(UUID actorId, OperationContext context) {
        ApiResult<Player> gate = actor(actorId, context);
        if (gate.isFailure()) return copy(gate);
        Faction faction = actorFaction(actorId);
        if (faction == null) return notFound("unclaim.no-faction");
        if (!faction.hasPermission(actorId, PermissionAction.UNCLAIM)) return forbidden("unclaim.forbidden");
        ApiResult<UnclaimResultView> result = api.unclaimAll(
                gate.getValue(), faction.getId(), context);
        if (result.isSuccess()) changed(faction, context, EnumSet.of(FactionField.TERRITORY), actorId);
        return result;
    }

    @Override
    public ApiResult<RelationView> requestRelation(UUID actorId, String targetFactionId,
                                                    String relationName, OperationContext context) {
        ApiResult<Player> gate = actor(actorId, context);
        if (gate.isFailure()) return copy(gate);
        Faction source = actorFaction(actorId);
        Faction target = plugin.getFactionManager().getFaction(targetFactionId);
        Relation relation = parseRelation(relationName);
        if (source == null || target == null) return notFound("relation.faction-not-found");
        if (relation == null || relation == Relation.NEUTRAL || relation == Relation.MEMBER) {
            return invalid("relation.invalid-type");
        }
        if (!source.hasPermission(actorId, permissionFor(relation))) return forbidden("relation.forbidden");
        RelationResult result = mutateRelation(source, target, relation);
        ApiResult<RelationView> mapped = relationResult(source, target, relation, result);
        if (mapped.isSuccess()) changed(source, context, EnumSet.of(FactionField.RELATIONS), actorId);
        return mapped;
    }

    @Override
    public ApiResult<RelationView> acceptRelation(UUID actorId, String sourceFactionId,
                                                   String relation, OperationContext context) {
        return requestRelation(actorId, sourceFactionId, relation, context);
    }

    @Override
    public ApiResult<Void> declineRelation(UUID actorId, String sourceFactionId,
                                            String relationName, OperationContext context) {
        ApiResult<Player> gate = actor(actorId, context);
        if (gate.isFailure()) return copy(gate);
        Faction target = actorFaction(actorId);
        Relation relation = parseRelation(relationName);
        if (target == null || relation == null) return invalid("relation.invalid-request");
        if (!target.hasPermission(actorId, permissionFor(relation))) return forbidden("relation.forbidden");
        if (!target.removeRelationRequestIfPresent(sourceFactionId, relation)) {
            return noChange("relation.request-not-found");
        }
        target.updateActivity();
        plugin.getStorageManager().markDirty(target);
        changed(target, context, EnumSet.of(FactionField.RELATIONS), actorId);
        return ApiResult.success(null);
    }

    @Override
    public ApiResult<RelationView> setNeutral(UUID actorId, String targetFactionId,
                                              OperationContext context) {
        ApiResult<Player> gate = actor(actorId, context);
        if (gate.isFailure()) return copy(gate);
        Faction source = actorFaction(actorId);
        Faction target = plugin.getFactionManager().getFaction(targetFactionId);
        if (source == null || target == null) return notFound("relation.faction-not-found");
        if (!source.hasPermission(actorId, PermissionAction.RELATION_NEUTRAL)) {
            return forbidden("relation.forbidden");
        }
        RelationResult result = plugin.getRelationManager().setNeutral(source, target);
        ApiResult<RelationView> mapped = relationResult(source, target, Relation.NEUTRAL, result);
        if (mapped.isSuccess()) changed(source, context, EnumSet.of(FactionField.RELATIONS), actorId);
        return mapped;
    }

    @Override
    public ApiResult<Long> depositBank(UUID actorId, long amountMinor, OperationContext context) {
        return bank(actorId, amountMinor, context, true);
    }

    @Override
    public ApiResult<Long> withdrawBank(UUID actorId, long amountMinor, OperationContext context) {
        return bank(actorId, amountMinor, context, false);
    }

    @Override
    public ApiResult<PositionView> setHome(UUID actorId, PositionView position, OperationContext context) {
        ApiResult<Player> gate = actor(actorId, context);
        if (gate.isFailure()) return copy(gate);
        if (!currentPositionMatches(gate.getValue(), position)) return conflict("home.stale-position");
        Faction faction = actorFaction(actorId);
        if (faction == null) return notFound("home.no-faction");
        OperationResult<StoredLocation> result = homeWarps().setHome(gate.getValue(), faction, context);
        if (!result.isSuccessful()) return from(result);
        changed(faction, context, EnumSet.of(FactionField.SETTINGS), actorId);
        return ApiResult.from(result, position(result.getValue()));
    }

    @Override
    public ApiResult<Void> teleportHome(UUID actorId, OperationContext context) {
        ApiResult<Player> gate = actor(actorId, context);
        if (gate.isFailure()) return copy(gate);
        Faction faction = actorFaction(actorId);
        if (faction == null) return notFound("home.no-faction");
        OperationResult<String> result = homeWarps().requestHomeTeleport(gate.getValue(), faction, context);
        return result.isSuccessful() ? ApiResult.<Void>success(null) : from(result);
    }

    @Override
    public ApiResult<WarpView> createWarp(UUID actorId, String name, PositionView position,
                                          OperationContext context) {
        ApiResult<Player> gate = actor(actorId, context);
        if (gate.isFailure()) return copy(gate);
        if (!currentPositionMatches(gate.getValue(), position)) return conflict("warp.stale-position");
        Faction faction = actorFaction(actorId);
        if (faction == null) return notFound("warp.no-faction");
        OperationResult<FactionWarp> result = homeWarps().setWarp(
                gate.getValue(), faction, name, null, context);
        if (!result.isSuccessful()) return from(result);
        changed(faction, context, EnumSet.of(FactionField.WARPS), actorId);
        return ApiResult.from(result, warp(result.getValue()));
    }

    @Override
    public ApiResult<WarpView> deleteWarp(UUID actorId, String name, OperationContext context) {
        ApiResult<Player> gate = actor(actorId, context);
        if (gate.isFailure()) return copy(gate);
        Faction faction = actorFaction(actorId);
        if (faction == null) return notFound("warp.no-faction");
        OperationResult<FactionWarp> result = homeWarps().deleteWarp(
                gate.getValue(), faction, name, context);
        if (!result.isSuccessful()) return from(result);
        changed(faction, context, EnumSet.of(FactionField.WARPS), actorId);
        return ApiResult.from(result, warp(result.getValue()));
    }

    @Override
    public ApiResult<Void> teleportWarp(UUID actorId, String name, String password,
                                        OperationContext context) {
        ApiResult<Player> gate = actor(actorId, context);
        if (gate.isFailure()) return copy(gate);
        Faction faction = actorFaction(actorId);
        if (faction == null) return notFound("warp.no-faction");
        OperationResult<String> result = homeWarps().requestWarpTeleport(
                gate.getValue(), faction, name, password, context);
        return result.isSuccessful() ? ApiResult.<Void>success(null) : from(result);
    }

    @Override
    public ApiResult<Boolean> setRolePermission(UUID actorId, FactionRole role,
                                                String permissionKey, boolean allowed,
                                                OperationContext context) {
        ApiResult<Player> gate = actor(actorId, context);
        if (gate.isFailure()) return copy(gate);
        Faction faction = actorFaction(actorId);
        PermissionAction permission = PermissionAction.fromConfigKey(permissionKey);
        if (faction == null || role == null || permission == null || role == FactionRole.LEADER) {
            return invalid("permission.invalid-input");
        }
        if (!faction.hasPermission(actorId, PermissionAction.PERMS)) return forbidden("permission.forbidden");
        boolean before = faction.hasPermission(role, permission);
        if (before == allowed) return noChange("permission.no-change");
        faction.setPermission(role, permission, allowed);
        faction.updateActivity();
        plugin.getStorageManager().markDirty(faction);
        changed(faction, context, EnumSet.of(FactionField.PERMISSIONS), actorId);
        return ApiResult.success(Boolean.valueOf(allowed));
    }

    @Override
    public ApiResult<Boolean> setRelationPermission(UUID actorId, String relationName,
                                                    String permissionKey, boolean allowed,
                                                    OperationContext context) {
        ApiResult<Player> gate = actor(actorId, context);
        if (gate.isFailure()) return copy(gate);
        Faction faction = actorFaction(actorId);
        Relation relation = parseRelation(relationName);
        PermissionAction permission = PermissionAction.fromConfigKey(permissionKey);
        if (faction == null || relation == null || permission == null) return invalid("permission.invalid-input");
        if (!faction.hasPermission(actorId, PermissionAction.PERMS)) return forbidden("permission.forbidden");
        boolean before = faction.hasPermission(relation, permission);
        if (before == allowed) return noChange("permission.no-change");
        faction.setPermission(relation, permission, allowed);
        faction.updateActivity();
        plugin.getStorageManager().markDirty(faction);
        changed(faction, context, EnumSet.of(FactionField.PERMISSIONS), actorId);
        return ApiResult.success(Boolean.valueOf(allowed));
    }

    @Override
    public ApiResult<ProgressionView> selectQuestCategory(UUID actorId, String categoryId,
                                                          OperationContext context) {
        ApiResult<Player> gate = actor(actorId, context);
        if (gate.isFailure()) return copy(gate);
        Faction faction = actorFaction(actorId);
        if (faction == null) return notFound("progression.no-faction");
        return unavailable("progression.manual-category-unavailable");
    }

    @Override
    public ApiResult<ProgressionView> claimProgressionReward(UUID actorId, int level,
                                                             OperationContext context) {
        ApiResult<Player> gate = actor(actorId, context);
        if (gate.isFailure()) return copy(gate);
        Faction faction = actorFaction(actorId);
        if (faction == null) return notFound("progression.no-faction");
        return unavailable("progression.rewards-automatic");
    }

    private ApiResult<Long> bank(UUID actorId, long amountMinor,
                                 OperationContext context, boolean deposit) {
        ApiResult<Player> gate = actor(actorId, context);
        if (gate.isFailure()) return copy(gate);
        if (amountMinor <= 0L) return invalid("economy.invalid-amount");
        Faction faction = actorFaction(actorId);
        if (faction == null) return notFound("economy.no-faction");
        EconomyService service = plugin.getEconomyManager().getService();
        OperationResult<EconomyTransactionResult> result = deposit
                ? service.depositToFaction(gate.getValue(), faction, MoneyAmount.ofMinor(amountMinor), context)
                : service.withdrawFromFaction(gate.getValue(), faction, MoneyAmount.ofMinor(amountMinor), context);
        if (!result.isSuccessful()) return from(result);
        changed(faction, context, EnumSet.of(FactionField.BANK), actorId);
        long balance = result.getValue().getFactionBalanceAfter().getMinorUnits();
        return ApiResult.from(result, Long.valueOf(balance));
    }

    private ApiResult<Player> actor(UUID actorId, OperationContext context) {
        if (!Bukkit.isPrimaryThread()) return unavailable("actions.main-thread-required");
        if (actorId == null || context == null || context.getActorId() == null
                || !actorId.equals(context.getActorId())) return invalid("actions.invalid-context");
        Player player = Bukkit.getPlayer(actorId);
        if (player == null || !player.isOnline()) return unavailable("actions.actor-offline");
        return ApiResult.success(player);
    }

    private Faction actorFaction(UUID actorId) {
        return actorId != null ? plugin.getFactionManager().getPlayerFaction(actorId) : null;
    }

    private MemberView member(String factionId, UUID playerId) {
        FactionView view = api.getFaction(factionId);
        if (view == null) return null;
        for (MemberView member : view.getMembers()) {
            if (playerId.equals(member.getUuid())) return member;
        }
        return null;
    }

    private HomeWarpService homeWarps() {
        return plugin.getFactionManager().getHomeWarpService();
    }

    private static FLocation location(ChunkView chunk) {
        return chunk != null ? new FLocation(chunk.getWorld(), chunk.getX(), chunk.getZ()) : null;
    }

    private static PositionView position(StoredLocation value) {
        return value != null ? new PositionView(value.getWorldName(), value.getX(), value.getY(),
                value.getZ(), value.getYaw(), value.getPitch()) : null;
    }

    private static WarpView warp(FactionWarp value) {
        return value != null && value.getStoredLocation() != null
                ? new WarpView(value.getName(), position(value.getStoredLocation()),
                value.isPasswordProtected(), value.getCreatedAt(), value.getCreatedBy(), value.getUpdatedAt())
                : null;
    }

    private static boolean currentPositionMatches(Player player, PositionView position) {
        if (player == null || position == null) return false;
        Location actual = player.getLocation();
        return actual != null && actual.getWorld() != null
                && actual.getWorld().getName().equalsIgnoreCase(position.getWorld())
                && Math.abs(actual.getX() - position.getX()) < 0.01D
                && Math.abs(actual.getY() - position.getY()) < 0.01D
                && Math.abs(actual.getZ() - position.getZ()) < 0.01D;
    }

    private RelationResult mutateRelation(Faction source, Faction target, Relation relation) {
        switch (relation) {
            case ALLY: return plugin.getRelationManager().requestAlly(source, target);
            case TRUCE: return plugin.getRelationManager().requestTruce(source, target);
            case ENEMY: return plugin.getRelationManager().declareEnemy(source, target);
            default: return RelationResult.DISABLED;
        }
    }

    private static Relation parseRelation(String value) {
        if (value == null) return null;
        try { return Relation.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private static PermissionAction permissionFor(Relation relation) {
        if (relation == Relation.ALLY) return PermissionAction.RELATION_ALLY;
        if (relation == Relation.TRUCE) return PermissionAction.RELATION_TRUCE;
        if (relation == Relation.ENEMY) return PermissionAction.RELATION_ENEMY;
        return PermissionAction.RELATION_NEUTRAL;
    }

    private static ApiResult<RelationView> relationResult(
            Faction source, Faction target, Relation relation, RelationResult result) {
        if (result == null) return failed("relation.null-result");
        if (result == RelationResult.SUCCESS || result == RelationResult.REQUEST_SENT) {
            return ApiResult.success(new RelationView(source.getId(), target.getId(), relation.name()));
        }
        if (result == RelationResult.ALREADY_SET || result == RelationResult.REQUEST_PENDING) {
            return noChange("relation." + result.name().toLowerCase(java.util.Locale.ROOT));
        }
        if (result == RelationResult.LIMIT_REACHED) return limit("relation.limit-reached");
        if (result == RelationResult.NO_PERMISSION) return forbidden("relation.forbidden");
        if (result == RelationResult.NOT_FOUND) return notFound("relation.not-found");
        if (result == RelationResult.UNAVAILABLE) return unavailable("relation.unavailable");
        return invalid("relation." + result.name().toLowerCase(java.util.Locale.ROOT));
    }

    private void changed(Faction faction, OperationContext context,
                         EnumSet<FactionField> fields, UUID... affected) {
        if (faction == null) return;
        java.util.Set<UUID> players = new java.util.LinkedHashSet<UUID>();
        if (affected != null) Collections.addAll(players, affected);
        Bukkit.getPluginManager().callEvent(new FactionSnapshotChangedEvent(
                faction.getId(), faction.getRevision(), fields, players, context));
    }

    private static <T> ApiResult<T> from(OperationResult<?> result) {
        return ApiResult.from(result, null);
    }

    private static <T> ApiResult<T> copy(ApiResult<?> result) {
        return new ApiResult<T>(result.getStatus(), null, result.getMessageKey(), result.getDetail());
    }

    private static <T> ApiResult<T> invalid(String key) { return ApiResult.failure(ApiResult.Status.INVALID_INPUT, key, null); }
    private static <T> ApiResult<T> notFound(String key) { return ApiResult.failure(ApiResult.Status.NOT_FOUND, key, null); }
    private static <T> ApiResult<T> forbidden(String key) { return ApiResult.failure(ApiResult.Status.FORBIDDEN, key, null); }
    private static <T> ApiResult<T> conflict(String key) { return ApiResult.failure(ApiResult.Status.CONFLICT, key, null); }
    private static <T> ApiResult<T> limit(String key) { return ApiResult.failure(ApiResult.Status.LIMIT_REACHED, key, null); }
    private static <T> ApiResult<T> unavailable(String key) { return ApiResult.failure(ApiResult.Status.UNAVAILABLE, key, null); }
    private static <T> ApiResult<T> failed(String key) { return ApiResult.failure(ApiResult.Status.FAILED, key, null); }
    private static <T> ApiResult<T> cancelled(String key, String detail) { return ApiResult.failure(ApiResult.Status.CANCELLED, key, detail); }
    private static <T> ApiResult<T> noChange(String key) { return new ApiResult<T>(ApiResult.Status.NO_CHANGE, null, key, null); }
}
