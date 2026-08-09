package me.krunsh.kfaction.storage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import me.krunsh.kfaction.data.ClaimGroup;
import me.krunsh.kfaction.data.ClaimGroup.Rule;
import me.krunsh.kfaction.data.FLocation;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionQuest;
import me.krunsh.kfaction.data.FactionRole;
import me.krunsh.kfaction.data.FactionWarp;
import me.krunsh.kfaction.data.PermissionAction;
import me.krunsh.kfaction.data.QuestCategory;
import me.krunsh.kfaction.data.QuestType;
import me.krunsh.kfaction.data.Relation;
import me.krunsh.kfaction.data.StoredLocation;
import me.krunsh.kfaction.permissions.TerritoryAction;
import me.krunsh.kfaction.progression.FactionProgressState;

/**
 * Codec JSON unique de la persistance Kfaction.
 *
 * captureFaction/captureFPlayer doivent être appelés sur le thread principal.
 * Une fois le StorageSnapshot créé, le payload String est immuable et peut
 * être écrit depuis le writer asynchrone sans toucher au domaine vivant.
 *
 * Le decoder reste compatible avec les anciens JSON V1.
 */
public final class JsonStorageCodec {

    private final Gson gson;

    public JsonStorageCodec() {
        this.gson = new GsonBuilder()
                .disableHtmlEscaping()
                .create();
    }

    // ============================================================
    // Capture immutable
    // ============================================================

    public StorageSnapshot captureFaction(
            Faction faction
    ) {
        if (faction == null || faction.isSystemFaction()) {
            throw new IllegalArgumentException(
                    "Cannot snapshot null/system faction"
            );
        }

        JsonObject json = serializeFaction(faction);
        return StorageSnapshot.faction(
                faction.getId(),
                gson.toJson(json)
        );
    }

    public StorageSnapshot captureFPlayer(
            FPlayer fPlayer
    ) {
        if (fPlayer == null || fPlayer.getUuid() == null) {
            throw new IllegalArgumentException(
                    "Cannot snapshot null FPlayer"
            );
        }

        JsonObject json = serializeFPlayer(fPlayer);
        return StorageSnapshot.player(
                fPlayer.getUuid().toString(),
                gson.toJson(json)
        );
    }

    // ============================================================
    // Decode
    // ============================================================

    public Faction decodeFaction(String payloadJson) {
        if (payloadJson == null || payloadJson.trim().isEmpty()) {
            return null;
        }

        JsonElement parsed =
                new JsonParser().parse(payloadJson);

        if (!parsed.isJsonObject()) {
            return null;
        }

        return deserializeFaction(parsed.getAsJsonObject());
    }

    public FPlayer decodeFPlayer(String payloadJson) {
        if (payloadJson == null || payloadJson.trim().isEmpty()) {
            return null;
        }

        JsonElement parsed =
                new JsonParser().parse(payloadJson);

        if (!parsed.isJsonObject()) {
            return null;
        }

        return deserializeFPlayer(parsed.getAsJsonObject());
    }

    // ============================================================
    // Faction encode
    // ============================================================

