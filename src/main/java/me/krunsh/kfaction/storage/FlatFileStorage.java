package me.krunsh.kfaction.storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FLocation;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionRole;
import me.krunsh.kfaction.data.PermissionAction;
import me.krunsh.kfaction.data.Relation;

/**
 * Implémentation de stockage en fichiers JSON (FlatFile)
 * Chaque faction et joueur est stocké dans un fichier séparé
 */
public class FlatFileStorage implements Storage {
    
    private final Kfaction plugin;
    private final Gson gson;
    
    private File factionsFolder;
    private File playersFolder;
    
    private boolean connected;
    
    public FlatFileStorage(Kfaction plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();
        this.connected = false;
    }
    
    @Override
    public void initialize() {
        // Créer les dossiers
        factionsFolder = new File(plugin.getDataFolder(), "data/factions");
        playersFolder = new File(plugin.getDataFolder(), "data/players");
        
        if (!factionsFolder.exists()) {
            factionsFolder.mkdirs();
        }
        if (!playersFolder.exists()) {
            playersFolder.mkdirs();
        }
        
        connected = true;
        plugin.getLogger().info("FlatFileStorage initialisé dans " + plugin.getDataFolder().getPath());
    }
    
    @Override
    public void shutdown() {
        connected = false;
    }
    
    // === Factions ===
    
    @Override
    public void loadFactions(Consumer<Faction> consumer) {
        File[] files = factionsFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return;
        
        int count = 0;
        for (File file : files) {
            Faction faction = loadFactionFromFile(file);
            if (faction != null) {
                consumer.accept(faction);
                count++;
            }
        }
        plugin.getLogger().info("Chargé " + count + " factions");
    }
    
    @Override
    public Faction loadFaction(String factionId) {
        File file = new File(factionsFolder, factionId + ".json");
        return loadFactionFromFile(file);
    }
    
