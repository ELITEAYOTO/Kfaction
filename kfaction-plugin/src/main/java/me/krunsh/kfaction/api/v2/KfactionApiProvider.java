package me.krunsh.kfaction.api.v2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.core.operation.OperationResult;
import me.krunsh.kfaction.data.FLocation;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionLog;
import me.krunsh.kfaction.data.FactionRole;
import me.krunsh.kfaction.data.FactionWarp;
import me.krunsh.kfaction.data.PermissionAction;
import me.krunsh.kfaction.data.Relation;
import me.krunsh.kfaction.data.StoredLocation;
import me.krunsh.kfaction.grace.GraceState;
import me.krunsh.kfaction.permissions.PermissionDecision;
import me.krunsh.kfaction.permissions.TerritoryAction;
import me.krunsh.kfaction.progression.ProgressionStatus;
import me.krunsh.kfaction.progression.RewardDefinition;
import me.krunsh.kfaction.progression.QuestProgressView;
import me.krunsh.kfaction.progression.LevelDefinition;
import me.krunsh.kfaction.progression.MemberTierDefinition;
import me.krunsh.kfaction.services.FactionLifecycleService;
import me.krunsh.kfaction.services.MembershipService;
import me.krunsh.kfaction.services.MembershipService.ChangeReason;
import me.krunsh.kfaction.services.RoleService;
import me.krunsh.kfaction.services.claim.ClaimBatchResult;
import me.krunsh.kfaction.services.claim.UnclaimBatchResult;
import me.krunsh.kfaction.zones.GlobalZoneType;
import me.krunsh.kfaction.zones.ZoneDefinition;

/**
 * Implémentation officielle de KfactionApiV2.
 *
 * Cette classe est enregistrée dans Bukkit ServicesManager par KfactionAPI.
 */