    private JsonObject serializeFaction(Faction faction) {
        JsonObject json = new JsonObject();

        json.addProperty(
                "storageSchemaVersion",
                StorageSnapshot.CURRENT_SCHEMA_VERSION
        );

        json.addProperty("id", faction.getId());
        json.addProperty("name", faction.getName());

        if (faction.getTag() != null) {
            json.addProperty("tag", faction.getTag());
        }

        if (faction.getDescription() != null) {
            json.addProperty(
                    "description",
                    faction.getDescription()
            );
        }

        if (faction.getLeader() != null) {
            json.addProperty(
                    "leader",
                    faction.getLeader().toString()
            );
        }

        JsonArray members = new JsonArray();
        for (UUID memberId : faction.getMembers()) {
            JsonObject member = new JsonObject();
            member.addProperty(
                    "uuid",
                    memberId.toString()
            );

            FactionRole role =
                    faction.getRole(memberId);

            if (role != null) {
                member.addProperty(
                        "role",
                        role.name()
                );
            }

            members.add(member);
        }
        json.add("members", members);

        JsonArray claims = new JsonArray();
        for (FLocation claim : faction.getClaims()) {
            claims.add(claim.getKey());
        }
        json.add("claims", claims);

        // Claim Groups V2 - schema payload 3.
        JsonArray claimGroups = new JsonArray();

        for (ClaimGroup group
                : faction.getClaimGroups()
                        .values()) {
            JsonObject groupJson =
                    new JsonObject();

            groupJson.addProperty(
                    "id",
                    group.getId()
            );

            JsonObject roleRules =
                    new JsonObject();

            for (Map.Entry<FactionRole,
                    Map<TerritoryAction, Rule>> roleEntry
                    : group.getRoleRulesSnapshot()
                            .entrySet()) {
                JsonObject actions =
                        new JsonObject();

                for (Map.Entry<TerritoryAction, Rule> actionEntry
                        : roleEntry.getValue()
                                .entrySet()) {
                    actions.addProperty(
                            actionEntry.getKey()
                                    .name(),
                            actionEntry.getValue()
                                    .name()
                    );
                }

                roleRules.add(
                        roleEntry.getKey()
                                .name(),
                        actions
                );
            }

            groupJson.add(
                    "roleRules",
                    roleRules
            );

            JsonObject relationRules =
                    new JsonObject();

            for (Map.Entry<Relation,
                    Map<TerritoryAction, Rule>> relationEntry
                    : group.getRelationRulesSnapshot()
                            .entrySet()) {
                JsonObject actions =
                        new JsonObject();

                for (Map.Entry<TerritoryAction, Rule> actionEntry
                        : relationEntry.getValue()
                                .entrySet()) {
                    actions.addProperty(
                            actionEntry.getKey()
                                    .name(),
                            actionEntry.getValue()
                                    .name()
                    );
                }

                relationRules.add(
                        relationEntry.getKey()
                                .name(),
                        actions
                );
            }

            groupJson.add(
                    "relationRules",
                    relationRules
            );

            claimGroups.add(groupJson);
        }

        json.add(
                "claimGroups",
                claimGroups
        );

        JsonObject claimGroupAssignments =
                new JsonObject();

        for (Map.Entry<FLocation, String> entry
                : faction.getClaimGroupAssignmentsSnapshot()
                        .entrySet()) {
            if (entry.getKey() != null
                    && entry.getValue() != null) {
                claimGroupAssignments.addProperty(
                        entry.getKey().getKey(),
                        entry.getValue()
                );
            }
        }

        json.add(
                "claimGroupAssignments",
                claimGroupAssignments
        );

        if (faction.hasHome()
                && faction.getStoredHome() != null) {
            json.add(
                    "home",
                    serializeStoredLocation(
                            faction.getStoredHome()
                    )
            );
        }

        /*
         * Warp payload V2.
         *
         * Le hash peut être persisté, jamais le mot de passe brut.
         */
        JsonObject warps = new JsonObject();

        for (Map.Entry<String, FactionWarp> entry
                : faction.getWarpDataSnapshot()
                        .entrySet()) {
            if (entry.getKey() == null
                    || entry.getValue() == null) {
                continue;
            }

            FactionWarp warp =
                    entry.getValue();

            JsonObject warpJson =
                    new JsonObject();

            warpJson.add(
                    "location",
                    serializeStoredLocation(
                            warp.getStoredLocation()
                    )
            );

            if (warp.getPasswordHash() != null) {
                warpJson.addProperty(
                        "passwordHash",
                        warp.getPasswordHash()
                );
            }

            warpJson.addProperty(
                    "createdAt",
                    warp.getCreatedAt()
            );

            if (warp.getCreatedBy() != null) {
                warpJson.addProperty(
                        "createdBy",
                        warp.getCreatedBy()
                );
            }

            warpJson.addProperty(
                    "updatedAt",
                    warp.getUpdatedAt()
            );

            warps.add(
                    warp.getName(),
                    warpJson
            );
        }

        json.add(
                "warps",
                warps
        );

        json.addProperty(
                "powerBoost",
                faction.getPowerBoost()
        );

        JsonObject relations = new JsonObject();
        for (Map.Entry<String, Relation> entry
                : faction.getAllRelations().entrySet()) {
            if (entry.getKey() != null
                    && entry.getValue() != null) {
                relations.addProperty(
                        entry.getKey(),
                        entry.getValue().name()
                );
            }
        }
        json.add("relations", relations);

        JsonObject relationRequests = new JsonObject();
        for (Map.Entry<String, Long> entry
                : faction.getRelationRequestsSnapshot()
                        .entrySet()) {
            if (entry.getKey() != null
                    && entry.getValue() != null) {
                relationRequests.addProperty(
                        entry.getKey(),
                        entry.getValue()
                );
            }
        }
        json.add(
                "relationRequests",
                relationRequests
        );

        JsonObject invites = new JsonObject();
        for (Map.Entry<UUID, Long> entry
                : faction.getInvitesSnapshot().entrySet()) {
            if (entry.getKey() != null
                    && entry.getValue() != null) {
                invites.addProperty(
                        entry.getKey().toString(),
                        entry.getValue()
                );
            }
        }
        json.add("invites", invites);

        /*
         * Economy V2:
         * bankMinor est canonique.
         * bank reste écrit comme façade de compatibilité avec les anciens
         * lecteurs/outils.
         */
        json.addProperty(
                "bankMinor",
                faction.getBankMinor()
        );

        json.addProperty(
                "bank",
                faction.getBank()
        );

        json.addProperty(
                "tntBank",
                faction.getTntBank()
        );
        json.addProperty(
                "tntEnabled",
                faction.isTntEnabled()
        );

        json.addProperty(
                "createdAt",
                faction.getCreatedAt()
        );
        json.addProperty(
                "lastActivity",
                faction.getLastActivity()
        );
        json.addProperty(
                "permanent",
                faction.isPermanent()
        );
        json.addProperty(
                "open",
                faction.isOpen()
        );

        JsonObject permissions = new JsonObject();
        for (Map.Entry<FactionRole, Set<PermissionAction>> entry
                : faction.getAllPermissions().entrySet()) {
            if (entry.getKey() == null
                    || entry.getValue() == null) {
                continue;
            }

            JsonArray actions = new JsonArray();
            for (PermissionAction action : entry.getValue()) {
                if (action != null) {
                    actions.add(action.name());
                }
            }

            permissions.add(
                    entry.getKey().name(),
                    actions
            );
        }
        json.add("permissions", permissions);

        JsonObject relationPermissions =
                new JsonObject();

        for (Map.Entry<Relation, Set<PermissionAction>> entry
                : faction.getRelationPermissions().entrySet()) {
            if (entry.getKey() == null
                    || entry.getValue() == null) {
                continue;
            }

            JsonArray actions = new JsonArray();
            for (PermissionAction action : entry.getValue()) {
                if (action != null) {
                    actions.add(action.name());
                }
            }

            relationPermissions.add(
                    entry.getKey().name(),
                    actions
            );
        }

        json.add(
                "relationPermissions",
                relationPermissions
        );

        json.addProperty(
                "level",
                faction.getLevel()
        );
        json.addProperty(
                "currentXp",
                faction.getCurrentXp()
        );

        if (faction.getActiveCategory() != null) {
            json.addProperty(
                    "activeCategory",
                    faction.getActiveCategory()
                            .getConfigKey()
            );
        }

        JsonArray activeQuests = new JsonArray();

        for (FactionQuest quest
                : faction.getActiveQuests()) {
            if (quest == null) {
                continue;
            }

            JsonObject questJson = new JsonObject();

            questJson.addProperty(
                    "id",
                    quest.getId()
            );
            questJson.addProperty(
                    "type",
                    quest.getType().getConfigKey()
            );
            questJson.addProperty(
                    "category",
                    quest.getCategory().getConfigKey()
            );
            questJson.addProperty(
                    "target",
                    quest.getTarget()
            );

            if (quest.getSparrowItemId() != null) {
                questJson.addProperty(
                        "sparrowmcItem",
                        quest.getSparrowItemId()
                );
            }

            questJson.addProperty(
                    "displayName",
                    quest.getDisplayName()
            );
            questJson.addProperty(
                    "progress",
                    quest.getProgress()
            );
            questJson.addProperty(
                    "required",
                    quest.getRequired()
            );
            questJson.addProperty(
                    "xpReward",
                    quest.getXpReward()
            );
            questJson.addProperty(
                    "completed",
                    quest.isCompleted()
            );

            activeQuests.add(questJson);
        }

        json.add(
                "activeQuests",
                activeQuests
        );

        serializeProgression(
                json,
                faction.getProgressionState()
        );

        JsonArray appliedRewards = new JsonArray();
        for (String reward
                : faction.getAppliedRewards()) {
            if (reward != null) {
                appliedRewards.add(reward);
            }
        }
        json.add(
                "appliedRewards",
                appliedRewards
        );

        json.addProperty(
                "chestSize",
                faction.getChestSize()
        );

        if (faction.getChestContentsB64() != null) {
            json.addProperty(
                    "chestContentsB64",
                    faction.getChestContentsB64()
            );
        }

        json.addProperty(
                "factionFlyEnabled",
                faction.isFactionFlyEnabled()
        );
        json.addProperty(
                "antiSethomeEnabled",
                faction.isAntiSethomeEnabled()
        );
        json.addProperty(
                "extraWarps",
                faction.getExtraWarps()
        );
        json.addProperty(
                "extraMembers",
                faction.getExtraMembers()
        );
        json.addProperty(
                "extraPowerBoost",
                faction.getExtraPowerBoost()
        );

        return json;
    }