    private Faction loadFactionFromFile(File file) {
        if (!file.exists()) return null;
        
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            // Utiliser l'ancienne API pour compatibilité avec Gson 2.2.4 (Minecraft 1.8.8)
            @SuppressWarnings("deprecation")
            JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
            return deserializeFaction(json);
        } catch (Exception e) {
            plugin.getLogger().severe("Erreur de chargement faction " + file.getName() + ": " + e.getMessage());
            return null;
        }
    }
    
    @Override
    public void saveFaction(Faction faction) {
        saveFactionChecked(faction);
    }

    @Override
    public synchronized boolean saveFactionChecked(Faction faction) {
        if (faction == null || faction.isSystemFaction()) return false;
        
        File file = new File(factionsFolder, faction.getId() + ".json");
        File temporary = new File(factionsFolder, faction.getId() + ".json.tmp");
        JsonObject json = serializeFaction(faction);
        
        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(temporary), StandardCharsets.UTF_8)) {
            gson.toJson(json, writer);
        } catch (Exception e) {
            plugin.getLogger().severe("Erreur de sauvegarde faction " + faction.getId() + ": " + e.getMessage());
            temporary.delete();
            return false;
        }
        try {
            try {
                Files.move(temporary.toPath(), file.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary.toPath(), file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Erreur de remplacement atomique faction "
                    + faction.getId() + ": " + e.getMessage());
            temporary.delete();
            return false;
        }
        return true;
    }
    
    @Override
    public void deleteFaction(String factionId) {
        File file = new File(factionsFolder, factionId + ".json");
        if (file.exists()) {
            file.delete();
        }
    }
    
    // === Sérialisation Faction ===
    
    private JsonObject serializeFaction(Faction faction) {
        JsonObject json = new JsonObject();
        
        // Identifiants
        json.addProperty("id", faction.getId());
        json.addProperty("name", faction.getName());
        json.addProperty("tag", faction.getTag());
        json.addProperty("description", faction.getDescription());
        
        // Leader
        if (faction.getLeader() != null) {
            json.addProperty("leader", faction.getLeader().toString());
        }
        
        // Membres et rôles
        JsonArray membersArray = new JsonArray();
        for (UUID member : faction.getMembers()) {
            JsonObject memberObj = new JsonObject();
            memberObj.addProperty("uuid", member.toString());
            FactionRole role = faction.getRole(member);
            if (role != null) {
                memberObj.addProperty("role", role.name());
            }
            membersArray.add(memberObj);
        }
        json.add("members", membersArray);
        
        // Claims
        JsonArray claimsArray = new JsonArray();
        for (FLocation claim : faction.getClaims()) {
            claimsArray.add(claim.getKey());
        }
        json.add("claims", claimsArray);
        
        // Home
        if (faction.hasHome()) {
            json.add("home", serializeLocation(faction.getHome()));
        }
        
        // Warps
        if (faction.getWarpCount() > 0) {
            JsonObject warpsObj = new JsonObject();
            for (Map.Entry<String, Location> entry : faction.getWarps().entrySet()) {
                warpsObj.add(entry.getKey(), serializeLocation(entry.getValue()));
            }
            json.add("warps", warpsObj);
        }
        
        // Power
        json.addProperty("powerBoost", faction.getPowerBoost());
        
        // Relations
        JsonObject relationsObj = new JsonObject();
        for (Map.Entry<String, Relation> entry : faction.getAllRelations().entrySet()) {
            relationsObj.addProperty(entry.getKey(), entry.getValue().name());
        }
        json.add("relations", relationsObj);
        
        // Économie
        json.addProperty("bank", faction.getBank());
        json.addProperty("tntBank", faction.getTntBank());
        json.addProperty("tntEnabled", faction.isTntEnabled());
        
        // Métadonnées
        json.addProperty("createdAt", faction.getCreatedAt());
        json.addProperty("lastActivity", faction.getLastActivity());
        json.addProperty("permanent", faction.isPermanent());
        json.addProperty("open", faction.isOpen());
        
        // Permissions (simplifiées)
        JsonObject permsObj = new JsonObject();
        for (Map.Entry<FactionRole, Set<PermissionAction>> entry : faction.getAllPermissions().entrySet()) {
            JsonArray actions = new JsonArray();
            for (PermissionAction action : entry.getValue()) {
                actions.add(action.name());
            }
            permsObj.add(entry.getKey().name(), actions);
        }
        json.add("permissions", permsObj);
        
        // Permissions de relation
        JsonObject relPermsObj = new JsonObject();
        for (Map.Entry<Relation, Set<PermissionAction>> entry : faction.getRelationPermissions().entrySet()) {
            JsonArray actions = new JsonArray();
            for (PermissionAction action : entry.getValue()) {
                actions.add(action.name());
            }
            relPermsObj.add(entry.getKey().name(), actions);
        }
        json.add("relationPermissions", relPermsObj);
        
        // === Système de Niveaux ===
        json.addProperty("level", faction.getLevel());
        json.addProperty("currentXp", faction.getCurrentXp());
        
        if (faction.getActiveCategory() != null) {
            json.addProperty("activeCategory", faction.getActiveCategory().getConfigKey());
        }
        
        // Quêtes actives
        JsonArray questsArray = new JsonArray();
        for (me.krunsh.kfaction.data.FactionQuest quest : faction.getActiveQuests()) {
            JsonObject questObj = new JsonObject();
            questObj.addProperty("id", quest.getId());
            questObj.addProperty("type", quest.getType().getConfigKey());
            questObj.addProperty("category", quest.getCategory().getConfigKey());
            questObj.addProperty("target", quest.getTarget());
            if (quest.getSparrowItemId() != null) {
                questObj.addProperty("sparrowmcItem", quest.getSparrowItemId());
            }
            questObj.addProperty("displayName", quest.getDisplayName());
            questObj.addProperty("progress", quest.getProgress());
            questObj.addProperty("required", quest.getRequired());
            questObj.addProperty("xpReward", quest.getXpReward());
            questObj.addProperty("completed", quest.isCompleted());
            questsArray.add(questObj);
        }
        json.add("activeQuests", questsArray);

        // Progression fixe v2. L'ancien format reste présent pour permettre
        // un rollback et une migration sans perte silencieuse.
        me.krunsh.kfaction.progression.FactionProgressState state =
                faction.getProgressionState();
        JsonObject progression = new JsonObject();
        progression.addProperty("schemaVersion", state.getSchemaVersion());
        progression.addProperty("levelStarted", state.getLevelStarted());
        if (state.getLockedTierId() != null) {
            progression.addProperty("lockedTierId", state.getLockedTierId());
            progression.addProperty("lockedTierRank", state.getLockedTierRank());
        }
        if (state.getPendingTransition() != null) {
            progression.addProperty("pendingTransition", state.getPendingTransition());
        }
        JsonObject rawProgress = new JsonObject();
        for (Map.Entry<String, Long> entry : state.snapshotProgress().entrySet()) {
            rawProgress.addProperty(entry.getKey(), entry.getValue());
        }
        progression.add("questProgress", rawProgress);
        JsonObject archivedProgress = new JsonObject();
        for (Map.Entry<String, Long> entry
                : state.snapshotArchivedProgress().entrySet()) {
            archivedProgress.addProperty(entry.getKey(), entry.getValue());
        }
        progression.add("archivedProgress", archivedProgress);
        JsonArray pendingRewards = new JsonArray();
        for (String reward : state.getPendingRewards()) {
            pendingRewards.add(reward);
        }
        progression.add("pendingRewards", pendingRewards);
        JsonObject pvpLedger = new JsonObject();
        for (Map.Entry<String,
                me.krunsh.kfaction.progression.FactionProgressState.PvpKillRecord>
                entry : state.snapshotPvpKillLedger().entrySet()) {
            JsonObject record = new JsonObject();
            record.addProperty("lastKillMillis",
                    entry.getValue().getLastKillMillis());
            record.addProperty("epochDay", entry.getValue().getEpochDay());
            record.addProperty("dailyCount", entry.getValue().getDailyCount());
            pvpLedger.add(entry.getKey(), record);
        }
        progression.add("pvpKillLedger", pvpLedger);
        json.add("progression", progression);
        
        // Récompenses appliquées
        JsonArray rewardsArray = new JsonArray();
        for (String reward : faction.getAppliedRewards()) {
            rewardsArray.add(reward);
        }
        json.add("appliedRewards", rewardsArray);
        
        // Récompenses de niveau
        json.addProperty("chestSize", faction.getChestSize());
        if (faction.getChestContentsB64() != null) {
            json.addProperty("chestContentsB64", faction.getChestContentsB64());
        }
        json.addProperty("factionFlyEnabled", faction.isFactionFlyEnabled());
        json.addProperty("antiSethomeEnabled", faction.isAntiSethomeEnabled());
        json.addProperty("extraWarps", faction.getExtraWarps());
        json.addProperty("extraMembers", faction.getExtraMembers());
        json.addProperty("extraPowerBoost", faction.getExtraPowerBoost());
        
        return json;
    }
    
    private Faction deserializeFaction(JsonObject json) {
        String id = json.get("id").getAsString();
        String name = json.get("name").getAsString();
        
        UUID leader = null;
        if (json.has("leader") && !json.get("leader").isJsonNull()) {
            leader = UUID.fromString(json.get("leader").getAsString());
        }
        
        Faction faction = new Faction(id, name, leader);
        
        // Tag
        if (json.has("tag")) {
            faction.setTag(json.get("tag").getAsString());
        }
        
        // Description
        if (json.has("description")) {
            faction.setDescription(json.get("description").getAsString());
        }
        
        // Membres
        if (json.has("members")) {
            JsonArray membersArray = json.getAsJsonArray("members");
            for (JsonElement elem : membersArray) {
                JsonObject memberObj = elem.getAsJsonObject();
                UUID uuid = UUID.fromString(memberObj.get("uuid").getAsString());
                FactionRole role = FactionRole.RECRUIT;
                if (memberObj.has("role")) {
                    try {
                        role = FactionRole.valueOf(memberObj.get("role").getAsString());
                    } catch (IllegalArgumentException ignored) {}
                }
                if (!uuid.equals(leader)) { // Leader déjà ajouté
                    faction.addMember(uuid, role);
                }
            }
        }
        
        // Claims
        if (json.has("claims")) {
            JsonArray claimsArray = json.getAsJsonArray("claims");
            for (JsonElement elem : claimsArray) {
                FLocation loc = FLocation.fromKey(elem.getAsString());
                if (loc != null) {
                    faction.addClaim(loc);
                }
            }
        }
        
        // Home
        if (json.has("home") && !json.get("home").isJsonNull()) {
            Location home = deserializeLocation(json.getAsJsonObject("home"));
            faction.setHome(home);
        }
        
        // Warps
        if (json.has("warps") && !json.get("warps").isJsonNull()) {
            JsonObject warpsObj = json.getAsJsonObject("warps");
            for (Map.Entry<String, JsonElement> entry : warpsObj.entrySet()) {
                Location warpLoc = deserializeLocation(entry.getValue().getAsJsonObject());
                if (warpLoc != null) {
                    faction.setWarp(entry.getKey(), warpLoc);
                }
            }
        }
        
        // Power
        if (json.has("powerBoost")) {
            faction.setPowerBoost(json.get("powerBoost").getAsDouble());
        }
        
        // Relations
        if (json.has("relations")) {
            JsonObject relationsObj = json.getAsJsonObject("relations");
            for (Map.Entry<String, JsonElement> entry : relationsObj.entrySet()) {
                Relation relation = Relation.fromString(entry.getValue().getAsString());
                faction.setRelation(entry.getKey(), relation);
            }
        }
        
        // Économie
        if (json.has("bank")) {
            faction.setBank(json.get("bank").getAsDouble());
        }
        if (json.has("tntBank")) {
            faction.setTntBank(json.get("tntBank").getAsInt());
        }
        if (json.has("tntEnabled")) {
            faction.setTntEnabled(json.get("tntEnabled").getAsBoolean());
        }
        
        // Métadonnées
        if (json.has("permanent")) {
            faction.setPermanent(json.get("permanent").getAsBoolean());
        }
        if (json.has("open")) {
            faction.setOpen(json.get("open").getAsBoolean());
        }
        
        // Permissions de relation
        if (json.has("relationPermissions")) {
            JsonObject relPermsObj = json.getAsJsonObject("relationPermissions");
            for (Map.Entry<String, JsonElement> entry : relPermsObj.entrySet()) {
                try {
                    Relation relation = Relation.valueOf(entry.getKey());
                    JsonArray actions = entry.getValue().getAsJsonArray();
                    for (JsonElement actionElem : actions) {
                        PermissionAction action = PermissionAction.valueOf(actionElem.getAsString());
                        faction.setPermission(relation, action, true);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }
        
        // === Système de Niveaux ===
        if (json.has("level")) {
            faction.setLevel(json.get("level").getAsInt());
        }
        if (json.has("currentXp")) {
            faction.setCurrentXp(json.get("currentXp").getAsInt());
        }
        if (json.has("activeCategory") && !json.get("activeCategory").isJsonNull()) {
            faction.setActiveCategory(
                me.krunsh.kfaction.data.QuestCategory.fromConfigKey(json.get("activeCategory").getAsString())
            );
        }
        
        // Quêtes actives
        if (json.has("activeQuests")) {
            java.util.List<me.krunsh.kfaction.data.FactionQuest> quests = new java.util.ArrayList<>();
            JsonArray questsArr = json.getAsJsonArray("activeQuests");
            for (JsonElement elem : questsArr) {
                JsonObject qObj = elem.getAsJsonObject();
                me.krunsh.kfaction.data.QuestType qType = me.krunsh.kfaction.data.QuestType.fromConfigKey(
                    qObj.get("type").getAsString());
                me.krunsh.kfaction.data.QuestCategory qCat = me.krunsh.kfaction.data.QuestCategory.fromConfigKey(
                    qObj.get("category").getAsString());
                if (qType != null && qCat != null) {
                    me.krunsh.kfaction.data.FactionQuest quest = new me.krunsh.kfaction.data.FactionQuest(
                        qObj.get("id").getAsString(),
                        qType, qCat,
                        qObj.get("target").getAsString(),
                        qObj.has("sparrowmcItem") ? qObj.get("sparrowmcItem").getAsString() : null,
                        qObj.get("displayName").getAsString(),
                        qObj.get("required").getAsInt(),
                        qObj.get("xpReward").getAsInt()
                    );
                    quest.setProgress(qObj.get("progress").getAsInt());
                    if (qObj.has("completed")) {
                        quest.setCompleted(qObj.get("completed").getAsBoolean());
                    }
                    quests.add(quest);
                }
            }
            faction.setActiveQuests(quests);
        }

        if (json.has("progression") && json.get("progression").isJsonObject()) {
            JsonObject progression = json.getAsJsonObject("progression");
            me.krunsh.kfaction.progression.FactionProgressState state =
                    faction.getProgressionState();
            if (progression.has("schemaVersion")) {
                state.setSchemaVersion(progression.get("schemaVersion").getAsInt());
            }
            state.restoreLevelStarted(progression.has("levelStarted")
                    ? progression.get("levelStarted").getAsInt() : faction.getLevel());
            state.restoreTier(
                    progression.has("lockedTierId")
                            ? progression.get("lockedTierId").getAsString() : null,
                    progression.has("lockedTierRank")
                            ? progression.get("lockedTierRank").getAsInt() : -1);
            if (progression.has("pendingTransition")) {
                state.setPendingTransition(
                        progression.get("pendingTransition").getAsString());
            }
            Map<String, Long> raw = new java.util.LinkedHashMap<String, Long>();
            if (progression.has("questProgress")
                    && progression.get("questProgress").isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry
                        : progression.getAsJsonObject("questProgress").entrySet()) {
                    raw.put(entry.getKey(), entry.getValue().getAsLong());
                }
            }
            state.restoreProgress(raw);
            Map<String, Long> archived =
                    new java.util.LinkedHashMap<String, Long>();
            if (progression.has("archivedProgress")
                    && progression.get("archivedProgress").isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry
                        : progression.getAsJsonObject("archivedProgress").entrySet()) {
                    archived.put(entry.getKey(), entry.getValue().getAsLong());
                }
            }
            state.restoreArchivedProgress(archived);
            if (progression.has("pendingRewards")
                    && progression.get("pendingRewards").isJsonArray()) {
                java.util.List<String> pending = new java.util.ArrayList<String>();
                for (JsonElement reward
                        : progression.getAsJsonArray("pendingRewards")) {
                    pending.add(reward.getAsString());
                }
                state.restorePendingRewards(pending);
            }
            if (progression.has("pvpKillLedger")
                    && progression.get("pvpKillLedger").isJsonObject()) {
                java.util.Map<String,
                        me.krunsh.kfaction.progression.FactionProgressState.PvpKillRecord>
                        ledger = new java.util.LinkedHashMap<String,
                        me.krunsh.kfaction.progression.FactionProgressState.PvpKillRecord>();
                for (Map.Entry<String, JsonElement> entry
                        : progression.getAsJsonObject("pvpKillLedger").entrySet()) {
                    if (!entry.getValue().isJsonObject()) continue;
                    JsonObject record = entry.getValue().getAsJsonObject();
                    if (!record.has("lastKillMillis")
                            || !record.has("epochDay")
                            || !record.has("dailyCount")) continue;
                    ledger.put(entry.getKey(),
                            new me.krunsh.kfaction.progression.FactionProgressState.PvpKillRecord(
                                    record.get("lastKillMillis").getAsLong(),
                                    record.get("epochDay").getAsLong(),
                                    record.get("dailyCount").getAsInt()));
                }
                state.restorePvpKillLedger(ledger);
            }
        }
        
        // Récompenses appliquées
        if (json.has("appliedRewards")) {
            JsonArray rewardsArr = json.getAsJsonArray("appliedRewards");
            for (JsonElement elem : rewardsArr) {
                faction.addAppliedReward(elem.getAsString());
            }
        }
        
        // Récompenses de niveau
        if (json.has("chestSize")) {
            faction.setChestSize(json.get("chestSize").getAsInt());
        }
        if (json.has("chestContentsB64") && !json.get("chestContentsB64").isJsonNull()) {
            faction.setChestContentsB64(json.get("chestContentsB64").getAsString());
        }
        if (json.has("factionFlyEnabled")) {
            faction.setFactionFlyEnabled(json.get("factionFlyEnabled").getAsBoolean());
        }
        if (json.has("antiSethomeEnabled")) {
            faction.setAntiSethomeEnabled(json.get("antiSethomeEnabled").getAsBoolean());
        }
        if (json.has("extraWarps")) {
            faction.setExtraWarps(json.get("extraWarps").getAsInt());
        }
        if (json.has("extraMembers")) {
            faction.setExtraMembers(json.get("extraMembers").getAsInt());
        }
        if (json.has("extraPowerBoost")) {
            faction.setExtraPowerBoost(json.get("extraPowerBoost").getAsDouble());
        }
        
        return faction;
    }
    
    // === FPlayers ===
    
    @Override
    public void loadFPlayers(Consumer<FPlayer> consumer) {
        File[] files = playersFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return;
        
        int count = 0;
        for (File file : files) {
            FPlayer fPlayer = loadFPlayerFromFile(file);
            if (fPlayer != null) {
                consumer.accept(fPlayer);
                count++;
            }
        }
        plugin.getLogger().info("Chargé " + count + " joueurs");
    }
    
    @Override
    public FPlayer loadFPlayer(String uuid) {
        File file = new File(playersFolder, uuid + ".json");
        return loadFPlayerFromFile(file);
    }
    
    private FPlayer loadFPlayerFromFile(File file) {
        if (!file.exists()) return null;
        
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            // Utiliser l'ancienne API pour compatibilité avec Gson 2.2.4 (Minecraft 1.8.8)
            @SuppressWarnings("deprecation")
            JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
            return deserializeFPlayer(json);
        } catch (Exception e) {
            plugin.getLogger().severe("Erreur de chargement joueur " + file.getName() + ": " + e.getMessage());
            return null;
        }
    }
    
    @Override
    public void saveFPlayer(FPlayer fPlayer) {
        if (fPlayer == null) return;
        
        File file = new File(playersFolder, fPlayer.getUuid().toString() + ".json");
        JsonObject json = serializeFPlayer(fPlayer);
        
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            gson.toJson(json, writer);
        } catch (Exception e) {
            plugin.getLogger().severe("Erreur de sauvegarde joueur " + fPlayer.getUuid() + ": " + e.getMessage());
        }
    }
    
    @Override
    public void deleteFPlayer(String uuid) {
        File file = new File(playersFolder, uuid + ".json");
        if (file.exists()) {
            file.delete();
        }
    }
    
    // === Sérialisation FPlayer ===
    
    private JsonObject serializeFPlayer(FPlayer fPlayer) {
        JsonObject json = new JsonObject();
        
        json.addProperty("uuid", fPlayer.getUuid().toString());
        json.addProperty("lastKnownName", fPlayer.getLastKnownName());
        
        if (fPlayer.getFactionId() != null) {
            json.addProperty("factionId", fPlayer.getFactionId());
        }
        if (fPlayer.getRole() != null) {
            json.addProperty("role", fPlayer.getRole().name());
        }
        
        json.addProperty("power", fPlayer.getPower());
        json.addProperty("maxPower", fPlayer.getMaxPower());
        json.addProperty("lastPowerUpdate", fPlayer.getLastPowerUpdate());
        
        json.addProperty("chatMode", fPlayer.getChatMode().name());
        
        json.addProperty("firstJoin", fPlayer.getFirstJoin());
        json.addProperty("lastSeen", fPlayer.getLastSeen());
        json.addProperty("kills", fPlayer.getKills());
        json.addProperty("deaths", fPlayer.getDeaths());
        
        return json;
    }
    
    private FPlayer deserializeFPlayer(JsonObject json) {
        UUID uuid = UUID.fromString(json.get("uuid").getAsString());
        FPlayer fPlayer = new FPlayer(uuid);
        
        if (json.has("lastKnownName")) {
            fPlayer.setLastKnownName(json.get("lastKnownName").getAsString());
        }
        
        if (json.has("factionId") && !json.get("factionId").isJsonNull()) {
            fPlayer.setFactionId(json.get("factionId").getAsString());
        }
        if (json.has("role") && !json.get("role").isJsonNull()) {
            try {
                fPlayer.setRole(FactionRole.valueOf(json.get("role").getAsString()));
            } catch (IllegalArgumentException ignored) {}
        }
        
        if (json.has("power")) {
            fPlayer.setPower(json.get("power").getAsDouble());
        }
        if (json.has("maxPower")) {
            fPlayer.setMaxPower(json.get("maxPower").getAsDouble());
        }
        if (json.has("lastPowerUpdate")) {
            fPlayer.setLastPowerUpdate(json.get("lastPowerUpdate").getAsLong());
        }
        
        if (json.has("chatMode")) {
            try {
                fPlayer.setChatMode(FPlayer.ChatMode.valueOf(json.get("chatMode").getAsString()));
            } catch (IllegalArgumentException ignored) {}
        }
        
        if (json.has("firstJoin")) {
            fPlayer.setFirstJoin(json.get("firstJoin").getAsLong());
        }
        if (json.has("lastSeen")) {
            fPlayer.setLastSeen(json.get("lastSeen").getAsLong());
        }
        if (json.has("kills")) {
            fPlayer.setKills(json.get("kills").getAsInt());
        }
        if (json.has("deaths")) {
            fPlayer.setDeaths(json.get("deaths").getAsInt());
        }
        
        return fPlayer;
    }
    
    // === Helpers Location ===
    
    private JsonObject serializeLocation(Location loc) {
        JsonObject json = new JsonObject();
        json.addProperty("world", loc.getWorld().getName());
        json.addProperty("x", loc.getX());
        json.addProperty("y", loc.getY());
        json.addProperty("z", loc.getZ());
        json.addProperty("yaw", loc.getYaw());
        json.addProperty("pitch", loc.getPitch());
        return json;
    }
    
    private Location deserializeLocation(JsonObject json) {
        String worldName = json.get("world").getAsString();
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        
        double x = json.get("x").getAsDouble();
        double y = json.get("y").getAsDouble();
        double z = json.get("z").getAsDouble();
        float yaw = json.has("yaw") ? json.get("yaw").getAsFloat() : 0;
        float pitch = json.has("pitch") ? json.get("pitch").getAsFloat() : 0;
        
        return new Location(world, x, y, z, yaw, pitch);
    }
    
    @Override
    public String getType() {
        return "flatfile";
    }
    
    @Override
    public boolean isConnected() {
        return connected;
    }
}