public final class KfactionApiProvider
        implements KfactionApiV23 {

    private final Kfaction plugin;

    private final MembershipService membershipService;
    private final RoleService roleService;
    private final FactionLifecycleService lifecycleService;

    public KfactionApiProvider(
            Kfaction plugin
    ) {
        if (plugin == null) {
            throw new IllegalArgumentException(
                    "plugin cannot be null"
            );
        }

        this.plugin = plugin;

        this.membershipService =
                new MembershipService(plugin);

        this.roleService =
                new RoleService(plugin);

        this.lifecycleService =
                new FactionLifecycleService(plugin);
    }

    @Override
    public String getApiVersion() {
        return KfactionApiV23.API_VERSION;
    }

    @Override
    public int getApiMajor() {
        return API_MAJOR;
    }

    @Override
    public int getDefaultMaxMembers() {
        return Math.max(
                0,
                plugin.getConfigManager()
                        .getInt(
                                "factions.members.max-per-faction",
                                50
                        )
        );
    }

    @Override
    public int getDefaultMaxWarps() {
        return Math.max(
                0,
                plugin.getConfigManager()
                        .getInt(
                                "warps.max-per-faction",
                                1
                        )
        );
    }

    // ============================================================
    // Read API
    // ============================================================

    @Override
    public FactionView getFaction(
            String factionId
    ) {
        return snapshotFaction(
                plugin.getFactionManager()
                        .getFaction(factionId)
        );
    }

    @Override
    public FactionView findFaction(
            String idNameOrTag
    ) {
        if (idNameOrTag == null
                || idNameOrTag.trim().isEmpty()) {
            return null;
        }

        Faction faction =
                plugin.getFactionManager()
                        .getFaction(
                                idNameOrTag
                        );

        if (faction == null) {
            faction =
                    plugin.getFactionManager()
                            .getFactionByName(
                                    idNameOrTag
                            );
        }

        if (faction == null) {
            faction =
                    plugin.getFactionManager()
                            .getFactionByTag(
                                    idNameOrTag
                            );
        }

        return snapshotFaction(faction);
    }

    @Override
    public List<FactionView> getFactions() {
        List<FactionView> result =
                new ArrayList<FactionView>();

        for (Faction faction
                : plugin.getFactionManager()
                        .getPlayerFactions()) {
            FactionView view =
                    snapshotFaction(
                            faction
                    );

            if (view != null) {
                result.add(view);
            }
        }

        Collections.sort(
                result,
                new Comparator<FactionView>() {
                    @Override
                    public int compare(
                            FactionView first,
                            FactionView second
                    ) {
                        String a =
                                first.getName() != null
                                        ? first.getName()
                                                .toLowerCase(
                                                        Locale.ROOT
                                                )
                                        : "";

                        String b =
                                second.getName() != null
                                        ? second.getName()
                                                .toLowerCase(
                                                        Locale.ROOT
                                                )
                                        : "";

                        return a.compareTo(b);
                    }
                }
        );

        return Collections.unmodifiableList(
                result
        );
    }

    @Override
    public PlayerView getPlayer(
            UUID playerId
    ) {
        if (playerId == null) {
            return null;
        }

        FPlayer fPlayer =
                plugin.getFPlayerManager()
                        .find(playerId);

        return snapshotPlayer(
                fPlayer
        );
    }

    @Override
    public FactionView getPlayerFaction(
            UUID playerId
    ) {
        return playerId != null
                ? snapshotFaction(
                        plugin.getFactionManager()
                                .getPlayerFaction(
                                        playerId
                                )
                )
                : null;
    }

    @Override
    public String getRelation(
            String firstFactionId,
            String secondFactionId
    ) {
        Faction first =
                requireFaction(
                        firstFactionId
                );

        Faction second =
                requireFaction(
                        secondFactionId
                );

        if (first == null
                || second == null) {
            return Relation.NEUTRAL.name();
        }

        if (first.getId()
                .equals(
                        second.getId()
                )) {
            return Relation.MEMBER.name();
        }

        Relation relation =
                first.getRelationTo(
                        second
                );

        return relation != null
                ? relation.name()
                : Relation.NEUTRAL.name();
    }

    @Override
    public TerritoryView getTerritory(
            FLocation location,
            UUID viewerId
    ) {
        if (location == null) {
            return null;
        }

        ZoneDefinition zone =
                plugin.getClaimManager()
                        .getZoneService()
                        .getDefinitionAt(
                                location
                        );

        if (zone != null) {
            TerritoryView.Type type;

            if (zone.isLegacySafezone()) {
                type =
                        TerritoryView.Type.SAFEZONE;
            } else if (zone.isLegacyWarzone()) {
                type =
                        TerritoryView.Type.WARZONE;
            } else {
                type =
                        TerritoryView.Type.GLOBAL_ZONE;
            }

            return new TerritoryView(
                    ChunkView.from(location),
                    type,
                    null,
                    null,
                    null,
                    null,
                    null,
                    zone.getId(),
                    zone.getDisplayName(),
                    zone.getColor(),
                    zone.getMapSymbol()
            );
        }

        Faction territory =
                plugin.getClaimManager()
                        .getPlayerFactionAt(
                                location
                        );

        TerritoryView.Type type =
                territory == null
                || territory.isWilderness()
                        ? TerritoryView.Type.WILDERNESS
                        : TerritoryView.Type.FACTION;

        String relation = null;

        if (viewerId != null
                && territory != null
                && !territory.isSystemFaction()) {
            Faction viewer =
                    plugin.getFactionManager()
                            .getPlayerFaction(
                                    viewerId
                            );

            if (viewer != null) {
                Relation value =
                        viewer.getRelationTo(
                                territory
                        );

                relation =
                        value != null
                                ? value.name()
                                : null;
            }
        }

        String claimGroupId =
                territory != null
                        && !territory.isSystemFaction()
                        ? territory.getClaimGroupId(
                                location
                        )
                        : null;

        return new TerritoryView(
                ChunkView.from(location),
                type,
                territory != null
                        && !territory.isWilderness()
                                ? territory.getId()
                                : null,
                territory != null
                        && !territory.isWilderness()
                                ? territory.getName()
                                : null,
                territory != null
                        && !territory.isWilderness()
                                ? territory.getTag()
                                : null,
                relation,
                claimGroupId
        );
    }

    @Override
    public ZoneView getGlobalZoneAt(
            FLocation location
    ) {
        if (location == null) {
            return null;
        }

        ZoneDefinition definition =
                plugin.getClaimManager()
                        .getZoneService()
                        .getDefinitionAt(
                                location
                        );

        return definition != null
                ? snapshotZone(
                        definition
                )
                : null;
    }

    @Override
    public List<ZoneView> getGlobalZones() {
        List<ZoneView> result =
                new ArrayList<ZoneView>();

        for (ZoneDefinition definition
                : plugin.getClaimManager()
                        .getZoneService()
                        .getDefinitionList()) {
            result.add(
                    snapshotZone(
                            definition
                    )
            );
        }

        Collections.sort(
                result,
                new Comparator<ZoneView>() {
                    @Override
                    public int compare(
                            ZoneView first,
                            ZoneView second
                    ) {
                        return first.getId()
                                .compareToIgnoreCase(
                                        second.getId()
                                );
                    }
                }
        );

        return Collections.unmodifiableList(
                result
        );
    }

    @Override
    public ProgressionView getProgression(
            String factionId
    ) {
        Faction faction =
                factionId != null
                        ? plugin.getFactionManager()
                                .getFaction(
                                        factionId
                                )
                        : null;

        if (faction == null
                || plugin.getQuestManager() == null) {
            return null;
        }

        /*
         * API READ contract: aucun ensureReadable(), migration, lockTier ou
         * markDirty ne doit être déclenché depuis une simple lecture.
         */
        ProgressionStatus status =
                plugin.getQuestManager()
                        .peekStatus(
                                faction
                        );

        MemberTierDefinition tier = null;

        if (status != null
                && status.getTierId() != null
                && plugin.getQuestManager()
                        .getActiveConfig() != null) {
            tier =
                    plugin.getQuestManager()
                            .getActiveConfig()
                            .getTier(
                                    status.getTierId()
                            );
        }

        return snapshotProgression(
                status,
                tier != null
                        ? tier.getDisplayName()
                        : null
        );
    }

    @Override
    public List<QuestView> getProgressionQuests(
            String factionId
    ) {
        Faction faction =
                factionId != null
                        ? plugin.getFactionManager()
                                .getFaction(factionId)
                        : null;

        if (faction == null
                || faction.isSystemFaction()
                || plugin.getQuestManager() == null) {
            return Collections.emptyList();
        }

        List<QuestView> result =
                new ArrayList<QuestView>();

        for (QuestProgressView view
                : plugin.getQuestManager()
                        .peekQuestViews(faction)) {
            if (view == null
                    || view.getDefinition() == null) {
                continue;
            }

            result.add(
                    new QuestView(
                            view.getDefinition().getId(),
                            view.getDefinition().getDisplayName(),
                            view.getDefinition().getIconMaterial(),
                            view.getDefinition().getIconData(),
                            view.getDefinition().getLore(),
                            view.getProgress(),
                            view.getRequired(),
                            view.getRemaining(),
                            view.getPercent(),
                            view.isCompleted()
                    )
            );
        }

        return Collections.unmodifiableList(result);
    }

    @Override
    public List<RewardLevelView> getRewardLevels(
            String factionId
    ) {
        Faction faction =
                factionId != null
                        ? plugin.getFactionManager()
                                .getFaction(factionId)
                        : null;

        if (faction == null
                || faction.isSystemFaction()
                || plugin.getQuestManager() == null
                || plugin.getQuestManager()
                        .getActiveConfig() == null) {
            return Collections.emptyList();
        }

        List<RewardLevelView> result =
                new ArrayList<RewardLevelView>();

        List<LevelDefinition> levels =
                new ArrayList<LevelDefinition>(
                        plugin.getQuestManager()
                                .getActiveConfig()
                                .getLevels()
                                .values()
                );

        Collections.sort(
                levels,
                new Comparator<LevelDefinition>() {
                    @Override
                    public int compare(
                            LevelDefinition first,
                            LevelDefinition second
                    ) {
                        return Integer.compare(
                                first.getNumber(),
                                second.getNumber()
                        );
                    }
                }
        );

        int currentLevel = faction.getLevel();

        for (LevelDefinition definition : levels) {
            if (definition == null) {
                continue;
            }

            List<String> rewards =
                    new ArrayList<String>();

            for (RewardDefinition reward
                    : definition.getRewardsOnEnter()) {
                if (reward != null) {
                    rewards.add(
                            reward.getDescription()
                    );
                }
            }

            RewardLevelView.State state;

            if (currentLevel > definition.getNumber()) {
                state = RewardLevelView.State.UNLOCKED;
            } else if (currentLevel == definition.getNumber()) {
                state = RewardLevelView.State.CURRENT;
            } else {
                state = RewardLevelView.State.LOCKED;
            }

            result.add(
                    new RewardLevelView(
                            definition.getNumber(),
                            state,
                            rewards
                    )
            );
        }

        return Collections.unmodifiableList(result);
    }

    @Override
    public GraceView getGrace() {
        if (plugin.getPermissionManager()
                .getGraceService() == null) {
            return new GraceView(
                    false,
                    0L,
                    0L,
                    0L,
                    null,
                    null,
                    0L
            );
        }

        GraceState state =
                plugin.getPermissionManager()
                .getGraceService()
                        .getStateSnapshot();

        long now =
                System.currentTimeMillis();

        return new GraceView(
                state.isActiveAt(now),
                state.getStartedAt(),
                state.getEndsAt(),
                state.getRemainingMillis(now),
                state.getStartedBy(),
                state.getReason(),
                state.getRevision()
        );
    }

    @Override
    public PermissionView checkTerritory(
            Player player,
            Location location,
            TerritoryAction action
    ) {
        PermissionDecision decision =
                plugin.getPermissionManager()
                        .getService()
                        .checkTerritory(
                                player,
                                location,
                                action
                        );

        return new PermissionView(
                decision.isAllowed(),
                decision.getReason() != null
                        ? decision.getReason()
                                .name()
                        : null,
                decision.getTerritoryFactionId(),
                decision.getRole() != null
                        ? decision.getRole()
                                .name()
                        : null,
                decision.getRelation() != null
                        ? decision.getRelation()
                                .name()
                        : null
        );
    }

    @Override
    public boolean canPvp(
            Player attacker,
            Player defender
    ) {
        return plugin.getPermissionManager()
                .getService()
                .canPvP(
                        attacker,
                        defender
                );
    }

    @Override
    public Boolean getRolePermission(
            String factionId,
            String role,
            String permissionKey
    ) {
        Faction faction =
                requireFaction(factionId);

        if (faction == null
                || role == null
                || permissionKey == null) {
            return null;
        }

        FactionRole factionRole;

        try {
            factionRole =
                    FactionRole.valueOf(
                            role.trim()
                                    .toUpperCase(Locale.ROOT)
                    );
        } catch (IllegalArgumentException exception) {
            return null;
        }

        PermissionAction action =
                PermissionAction.fromConfigKey(
                        permissionKey.trim()
                );

        if (action == null) {
            return null;
        }

        return Boolean.valueOf(
                faction.hasPermission(
                        factionRole,
                        action
                )
        );
    }

    @Override
    public Boolean getRelationPermission(
            String factionId,
            String relation,
            String permissionKey
    ) {
        Faction faction =
                requireFaction(factionId);

        if (faction == null
                || relation == null
                || permissionKey == null) {
            return null;
        }

        Relation parsed;

        try {
            parsed =
                    Relation.valueOf(
                            relation.trim()
                                    .toUpperCase(Locale.ROOT)
                    );
        } catch (IllegalArgumentException exception) {
            return null;
        }

        PermissionAction action =
                PermissionAction.fromConfigKey(
                        permissionKey.trim()
                );

        if (action == null) {
            return null;
        }

        return Boolean.valueOf(
                faction.hasPermission(
                        parsed,
                        action
                )
        );
    }

    // ============================================================
    // Additive read API 2.3
    // ============================================================

    @Override
    public PageView<ClaimView> getFactionClaims(
            String factionId,
            PageRequest request
    ) {
        PageRequest safe = request != null ? request : PageRequest.first();
        Faction faction = plugin.getFactionManager().getFaction(factionId);
        if (faction == null || faction.isSystemFaction()) {
            return PageView.empty(safe);
        }

        final Map<FLocation, String> groups =
                faction.getClaimGroupAssignmentsSnapshot();
        List<FLocation> claims = new ArrayList<FLocation>(faction.getClaims());
        Collections.sort(claims, new Comparator<FLocation>() {
            @Override
            public int compare(FLocation first, FLocation second) {
                int world = first.getWorldName().compareToIgnoreCase(second.getWorldName());
                if (world != 0) return world;
                int x = Integer.compare(first.getX(), second.getX());
                return x != 0 ? x : Integer.compare(first.getZ(), second.getZ());
            }
        });

        int from = Math.min(safe.getOffset(), claims.size());
        int to = Math.min(claims.size(), from + safe.getLimit());
        List<ClaimView> items = new ArrayList<ClaimView>(to - from);
        for (FLocation location : claims.subList(from, to)) {
            items.add(new ClaimView(ChunkView.from(location), groups.get(location)));
        }
        return new PageView<ClaimView>(items, safe.getOffset(), safe.getLimit(), claims.size());
    }

    @Override
    public List<WarpView> getFactionWarps(String factionId) {
        Faction faction = plugin.getFactionManager().getFaction(factionId);
        if (faction == null || faction.isSystemFaction()) {
            return Collections.emptyList();
        }
        List<WarpView> result = new ArrayList<WarpView>();
        for (FactionWarp warp : faction.getWarpDataSnapshot().values()) {
            WarpView view = snapshotWarp(warp);
            if (view != null) result.add(view);
        }
        Collections.sort(result, new Comparator<WarpView>() {
            @Override
            public int compare(WarpView first, WarpView second) {
                return first.getName().compareToIgnoreCase(second.getName());
            }
        });
        return Collections.unmodifiableList(result);
    }

    @Override
    public PageView<FactionLogView> getFactionLogs(
            String factionId,
            FactionLogQuery query
    ) {
        FactionLogQuery safeQuery = query != null ? query : FactionLogQuery.recent();
        PageRequest page = safeQuery.getPage();
        Faction faction = plugin.getFactionManager().getFaction(factionId);
        if (faction == null || faction.isSystemFaction() || plugin.getLogManager() == null) {
            return PageView.empty(page);
        }

        List<FactionLogView> filtered = new ArrayList<FactionLogView>();
        for (FactionLog log : plugin.getLogManager().getLogs(factionId)) {
            if (log == null) continue;
            if (safeQuery.getType() != null
                    && (log.getType() == null
                    || !safeQuery.getType().equalsIgnoreCase(log.getType().name()))) {
                continue;
            }
            if (safeQuery.getActorId() != null
                    && !safeQuery.getActorId().equals(log.getPlayerUuid())
                    && !safeQuery.getActorId().equals(log.getTargetUuid())) {
                continue;
            }
            filtered.add(snapshotLog(log));
        }
        Collections.sort(filtered, new Comparator<FactionLogView>() {
            @Override
            public int compare(FactionLogView first, FactionLogView second) {
                return Long.compare(second.getTimestamp(), first.getTimestamp());
            }
        });
        int from = Math.min(page.getOffset(), filtered.size());
        int to = Math.min(filtered.size(), from + page.getLimit());
        return new PageView<FactionLogView>(
                new ArrayList<FactionLogView>(filtered.subList(from, to)),
                page.getOffset(), page.getLimit(), filtered.size());
    }

    @Override
    public List<FactionInviteView> getFactionInvites(String factionId) {
        Faction faction = plugin.getFactionManager().getFaction(factionId);
        if (faction == null || faction.isSystemFaction()) return Collections.emptyList();
        long lifetime = Math.max(1L, plugin.getConfigManager().getLong(
                "factions.invite-expiration", 300L)) * 1000L;
        long now = System.currentTimeMillis();
        List<FactionInviteView> result = new ArrayList<FactionInviteView>();
        for (Map.Entry<UUID, Long> entry : faction.getInvitesSnapshot().entrySet()) {
            long created = entry.getValue() != null ? Math.max(0L, entry.getValue()) : 0L;
            long expires = created + lifetime;
            result.add(new FactionInviteView(factionId, entry.getKey(), created, expires,
                    now > expires ? FactionInviteView.Status.EXPIRED : FactionInviteView.Status.PENDING));
        }
        Collections.sort(result, new Comparator<FactionInviteView>() {
            @Override
            public int compare(FactionInviteView first, FactionInviteView second) {
                return Long.compare(second.getCreatedAt(), first.getCreatedAt());
            }
        });
        return Collections.unmodifiableList(result);
    }

    @Override
    public List<RelationRequestView> getRelationRequests(String factionId) {
        Faction faction = plugin.getFactionManager().getFaction(factionId);
        if (faction == null || faction.isSystemFaction()) return Collections.emptyList();
        long lifetime = Math.max(1L, plugin.getConfigManager().getLong(
                "relations.request-expiration-seconds", 300L)) * 1000L;
        long now = System.currentTimeMillis();
        List<RelationRequestView> result = new ArrayList<RelationRequestView>();
        for (Map.Entry<String, Long> entry : faction.getRelationRequestsSnapshot().entrySet()) {
            String key = entry.getKey();
            if (key == null || key.trim().isEmpty()) continue;
            int separator = key.indexOf('|');
            String relation = separator > 0 ? key.substring(0, separator) : "LEGACY";
            String target = separator > 0 ? key.substring(separator + 1) : key;
            long created = entry.getValue() != null ? Math.max(0L, entry.getValue()) : 0L;
            long expires = created + lifetime;
            result.add(new RelationRequestView(factionId, target, relation, created, expires,
                    now > expires ? RelationRequestView.Status.EXPIRED : RelationRequestView.Status.PENDING));
        }
        Collections.sort(result, new Comparator<RelationRequestView>() {
            @Override
            public int compare(RelationRequestView first, RelationRequestView second) {
                return Long.compare(second.getCreatedAt(), first.getCreatedAt());
            }
        });
        return Collections.unmodifiableList(result);
    }

    @Override
    public List<RelationView> getRelations(String factionId) {
        Faction faction = plugin.getFactionManager().getFaction(factionId);
        if (faction == null || faction.isSystemFaction()) return Collections.emptyList();
        List<RelationView> result = new ArrayList<RelationView>();
        for (Map.Entry<String, Relation> entry : faction.getAllRelations().entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                result.add(new RelationView(factionId, entry.getKey(), entry.getValue().name()));
            }
        }
        Collections.sort(result, new Comparator<RelationView>() {
            @Override
            public int compare(RelationView first, RelationView second) {
                return first.getOtherFactionId().compareToIgnoreCase(second.getOtherFactionId());
            }
        });
        return Collections.unmodifiableList(result);
    }

    @Override
    public FactionAclView getFactionAcl(String factionId) {
        Faction faction = plugin.getFactionManager().getFaction(factionId);
        if (faction == null || faction.isSystemFaction()) return null;
        Map<String, Set<String>> roles = new LinkedHashMap<String, Set<String>>();
        for (FactionRole role : FactionRole.values()) {
            Set<String> values = new LinkedHashSet<String>();
            Set<PermissionAction> permissions = faction.getAllPermissions().get(role);
            if (permissions != null) {
                for (PermissionAction permission : permissions) values.add(permission.getConfigKey());
            }
            roles.put(role.name(), values);
        }
        Map<String, Set<String>> relations = new LinkedHashMap<String, Set<String>>();
        for (Relation relation : Relation.values()) {
            Set<String> values = new LinkedHashSet<String>();
            Set<PermissionAction> permissions = faction.getRelationPermissions().get(relation);
            if (permissions != null) {
                for (PermissionAction permission : permissions) values.add(permission.getConfigKey());
            }
            relations.put(relation.name(), values);
        }
        return new FactionAclView(roles, relations);
    }

    @Override
    public FactionSettingsView getFactionSettings(String factionId) {
        Faction faction = plugin.getFactionManager().getFaction(factionId);
        if (faction == null || faction.isSystemFaction()) return null;
        return new FactionSettingsView(
                factionId,
                snapshotPosition(faction.getStoredHome()),
                faction.isOpen(),
                faction.isPermanent(),
                faction.isFactionFlyEnabled(),
                faction.isAntiSethomeEnabled(),
                getDefaultMaxMembers() + Math.max(0, faction.getExtraMembers()),
                getDefaultMaxWarps() + Math.max(0, faction.getExtraWarps()));
    }

    // ============================================================
    // Trusted mutations
    // ============================================================

    @Override
    public ApiResult<MemberView> joinMember(
            String factionId,
            UUID playerId,
            FactionRole role,
            OperationContext context
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return wrongThread();
        }

        Faction faction =
                requireFaction(
                        factionId
                );

        if (faction == null) {
            return notFound(
                    "api.faction-not-found"
            );
        }

        OperationResult<FactionRole> result =
                membershipService.join(
                        faction,
                        playerId,
                        role != null
                                ? role
                                : FactionRole.RECRUIT,
                        ChangeReason.ADMIN_JOIN,
                        safeContext(context),
                        false
                );

        return ApiResult.from(
                result,
                result.isSuccessful()
                        ? snapshotMember(
                                faction,
                                playerId
                        )
                        : null
        );
    }

    @Override
    public ApiResult<Void> removeMember(
            String factionId,
            UUID playerId,
            OperationContext context
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return wrongThread();
        }

        Faction faction =
                requireFaction(
                        factionId
                );

        if (faction == null) {
            return notFound(
                    "api.faction-not-found"
            );
        }

        OperationResult<Void> result =
                membershipService.remove(
                        faction,
                        playerId,
                        ChangeReason.ADMIN_LEAVE,
                        safeContext(context),
                        false
                );

        return ApiResult.from(
                result,
                null
        );
    }

    @Override
    public ApiResult<MemberView> setRole(
            String factionId,
            UUID playerId,
            FactionRole role,
            OperationContext context
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return wrongThread();
        }

        Faction faction =
                requireFaction(
                        factionId
                );

        if (faction == null) {
            return notFound(
                    "api.faction-not-found"
            );
        }

        OperationResult<FactionRole> result =
                roleService.setRole(
                        faction,
                        playerId,
                        role,
                        safeContext(context)
                );

        return ApiResult.from(
                result,
                result.isSuccessful()
                        ? snapshotMember(
                                faction,
                                playerId
                        )
                        : null
        );
    }

    @Override
    public ApiResult<MemberView> transferLeadership(
            String factionId,
            UUID playerId,
            OperationContext context
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return wrongThread();
        }

        Faction faction =
                requireFaction(
                        factionId
                );

        if (faction == null) {
            return notFound(
                    "api.faction-not-found"
            );
        }

        OperationResult<FactionRole> result =
                roleService.transferLeadership(
                        faction,
                        playerId,
                        safeContext(context)
                );

        return ApiResult.from(
                result,
                result.isSuccessful()
                        ? snapshotMember(
                                faction,
                                playerId
                        )
                        : null
        );
    }

    @Override
    public ApiResult<Integer> disbandFaction(
            String factionId,
            OperationContext context
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return wrongThread();
        }

        Faction faction =
                requireFaction(
                        factionId
                );

        if (faction == null) {
            return notFound(
                    "api.faction-not-found"
            );
        }

        OperationResult<Integer> result =
                lifecycleService.disband(
                        faction,
                        safeContext(context)
                );

        return ApiResult.from(
                result,
                result.getValue()
        );
    }

    @Override
    public ApiResult<ClaimResultView> claimSingle(
            Player actor,
            String factionId,
            FLocation location,
            OperationContext context
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return wrongThread();
        }

        Faction faction =
                requireFaction(
                        factionId
                );

        if (faction == null) {
            return notFound(
                    "api.faction-not-found"
            );
        }

        OperationResult<ClaimBatchResult> result =
                plugin.getClaimManager()
                        .getService()
                        .claimSingle(
                                actor,
                                faction,
                                location,
                                safeContext(context)
                        );

        return ApiResult.from(
                result,
                result.hasValue()
                        ? snapshotClaim(
                                result.getValue()
                        )
                        : null
        );
    }

    @Override
    public ApiResult<ClaimResultView> claimRadius(
            Player actor,
            String factionId,
            FLocation center,
            int radius,
            OperationContext context
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return wrongThread();
        }

        Faction faction =
                requireFaction(
                        factionId
                );

        if (faction == null) {
            return notFound(
                    "api.faction-not-found"
            );
        }

        OperationResult<ClaimBatchResult> result =
                plugin.getClaimManager()
                        .getService()
                        .claimRadius(
                                actor,
                                faction,
                                center,
                                radius,
                                safeContext(context)
                        );

        return ApiResult.from(
                result,
                result.hasValue()
                        ? snapshotClaim(
                                result.getValue()
                        )
                        : null
        );
    }

    @Override
    public ApiResult<ClaimResultView> claimFill(
            Player actor,
            String factionId,
            FLocation start,
            OperationContext context
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return wrongThread();
        }

        Faction faction =
                requireFaction(
                        factionId
                );

        if (faction == null) {
            return notFound(
                    "api.faction-not-found"
            );
        }

        OperationResult<ClaimBatchResult> result =
                plugin.getClaimManager()
                        .getService()
                        .claimFill(
                                actor,
                                faction,
                                start,
                                safeContext(context)
                        );

        return ApiResult.from(
                result,
                result.hasValue()
                        ? snapshotClaim(
                                result.getValue()
                        )
                        : null
        );
    }

    @Override
    public ApiResult<UnclaimResultView> unclaimSingle(
            Player actor,
            String factionId,
            FLocation location,
            OperationContext context
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return wrongThread();
        }

        Faction faction =
                requireFaction(
                        factionId
                );

        if (faction == null) {
            return notFound(
                    "api.faction-not-found"
            );
        }

        OperationResult<UnclaimBatchResult> result =
                plugin.getClaimManager()
                        .getUnclaimService()
                        .unclaimSingle(
                                actor,
                                faction,
                                location,
                                safeContext(context)
                        );

        return ApiResult.from(
                result,
                result.hasValue()
                        ? snapshotUnclaim(
                                result.getValue()
                        )
                        : null
        );
    }

    @Override
    public ApiResult<UnclaimResultView> unclaimRadius(
            Player actor,
            String factionId,
            FLocation center,
            int radius,
            OperationContext context
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return wrongThread();
        }

        Faction faction =
                requireFaction(
                        factionId
                );

        if (faction == null) {
            return notFound(
                    "api.faction-not-found"
            );
        }

        OperationResult<UnclaimBatchResult> result =
                plugin.getClaimManager()
                        .getUnclaimService()
                        .unclaimRadius(
                                actor,
                                faction,
                                center,
                                radius,
                                safeContext(context)
                        );

        return ApiResult.from(
                result,
                result.hasValue()
                        ? snapshotUnclaim(
                                result.getValue()
                        )
                        : null
        );
    }

    @Override
    public ApiResult<UnclaimResultView> unclaimAll(
            Player actor,
            String factionId,
            OperationContext context
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return wrongThread();
        }

        Faction faction =
                requireFaction(
                        factionId
                );

        if (faction == null) {
            return notFound(
                    "api.faction-not-found"
            );
        }

        OperationResult<UnclaimBatchResult> result =
                plugin.getClaimManager()
                        .getUnclaimService()
                        .unclaimAll(
                                actor,
                                faction,
                                safeContext(context)
                        );

        return ApiResult.from(
                result,
                result.hasValue()
                        ? snapshotUnclaim(
                                result.getValue()
                        )
                        : null
        );
    }

    @Override
    public ApiResult<String> setGlobalZoneById(
            FLocation location,
            String zoneId,
            OperationContext context
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return wrongThread();
        }

        OperationResult<String> result =
                plugin.getClaimManager()
                        .getZoneService()
                        .setZone(
                                location,
                                zoneId,
                                safeContext(context)
                        );

        return ApiResult.from(
                result,
                result.hasValue()
                        ? result.getValue()
                        : null
        );
    }

    @Override
    public ApiResult<String> setGlobalZone(
            FLocation location,
            GlobalZoneType type,
            OperationContext context
    ) {
        return setGlobalZoneById(
                location,
                type != null
                        ? type.getConfigKey()
                        : null,
                context
        );
    }

    @Override
    public ApiResult<String> clearGlobalZoneById(
            FLocation location,
            String expectedZoneId,
            OperationContext context
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return wrongThread();
        }

        OperationResult<String> result =
                plugin.getClaimManager()
                        .getZoneService()
                        .clearZone(
                                location,
                                expectedZoneId,
                                safeContext(context)
                        );

        return ApiResult.from(
                result,
                result.hasValue()
                        ? result.getValue()
                        : null
        );
    }

    @Override
    public ApiResult<String> clearGlobalZone(
            FLocation location,
            GlobalZoneType expectedType,
            OperationContext context
    ) {
        return clearGlobalZoneById(
                location,
                expectedType != null
                        ? expectedType.getConfigKey()
                        : null,
                context
        );
    }

    @Override
    public ApiResult<Boolean> reloadProgression(
            OperationContext context
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return wrongThread();
        }

        if (plugin.getQuestManager() == null) {
            return ApiResult.failure(
                    ApiResult.Status.UNAVAILABLE,
                    "api.progression-unavailable",
                    null
            );
        }

        boolean reloaded =
                plugin.getQuestManager()
                        .reloadConfig(
                                safeContext(context)
                        );

        if (!reloaded) {
            return ApiResult.failure(
                    ApiResult.Status.FAILED,
                    "api.progression-reload-failed",
                    null
            );
        }

        return ApiResult.success(
                Boolean.TRUE
        );
    }

    // ============================================================
    // Snapshot factories
    // ============================================================

    private static PositionView snapshotPosition(StoredLocation location) {
        return location != null
                ? new PositionView(
                        location.getWorldName(),
                        location.getX(),
                        location.getY(),
                        location.getZ(),
                        location.getYaw(),
                        location.getPitch())
                : null;
    }

    private static WarpView snapshotWarp(FactionWarp warp) {
        if (warp == null || warp.getStoredLocation() == null) return null;
        return new WarpView(
                warp.getName(),
                snapshotPosition(warp.getStoredLocation()),
                warp.isPasswordProtected(),
                warp.getCreatedAt(),
                warp.getCreatedBy(),
                warp.getUpdatedAt());
    }

    private static FactionLogView snapshotLog(FactionLog log) {
        return new FactionLogView(
                log.getId(),
                log.getFactionId(),
                log.getType() != null ? log.getType().name() : null,
                log.getPlayerUuid(),
                log.getPlayerName(),
                log.getTargetUuid(),
                log.getTargetName(),
                log.getDetails(),
                log.getTimestamp());
    }

    private FactionView snapshotFaction(
            Faction faction
    ) {
        if (faction == null
                || faction.isSystemFaction()) {
            return null;
        }

        List<MemberView> members =
                new ArrayList<MemberView>();

        for (UUID memberId
                : faction.getMembers()) {
            MemberView member =
                    snapshotMember(
                            faction,
                            memberId
                    );

            if (member != null) {
                members.add(member);
            }
        }

        Collections.sort(
                members,
                new Comparator<MemberView>() {
                    @Override
                    public int compare(
                            MemberView first,
                            MemberView second
                    ) {
                        String a =
                                first.getName() != null
                                        ? first.getName()
                                        : first.getUuid()
                                                .toString();

                        String b =
                                second.getName() != null
                                        ? second.getName()
                                        : second.getUuid()
                                                .toString();

                        return a.compareToIgnoreCase(b);
                    }
                }
        );

        int onlineCount = 0;

        for (MemberView member : members) {
            if (member != null
                    && member.isOnline()) {
                onlineCount++;
            }
        }

        int baseMaxMembers =
                getDefaultMaxMembers();

        int maxMembers =
                baseMaxMembers
                        + Math.max(
                                0,
                                faction.getExtraMembers()
                        );

        int baseMaxWarps =
                getDefaultMaxWarps();

        int maxWarps =
                baseMaxWarps
                        + Math.max(
                                0,
                                faction.getExtraWarps()
                        );

        return new FactionView(
                faction.getId(),
                faction.getName(),
                faction.getTag(),
                faction.getDescription(),
                faction.getLeader(),
                members,
                onlineCount,
                faction.getClaimCount(),
                plugin.getClaimManager()
                        .getMaxClaims(faction),
                faction.getClaimGroupCount(),
                faction.getWarpCount(),
                maxWarps,
                maxMembers,
                faction.getAllies().size(),
                faction.getEnemies().size(),
                faction.getTruces().size(),
                faction.getPower(),
                faction.getMaxPower(),
                faction.getBankMinor(),
                faction.getBalance(),
                faction.getLevel(),
                faction.isOpen(),
                faction.isPermanent(),
                plugin.getClaimManager()
                        .isRaidable(faction),
                faction.hasChest(),
                faction.isFactionFlyEnabled(),
                faction.isAntiSethomeEnabled(),
                faction.getCreatedAt(),
                faction.getLastActivity(),
                faction.getRevision()
        );
    }

    private MemberView snapshotMember(
            Faction faction,
            UUID playerId
    ) {
        if (faction == null
                || playerId == null
                || !faction.isMember(
                        playerId
                )) {
            return null;
        }

        FPlayer fPlayer =
                plugin.getFPlayerManager()
                        .findLoaded(playerId);

        Player online =
                Bukkit.getPlayer(
                        playerId
                );

        FactionRole role =
                faction.getRole(
                        playerId
                );

        return new MemberView(
                playerId,
                fPlayer != null
                        && fPlayer.getLastKnownName() != null
                        && !fPlayer.getLastKnownName()
                                .trim()
                                .isEmpty()
                        ? fPlayer.getLastKnownName()
                        : online != null
                                ? online.getName()
                                : null,
                role != null
                        ? role.name()
                        : null,
                online != null
        );
    }

    private PlayerView snapshotPlayer(
            FPlayer fPlayer
    ) {
        if (fPlayer == null) {
            return null;
        }

        return new PlayerView(
                fPlayer.getUuid(),
                fPlayer.getLastKnownName(),
                fPlayer.getFactionId(),
                fPlayer.getRole() != null
                        ? fPlayer.getRole()
                                .name()
                        : null,
                fPlayer.getRole() != null
                        ? fPlayer.getRole()
                                .getDisplayName()
                        : null,
                fPlayer.getRole() != null
                        ? fPlayer.getRole()
                                .getPrefix()
                        : "",
                fPlayer.getChatMode() != null
                        ? fPlayer.getChatMode()
                                .name()
                        : null,
                fPlayer.getPower(),
                fPlayer.getMaxPower(),
                Bukkit.getPlayer(
                        fPlayer.getUuid()
                ) != null,
                fPlayer.isBypassing(),
                fPlayer.isMapAutoUpdateEnabled(),
                fPlayer.getFirstJoin(),
                fPlayer.getLastSeen(),
                fPlayer.getKills(),
                fPlayer.getDeaths()
        );
    }

    private static ProgressionView snapshotProgression(
            ProgressionStatus status,
            String tierDisplayName
    ) {
        if (status == null) {
            return null;
        }

        return new ProgressionView(
                status.getHealth()
                        .name(),
                status.getFactionLevel(),
                status.getLevelStarted(),
                status.getMaxLevel(),
                status.getTierId(),
                tierDisplayName,
                status.getCompletedQuests(),
                status.getTotalQuests(),
                status.getPercent(),
                status.getPendingTransition(),
                status.getPendingRewards(),
                status.getLastProgressAt(),
                status.getLastLevelUpAt(),
                status.getTransitionRevision()
        );
    }

    private static ClaimResultView snapshotClaim(
            ClaimBatchResult result
    ) {
        if (result == null) {
            return null;
        }

        List<ChunkView> locations =
                new ArrayList<ChunkView>();

        for (FLocation location
                : result.getClaimedLocations()) {
            ChunkView view =
                    ChunkView.from(
                            location
                    );

            if (view != null) {
                locations.add(view);
            }
        }

        return new ClaimResultView(
                result.getMode() != null
                        ? result.getMode()
                                .name()
                        : null,
                result.getRequestedCount(),
                result.getClaimedCount(),
                result.getSkippedOwnedCount(),
                result.getOverclaimCount(),
                locations
        );
    }

    private ZoneView snapshotZone(
            ZoneDefinition definition
    ) {
        List<String> allowed =
                new ArrayList<String>();

        for (TerritoryAction action
                : definition.getAllowedActions()) {
            allowed.add(
                    action.name()
            );
        }

        List<String> denied =
                new ArrayList<String>();

        for (TerritoryAction action
                : definition.getDeniedActions()) {
            denied.add(
                    action.name()
            );
        }

        Collections.sort(allowed);
        Collections.sort(denied);

        return new ZoneView(
                definition.getId(),
                definition.getDisplayName(),
                definition.getColor(),
                definition.getMapSymbol(),
                definition.getTitle(),
                definition.getSubtitle(),
                definition.getEnterMessage(),
                definition.isPvpAllowed(),
                definition.getDefaultPolicy()
                        .name(),
                allowed,
                denied,
                plugin.getClaimManager()
                        .getZoneService()
                        .count(
                                definition.getId()
                        ),
                definition.isConfigured()
        );
    }

    private static UnclaimResultView snapshotUnclaim(
            UnclaimBatchResult result
    ) {
        if (result == null) {
            return null;
        }

        List<ChunkView> locations =
                new ArrayList<ChunkView>();

        for (FLocation location
                : result.getLocations()) {
            ChunkView view =
                    ChunkView.from(
                            location
                    );

            if (view != null) {
                locations.add(view);
            }
        }

        return new UnclaimResultView(
                result.getType() != null
                        ? result.getType()
                                .name()
                        : null,
                result.getUnclaimedCount(),
                result.getClaimGroupAssignmentsRemoved(),
                result.isHomeRemoved(),
                result.getRemovedWarps(),
                locations
        );
    }

    // ============================================================
    // Helpers
    // ============================================================

    private Faction requireFaction(
            String factionId
    ) {
        if (factionId == null
                || factionId.trim()
                        .isEmpty()) {
            return null;
        }

        Faction faction =
                plugin.getFactionManager()
                        .getFaction(
                                factionId
                        );

        return faction != null
                && !faction.isSystemFaction()
                ? faction
                : null;
    }

    private static OperationContext safeContext(
            OperationContext context
    ) {
        return context != null
                ? context
                : OperationContext.system();
    }

    private static <T> ApiResult<T> wrongThread() {
        return ApiResult.failure(
                ApiResult.Status.UNAVAILABLE,
                "api.main-thread-required",
                "Trusted mutations must be called on the Bukkit primary thread"
        );
    }

    private static <T> ApiResult<T> notFound(
            String messageKey
    ) {
        return ApiResult.failure(
                ApiResult.Status.NOT_FOUND,
                messageKey,
                null
        );
    }
}