    private void serializeProgression(
            JsonObject root,
            FactionProgressState state
    ) {
        JsonObject progression = new JsonObject();

        progression.addProperty(
                "schemaVersion",
                state.getSchemaVersion()
        );
        progression.addProperty(
                "levelStarted",
                state.getLevelStarted()
        );

        if (state.getLockedTierId() != null) {
            progression.addProperty(
                    "lockedTierId",
                    state.getLockedTierId()
            );
            progression.addProperty(
                    "lockedTierRank",
                    state.getLockedTierRank()
            );
        }

        if (state.getPendingTransition() != null) {
            progression.addProperty(
                    "pendingTransition",
                    state.getPendingTransition()
            );
        }

        progression.addProperty(
                "lastProgressAt",
                state.getLastProgressAt()
        );

        progression.addProperty(
                "lastLevelUpAt",
                state.getLastLevelUpAt()
        );

        progression.addProperty(
                "transitionRevision",
                state.getTransitionRevision()
        );

        JsonObject questProgress = new JsonObject();
        for (Map.Entry<String, Long> entry
                : state.snapshotProgress().entrySet()) {
            questProgress.addProperty(
                    entry.getKey(),
                    entry.getValue()
            );
        }
        progression.add(
                "questProgress",
                questProgress
        );

        JsonObject archivedProgress = new JsonObject();
        for (Map.Entry<String, Long> entry
                : state.snapshotArchivedProgress()
                        .entrySet()) {
            archivedProgress.addProperty(
                    entry.getKey(),
                    entry.getValue()
            );
        }
        progression.add(
                "archivedProgress",
                archivedProgress
        );

        JsonArray pendingRewards = new JsonArray();
        for (String reward : state.getPendingRewards()) {
            if (reward != null) {
                pendingRewards.add(reward);
            }
        }
        progression.add(
                "pendingRewards",
                pendingRewards
        );

        JsonObject pendingRewardRecords =
                new JsonObject();

        for (Map.Entry<String, FactionProgressState.PendingRewardRecord> entry
                : state.snapshotPendingRewardRecords()
                        .entrySet()) {
            if (entry.getKey() == null
                    || entry.getValue() == null) {
                continue;
            }

            FactionProgressState.PendingRewardRecord value =
                    entry.getValue();

            JsonObject record =
                    new JsonObject();

            record.addProperty(
                    "status",
                    value.getStatus().name()
            );

            record.addProperty(
                    "attempts",
                    value.getAttempts()
            );

            record.addProperty(
                    "lastAttemptAt",
                    value.getLastAttemptAt()
            );

            if (value.getDetail() != null) {
                record.addProperty(
                        "detail",
                        value.getDetail()
                );
            }

            pendingRewardRecords.add(
                    entry.getKey(),
                    record
            );
        }

        progression.add(
                "pendingRewardRecords",
                pendingRewardRecords
        );

        JsonObject pvpLedger = new JsonObject();

        for (Map.Entry<String, FactionProgressState.PvpKillRecord> entry
                : state.snapshotPvpKillLedger().entrySet()) {
            FactionProgressState.PvpKillRecord value =
                    entry.getValue();

            if (entry.getKey() == null || value == null) {
                continue;
            }

            JsonObject record = new JsonObject();
            record.addProperty(
                    "lastKillMillis",
                    value.getLastKillMillis()
            );
            record.addProperty(
                    "epochDay",
                    value.getEpochDay()
            );
            record.addProperty(
                    "dailyCount",
                    value.getDailyCount()
            );

            pvpLedger.add(
                    entry.getKey(),
                    record
            );
        }

        progression.add(
                "pvpKillLedger",
                pvpLedger
        );

        root.add(
                "progression",
                progression
        );
    }

    // ============================================================
    // Faction decode
    // ============================================================

    private Faction deserializeFaction(
            JsonObject json
    ) {
        String id =
                requireString(json, "id");

        String name =
                requireString(json, "name");

        UUID leader = null;

        if (hasValue(json, "leader")) {
            leader = parseUuid(
                    json.get("leader").getAsString()
            );
        }

        long createdAt =
                getLong(
                        json,
                        "createdAt",
                        System.currentTimeMillis()
                );

        long lastActivity =
                getLong(
                        json,
                        "lastActivity",
                        createdAt
                );

        Faction faction =
                new Faction(
                        id,
                        name,
                        leader,
                        createdAt,
                        lastActivity
                );

        if (hasValue(json, "tag")) {
            faction.setTag(
                    json.get("tag").getAsString()
            );
        }

        if (hasValue(json, "description")) {
            faction.setDescription(
                    json.get("description").getAsString()
            );
        }

        if (json.has("members")
                && json.get("members").isJsonArray()) {
            for (JsonElement element
                    : json.getAsJsonArray("members")) {
                if (!element.isJsonObject()) {
                    continue;
                }

                JsonObject memberJson =
                        element.getAsJsonObject();

                if (!hasValue(memberJson, "uuid")) {
                    continue;
                }

                UUID memberId =
                        parseUuid(
                                memberJson.get("uuid")
                                        .getAsString()
                        );

                if (memberId == null) {
                    continue;
                }

                FactionRole role =
                        parseRole(
                                hasValue(memberJson, "role")
                                        ? memberJson.get("role")
                                                .getAsString()
                                        : null,
                                FactionRole.RECRUIT
                        );

                if (leader == null
                        || !leader.equals(memberId)) {
                    faction.addMember(
                            memberId,
                            role
                    );
                }
            }
        }

        if (json.has("claims")
                && json.get("claims").isJsonArray()) {
            for (JsonElement element
                    : json.getAsJsonArray("claims")) {
                if (element == null
                        || element.isJsonNull()) {
                    continue;
                }

                FLocation location =
                        FLocation.fromKey(
                                element.getAsString()
                        );

                if (location != null) {
                    faction.addClaim(location);
                }
            }
        }

        restoreClaimGroups(
                faction,
                json
        );

        if (json.has("home")
                && json.get("home").isJsonObject()) {
            StoredLocation home =
                    deserializeStoredLocation(
                            json.getAsJsonObject(
                                    "home"
                            )
                    );

            if (home != null) {
                faction.restoreHome(home);
            }
        }

        if (json.has("warps")
                && json.get("warps").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry
                    : json.getAsJsonObject("warps")
                            .entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }

                JsonObject warpJson =
                        entry.getValue()
                                .getAsJsonObject();

                /*
                 * Nouveau format:
                 * name -> { location:{...}, passwordHash:..., ... }
                 *
                 * Ancien format:
                 * name -> { world:..., x:..., y:..., z:... }
                 */
                JsonObject locationJson =
                        warpJson.has("location")
                                && warpJson.get("location")
                                        .isJsonObject()
                                ? warpJson.getAsJsonObject(
                                        "location"
                                )
                                : warpJson;

                StoredLocation location =
                        deserializeStoredLocation(
                                locationJson
                        );

                if (location == null) {
                    continue;
                }

                String passwordHash =
                        getString(
                                warpJson,
                                "passwordHash",
                                null
                        );

                long warpCreatedAt =
                        getLong(
                                warpJson,
                                "createdAt",
                                System.currentTimeMillis()
                        );

                String warpCreatedBy =
                        getString(
                                warpJson,
                                "createdBy",
                                null
                        );

                long warpUpdatedAt =
                        getLong(
                                warpJson,
                                "updatedAt",
                                warpCreatedAt
                        );

                faction.restoreWarp(
                        new FactionWarp(
                                entry.getKey(),
                                location,
                                passwordHash,
                                warpCreatedAt,
                                warpCreatedBy,
                                warpUpdatedAt
                        )
                );
            }
        }

        faction.setPowerBoost(
                getDouble(json, "powerBoost", 0.0D)
        );

        if (json.has("relations")
                && json.get("relations").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry
                    : json.getAsJsonObject("relations")
                            .entrySet()) {
                if (entry.getValue().isJsonNull()) {
                    continue;
                }

                Relation relation =
                        Relation.fromString(
                                entry.getValue()
                                        .getAsString()
                        );

                faction.setRelation(
                        entry.getKey(),
                        relation
                );
            }
        }

        if (json.has("relationRequests")
                && json.get("relationRequests")
                        .isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry
                    : json.getAsJsonObject(
                            "relationRequests"
                    ).entrySet()) {
                if (entry.getValue().isJsonNull()) {
                    continue;
                }

                faction.restoreRelationRequest(
                        entry.getKey(),
                        entry.getValue().getAsLong()
                );
            }
        }

        if (json.has("invites")
                && json.get("invites").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry
                    : json.getAsJsonObject("invites")
                            .entrySet()) {
                UUID playerId =
                        parseUuid(entry.getKey());

                if (playerId == null
                        || entry.getValue().isJsonNull()) {
                    continue;
                }

                faction.restoreInvite(
                        playerId,
                        entry.getValue().getAsLong()
                );
            }
        }

        if (hasValue(
                json,
                "bankMinor"
        )) {
            faction.restoreBankMinor(
                    Math.max(
                            0L,
                            getLong(
                                    json,
                                    "bankMinor",
                                    0L
                            )
                    )
            );
        } else {
            /*
             * Migration automatique des anciens payloads double.
             */
            faction.setBank(
                    getDouble(
                            json,
                            "bank",
                            0.0D
                    )
            );
        }

        faction.setTntBank(
                getInt(json, "tntBank", 0)
        );
        faction.setTntEnabled(
                getBoolean(json, "tntEnabled", true)
        );

        faction.setPermanent(
                getBoolean(json, "permanent", false)
        );
        faction.setOpen(
                getBoolean(json, "open", false)
        );

        restoreRolePermissions(
                faction,
                json
        );
        restoreRelationPermissions(
                faction,
                json
        );

        faction.setLevel(
                getInt(json, "level", 0)
        );
        faction.setCurrentXp(
                getInt(json, "currentXp", 0)
        );

        if (hasValue(json, "activeCategory")) {
            faction.setActiveCategory(
                    QuestCategory.fromConfigKey(
                            json.get("activeCategory")
                                    .getAsString()
                    )
            );
        }

        restoreActiveQuests(
                faction,
                json
        );
        restoreProgression(
                faction,
                json
        );

        if (json.has("appliedRewards")
                && json.get("appliedRewards")
                        .isJsonArray()) {
            for (JsonElement reward
                    : json.getAsJsonArray(
                            "appliedRewards"
                    )) {
                if (reward != null
                        && !reward.isJsonNull()) {
                    faction.addAppliedReward(
                            reward.getAsString()
                    );
                }
            }
        }

        faction.setChestSize(
                getInt(json, "chestSize", 0)
        );

        if (hasValue(json, "chestContentsB64")) {
            faction.setChestContentsB64(
                    json.get("chestContentsB64")
                            .getAsString()
            );
        }

        faction.setFactionFlyEnabled(
                getBoolean(
                        json,
                        "factionFlyEnabled",
                        false
                )
        );

        faction.setAntiSethomeEnabled(
                getBoolean(
                        json,
                        "antiSethomeEnabled",
                        false
                )
        );

        faction.setExtraWarps(
                getInt(json, "extraWarps", 0)
        );
        faction.setExtraMembers(
                getInt(json, "extraMembers", 0)
        );
        faction.setExtraPowerBoost(
                getDouble(
                        json,
                        "extraPowerBoost",
                        0.0D
                )
        );

        return faction;
    }

    private void restoreClaimGroups(
            Faction faction,
            JsonObject root
    ) {
        if (faction == null || root == null) {
            return;
        }

        if (root.has("claimGroups")
                && root.get("claimGroups")
                        .isJsonArray()) {
            for (JsonElement groupElement
                    : root.getAsJsonArray(
                            "claimGroups"
                    )) {
                if (groupElement == null
                        || !groupElement.isJsonObject()) {
                    continue;
                }

                JsonObject groupJson =
                        groupElement.getAsJsonObject();

                if (!hasValue(
                        groupJson,
                        "id"
                )) {
                    continue;
                }

                ClaimGroup group;

                try {
                    group =
                            new ClaimGroup(
                                    groupJson.get("id")
                                            .getAsString()
                            );
                } catch (IllegalArgumentException exception) {
                    continue;
                }

                if (groupJson.has("roleRules")
                        && groupJson.get("roleRules")
                                .isJsonObject()) {
                    JsonObject roleRules =
                            groupJson.getAsJsonObject(
                                    "roleRules"
                            );

                    for (Map.Entry<String, JsonElement> roleEntry
                            : roleRules.entrySet()) {
                        FactionRole role =
                                FactionRole.parse(
                                        roleEntry.getKey()
                                );

                        if (role == null
                                || role == FactionRole.LEADER
                                || !roleEntry.getValue()
                                        .isJsonObject()) {
                            continue;
                        }

                        for (Map.Entry<String, JsonElement> actionEntry
                                : roleEntry.getValue()
                                        .getAsJsonObject()
                                        .entrySet()) {
                            TerritoryAction action =
                                    TerritoryAction.fromConfigKey(
                                            actionEntry.getKey()
                                    );

                            Rule rule =
                                    actionEntry.getValue()
                                                    .isJsonNull()
                                            ? null
                                            : Rule.parse(
                                                    actionEntry.getValue()
                                                            .getAsString()
                                            );

                            if (action != null
                                    && rule != null
                                    && rule != Rule.INHERIT) {
                                group.setRoleRule(
                                        role,
                                        action,
                                        rule
                                );
                            }
                        }
                    }
                }

                if (groupJson.has("relationRules")
                        && groupJson.get("relationRules")
                                .isJsonObject()) {
                    JsonObject relationRules =
                            groupJson.getAsJsonObject(
                                    "relationRules"
                            );

                    for (Map.Entry<String, JsonElement> relationEntry
                            : relationRules.entrySet()) {
                        Relation relation =
                                Relation.fromString(
                                        relationEntry.getKey()
                                );

                        if (relation == null
                                || relation == Relation.MEMBER
                                || !relationEntry.getValue()
                                        .isJsonObject()) {
                            continue;
                        }

                        for (Map.Entry<String, JsonElement> actionEntry
                                : relationEntry.getValue()
                                        .getAsJsonObject()
                                        .entrySet()) {
                            TerritoryAction action =
                                    TerritoryAction.fromConfigKey(
                                            actionEntry.getKey()
                                    );

                            Rule rule =
                                    actionEntry.getValue()
                                                    .isJsonNull()
                                            ? null
                                            : Rule.parse(
                                                    actionEntry.getValue()
                                                            .getAsString()
                                            );

                            if (action != null
                                    && rule != null
                                    && rule != Rule.INHERIT) {
                                group.setRelationRule(
                                        relation,
                                        action,
                                        rule
                                );
                            }
                        }
                    }
                }

                faction.restoreClaimGroup(group);
            }
        }

        /*
         * Les assignments sont restaurés APRÈS les groupes et APRÈS claims.
         * Faction.restoreClaimGroupAssignment revalide donc les deux côtés.
         */
        if (root.has("claimGroupAssignments")
                && root.get("claimGroupAssignments")
                        .isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry
                    : root.getAsJsonObject(
                            "claimGroupAssignments"
                    ).entrySet()) {
                if (entry.getValue() == null
                        || entry.getValue()
                                .isJsonNull()) {
                    continue;
                }

                FLocation location =
                        FLocation.fromKey(
                                entry.getKey()
                        );

                if (location == null) {
                    continue;
                }

                faction.restoreClaimGroupAssignment(
                        location,
                        entry.getValue()
                                .getAsString()
                );
            }
        }
    }

    private void restoreRolePermissions(
            Faction faction,
            JsonObject root
    ) {
        if (!root.has("permissions")
                || !root.get("permissions")
                        .isJsonObject()) {
            return;
        }

        JsonObject permissions =
                root.getAsJsonObject("permissions");

        for (Map.Entry<String, JsonElement> entry
                : permissions.entrySet()) {
            FactionRole role =
                    parseRole(
                            entry.getKey(),
                            null
                    );

            if (role == null
                    || role == FactionRole.LEADER
                    || !entry.getValue().isJsonArray()) {
                continue;
            }

            for (PermissionAction action
                    : PermissionAction.values()) {
                faction.setPermission(
                        role,
                        action,
                        false
                );
            }

            for (JsonElement actionElement
                    : entry.getValue().getAsJsonArray()) {
                PermissionAction action =
                        parseAction(actionElement);

                if (action != null) {
                    faction.setPermission(
                            role,
                            action,
                            true
                    );
                }
            }
        }
    }

    private void restoreRelationPermissions(
            Faction faction,
            JsonObject root
    ) {
        if (!root.has("relationPermissions")
                || !root.get("relationPermissions")
                        .isJsonObject()) {
            return;
        }

        JsonObject relationPermissions =
                root.getAsJsonObject(
                        "relationPermissions"
                );

        for (Map.Entry<String, JsonElement> entry
                : relationPermissions.entrySet()) {
            Relation relation;

            try {
                relation =
                        Relation.valueOf(
                                entry.getKey()
                        );
            } catch (IllegalArgumentException exception) {
                continue;
            }

            if (!entry.getValue().isJsonArray()) {
                continue;
            }

            for (PermissionAction action
                    : PermissionAction.values()) {
                faction.setPermission(
                        relation,
                        action,
                        false
                );
            }

            for (JsonElement actionElement
                    : entry.getValue().getAsJsonArray()) {
                PermissionAction action =
                        parseAction(actionElement);

                if (action != null) {
                    faction.setPermission(
                            relation,
                            action,
                            true
                    );
                }
            }
        }
    }

    private void restoreActiveQuests(
            Faction faction,
            JsonObject root
    ) {
        if (!root.has("activeQuests")
                || !root.get("activeQuests")
                        .isJsonArray()) {
            return;
        }

        List<FactionQuest> quests =
                new ArrayList<>();

        for (JsonElement element
                : root.getAsJsonArray("activeQuests")) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject questJson =
                    element.getAsJsonObject();

            if (!hasValue(questJson, "id")
                    || !hasValue(questJson, "type")
                    || !hasValue(questJson, "category")
                    || !hasValue(questJson, "target")
                    || !hasValue(questJson, "displayName")
                    || !hasValue(questJson, "required")
                    || !hasValue(questJson, "xpReward")) {
                continue;
            }

            QuestType type =
                    QuestType.fromConfigKey(
                            questJson.get("type")
                                    .getAsString()
                    );

            QuestCategory category =
                    QuestCategory.fromConfigKey(
                            questJson.get("category")
                                    .getAsString()
                    );

            if (type == null || category == null) {
                continue;
            }

            FactionQuest quest =
                    new FactionQuest(
                            questJson.get("id")
                                    .getAsString(),
                            type,
                            category,
                            questJson.get("target")
                                    .getAsString(),
                            hasValue(
                                    questJson,
                                    "sparrowmcItem"
                            )
                                    ? questJson.get(
                                            "sparrowmcItem"
                                    ).getAsString()
                                    : null,
                            questJson.get("displayName")
                                    .getAsString(),
                            questJson.get("required")
                                    .getAsInt(),
                            questJson.get("xpReward")
                                    .getAsInt()
                    );

            quest.setProgress(
                    getInt(
                            questJson,
                            "progress",
                            0
                    )
            );

            quest.setCompleted(
                    getBoolean(
                            questJson,
                            "completed",
                            false
                    )
            );

            quests.add(quest);
        }

        faction.setActiveQuests(quests);
    }

    private void restoreProgression(
            Faction faction,
            JsonObject root
    ) {
        if (!root.has("progression")
                || !root.get("progression")
                        .isJsonObject()) {
            return;
        }

        JsonObject progression =
                root.getAsJsonObject("progression");

        FactionProgressState state =
                faction.getProgressionState();

        state.setSchemaVersion(
                getInt(
                        progression,
                        "schemaVersion",
                        state.getSchemaVersion()
                )
        );

        state.restoreLevelStarted(
                getInt(
                        progression,
                        "levelStarted",
                        faction.getLevel()
                )
        );

        state.restoreTier(
                hasValue(progression, "lockedTierId")
                        ? progression.get("lockedTierId")
                                .getAsString()
                        : null,
                getInt(
                        progression,
                        "lockedTierRank",
                        -1
                )
        );

        if (hasValue(
                progression,
                "pendingTransition"
        )) {
            state.setPendingTransition(
                    progression.get(
                            "pendingTransition"
                    ).getAsString()
            );
        } else {
            state.setPendingTransition(null);
        }

        state.restoreDiagnostics(
                getLong(
                        progression,
                        "lastProgressAt",
                        0L
                ),
                getLong(
                        progression,
                        "lastLevelUpAt",
                        0L
                ),
                getLong(
                        progression,
                        "transitionRevision",
                        0L
                )
        );

        Map<String, Long> questProgress =
                readLongMap(
                        progression,
                        "questProgress"
                );
        state.restoreProgress(questProgress);

        Map<String, Long> archivedProgress =
                readLongMap(
                        progression,
                        "archivedProgress"
                );
        state.restoreArchivedProgress(
                archivedProgress
        );

        List<String> pendingRewards =
                new ArrayList<>();

        if (progression.has("pendingRewards")
                && progression.get("pendingRewards")
                        .isJsonArray()) {
            for (JsonElement reward
                    : progression.getAsJsonArray(
                            "pendingRewards"
                    )) {
                if (reward != null
                        && !reward.isJsonNull()) {
                    pendingRewards.add(
                            reward.getAsString()
                    );
                }
            }
        }

        state.restorePendingRewards(
                pendingRewards
        );

        Map<String, FactionProgressState.PendingRewardRecord>
                pendingRewardRecords =
                        new LinkedHashMap<String, FactionProgressState.PendingRewardRecord>();

        if (progression.has("pendingRewardRecords")
                && progression.get("pendingRewardRecords")
                        .isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry
                    : progression.getAsJsonObject(
                            "pendingRewardRecords"
                    ).entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }

                JsonObject record =
                        entry.getValue()
                                .getAsJsonObject();

                FactionProgressState.PendingRewardStatus status =
                        FactionProgressState.PendingRewardStatus.PREPARED;

                if (hasValue(record, "status")) {
                    try {
                        status =
                                FactionProgressState.PendingRewardStatus
                                        .valueOf(
                                                record.get("status")
                                                        .getAsString()
                                        );
                    } catch (IllegalArgumentException ignored) {
                        status =
                                FactionProgressState.PendingRewardStatus.PREPARED;
                    }
                }

                pendingRewardRecords.put(
                        entry.getKey(),
                        new FactionProgressState.PendingRewardRecord(
                                status,
                                getInt(
                                        record,
                                        "attempts",
                                        0
                                ),
                                getLong(
                                        record,
                                        "lastAttemptAt",
                                        0L
                                ),
                                hasValue(record, "detail")
                                        ? record.get("detail")
                                                .getAsString()
                                        : null
                        )
                );
            }
        }

        state.restorePendingRewardRecords(
                pendingRewardRecords
        );

        Map<String, FactionProgressState.PvpKillRecord> ledger =
                new LinkedHashMap<>();

        if (progression.has("pvpKillLedger")
                && progression.get("pvpKillLedger")
                        .isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry
                    : progression.getAsJsonObject(
                            "pvpKillLedger"
                    ).entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }

                JsonObject record =
                        entry.getValue().getAsJsonObject();

                ledger.put(
                        entry.getKey(),
                        new FactionProgressState.PvpKillRecord(
                                getLong(
                                        record,
                                        "lastKillMillis",
                                        0L
                                ),
                                getLong(
                                        record,
                                        "epochDay",
                                        0L
                                ),
                                getInt(
                                        record,
                                        "dailyCount",
                                        0
                                )
                        )
                );
            }
        }

        state.restorePvpKillLedger(ledger);
    }

    // ============================================================
    // FPlayer
    // ============================================================

    private JsonObject serializeFPlayer(
            FPlayer fPlayer
    ) {
        JsonObject json = new JsonObject();

        json.addProperty(
                "storageSchemaVersion",
                StorageSnapshot.CURRENT_SCHEMA_VERSION
        );
        json.addProperty(
                "uuid",
                fPlayer.getUuid().toString()
        );

        if (fPlayer.getLastKnownName() != null) {
            json.addProperty(
                    "lastKnownName",
                    fPlayer.getLastKnownName()
            );
        }

        if (fPlayer.getFactionId() != null) {
            json.addProperty(
                    "factionId",
                    fPlayer.getFactionId()
            );
        }

        if (fPlayer.getRole() != null) {
            json.addProperty(
                    "role",
                    fPlayer.getRole().name()
            );
        }

        json.addProperty(
                "power",
                fPlayer.getPower()
        );
        json.addProperty(
                "maxPower",
                fPlayer.getMaxPower()
        );
        json.addProperty(
                "lastPowerUpdate",
                fPlayer.getLastPowerUpdate()
        );

        if (fPlayer.getChatMode() != null) {
            json.addProperty(
                    "chatMode",
                    fPlayer.getChatMode().name()
            );
        }

        /*
         * Map V2:
         * préférence utilisateur persistante.
         *
         * Les toggles plus dangereux comme auto-claim restent volontairement
         * runtime-only.
         */
        json.addProperty(
                "mapAutoUpdateEnabled",
                fPlayer.isMapAutoUpdateEnabled()
        );

        json.addProperty(
                "firstJoin",
                fPlayer.getFirstJoin()
        );
        json.addProperty(
                "lastSeen",
                fPlayer.getLastSeen()
        );
        json.addProperty(
                "kills",
                fPlayer.getKills()
        );
        json.addProperty(
                "deaths",
                fPlayer.getDeaths()
        );

        if (fPlayer.getPendingInvite() != null) {
            json.addProperty(
                    "pendingInvite",
                    fPlayer.getPendingInvite()
            );
            json.addProperty(
                    "inviteTimestamp",
                    fPlayer.getInviteTimestamp()
            );
        }

        return json;
    }

    private FPlayer deserializeFPlayer(
            JsonObject json
    ) {
        UUID uuid =
                parseUuid(
                        requireString(
                                json,
                                "uuid"
                        )
                );

        if (uuid == null) {
            return null;
        }

        FPlayer fPlayer = new FPlayer(uuid);

        if (hasValue(json, "lastKnownName")) {
            fPlayer.setLastKnownName(
                    json.get("lastKnownName")
                            .getAsString()
            );
        }

        if (hasValue(json, "factionId")) {
            fPlayer.setFactionId(
                    json.get("factionId")
                            .getAsString()
            );
        }

        if (hasValue(json, "role")) {
            fPlayer.setRole(
                    parseRole(
                            json.get("role")
                                    .getAsString(),
                            null
                    )
            );
        }

        /*
         * maxPower AVANT power :
         * l'ancien decoder faisait l'inverse et pouvait clamp le power à 10.
         */
        fPlayer.setMaxPower(
                getDouble(
                        json,
                        "maxPower",
                        fPlayer.getMaxPower()
                )
        );

        fPlayer.setPower(
                getDouble(
                        json,
                        "power",
                        fPlayer.getPower()
                )
        );

        fPlayer.setLastPowerUpdate(
                getLong(
                        json,
                        "lastPowerUpdate",
                        fPlayer.getLastPowerUpdate()
                )
        );

        if (hasValue(json, "chatMode")) {
            try {
                fPlayer.setChatMode(
                        FPlayer.ChatMode.valueOf(
                                json.get("chatMode")
                                        .getAsString()
                        )
                );
            } catch (IllegalArgumentException ignored) {
                // PUBLIC du constructeur conservé.
            }
        }

        fPlayer.setMapAutoUpdateEnabled(
                getBoolean(
                        json,
                        "mapAutoUpdateEnabled",
                        false
                )
        );

        fPlayer.setFirstJoin(
                getLong(
                        json,
                        "firstJoin",
                        fPlayer.getFirstJoin()
                )
        );
        fPlayer.setLastSeen(
                getLong(
                        json,
                        "lastSeen",
                        fPlayer.getLastSeen()
                )
        );
        fPlayer.setKills(
                getInt(
                        json,
                        "kills",
                        fPlayer.getKills()
                )
        );
        fPlayer.setDeaths(
                getInt(
                        json,
                        "deaths",
                        fPlayer.getDeaths()
                )
        );

        if (hasValue(json, "pendingInvite")) {
            fPlayer.setPendingInvite(
                    json.get("pendingInvite")
                            .getAsString()
            );

            if (json.has("inviteTimestamp")) {
                /*
                 * setPendingInvite remplit maintenant ; l'ancienne API ne
                 * fournit pas de setter timestamp. Ce champ est legacy et
                 * l'invitation canonique V2 vit sur Faction.
                 */
            }
        }

        return fPlayer;
    }

    // ============================================================
    // Location / helpers
    // ============================================================

    private JsonObject serializeStoredLocation(
            StoredLocation location
    ) {
        if (location == null) {
            throw new IllegalArgumentException(
                    "StoredLocation cannot be null"
            );
        }

        JsonObject json =
                new JsonObject();

        json.addProperty(
                "world",
                location.getWorldName()
        );

        json.addProperty(
                "x",
                location.getX()
        );

        json.addProperty(
                "y",
                location.getY()
        );

        json.addProperty(
                "z",
                location.getZ()
        );

        json.addProperty(
                "yaw",
                location.getYaw()
        );

        json.addProperty(
                "pitch",
                location.getPitch()
        );

        return json;
    }

    private StoredLocation deserializeStoredLocation(
            JsonObject json
    ) {
        if (json == null
                || !hasValue(
                        json,
                        "world"
                )) {
            return null;
        }

        try {
            return new StoredLocation(
                    json.get("world")
                            .getAsString(),
                    getDouble(
                            json,
                            "x",
                            0.0D
                    ),
                    getDouble(
                            json,
                            "y",
                            0.0D
                    ),
                    getDouble(
                            json,
                            "z",
                            0.0D
                    ),
                    (float) getDouble(
                            json,
                            "yaw",
                            0.0D
                    ),
                    (float) getDouble(
                            json,
                            "pitch",
                            0.0D
                    )
            );
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Map<String, Long> readLongMap(
            JsonObject root,
            String key
    ) {
        Map<String, Long> result =
                new LinkedHashMap<>();

        if (!root.has(key)
                || !root.get(key).isJsonObject()) {
            return result;
        }

        for (Map.Entry<String, JsonElement> entry
                : root.getAsJsonObject(key).entrySet()) {
            if (entry.getValue() != null
                    && !entry.getValue().isJsonNull()) {
                result.put(
                        entry.getKey(),
                        entry.getValue().getAsLong()
                );
            }
        }

        return result;
    }

    private static PermissionAction parseAction(
            JsonElement element
    ) {
        if (element == null || element.isJsonNull()) {
            return null;
        }

        try {
            return PermissionAction.valueOf(
                    element.getAsString()
            );
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static FactionRole parseRole(
            String raw,
            FactionRole fallback
    ) {
        if (raw == null) {
            return fallback;
        }

        FactionRole parsed =
                FactionRole.parse(raw);

        return parsed != null
                ? parsed
                : fallback;
    }

    private static UUID parseUuid(String raw) {
        if (raw == null) {
            return null;
        }

        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String requireString(
            JsonObject json,
            String key
    ) {
        if (!hasValue(json, key)) {
            throw new IllegalArgumentException(
                    "Missing JSON field: " + key
            );
        }

        return json.get(key).getAsString();
    }

    private static boolean hasValue(
            JsonObject json,
            String key
    ) {
        return json != null
                && json.has(key)
                && json.get(key) != null
                && !json.get(key).isJsonNull();
    }

    private static String getString(
            JsonObject json,
            String key,
            String fallback
    ) {
        return hasValue(json, key)
                ? json.get(key).getAsString()
                : fallback;
    }

    private static int getInt(
            JsonObject json,
            String key,
            int fallback
    ) {
        return hasValue(json, key)
                ? json.get(key).getAsInt()
                : fallback;
    }

    private static long getLong(
            JsonObject json,
            String key,
            long fallback
    ) {
        return hasValue(json, key)
                ? json.get(key).getAsLong()
                : fallback;
    }

    private static double getDouble(
            JsonObject json,
            String key,
            double fallback
    ) {
        return hasValue(json, key)
                ? json.get(key).getAsDouble()
                : fallback;
    }

    private static boolean getBoolean(
            JsonObject json,
            String key,
            boolean fallback
    ) {
        return hasValue(json, key)
                ? json.get(key).getAsBoolean()
                : fallback;
    }
}
