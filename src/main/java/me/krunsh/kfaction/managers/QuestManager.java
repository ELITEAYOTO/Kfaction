package me.krunsh.kfaction.managers;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.QuestCategory;
import me.krunsh.kfaction.data.Relation;
import me.krunsh.kfaction.progression.FactionProgressState;
import me.krunsh.kfaction.progression.LevelDefinition;
import me.krunsh.kfaction.progression.MemberTierDefinition;
import me.krunsh.kfaction.progression.ProgressionConfig;
import me.krunsh.kfaction.progression.ProgressionConfigLoader;
import me.krunsh.kfaction.progression.ProgressionEngine;
import me.krunsh.kfaction.progression.ProgressionMigrationService;
import me.krunsh.kfaction.progression.ProgressionPolicy;
import me.krunsh.kfaction.progression.ProgressionUpdate;
import me.krunsh.kfaction.progression.QuestAction;
import me.krunsh.kfaction.progression.QuestProgressView;
import me.krunsh.kfaction.progression.QuestTypeRegistry;
import me.krunsh.kfaction.progression.RewardDefinition;
import me.krunsh.kfaction.progression.ValidationEnvironment;
import me.krunsh.kfaction.progression.ValidationIssue;

/**
 * Moteur de progression faction v2.
 *
 * Une seule source de vérité est active: progression.yml. Les anciens fichiers
 * quests.yml/levels.yml et leurs champs JSON ne servent plus qu'au rollback et
 * à la migration conservatrice.
 */
public final class QuestManager {
    private final Kfaction plugin;
    private final QuestTypeRegistry typeRegistry = QuestTypeRegistry.builtIns();
    private final Map<String, Object> factionLocks =
            new ConcurrentHashMap<String, Object>();
    private final Set<String> warnedPending =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final Set<String> warnedLegacyCommands =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final ProgressionMigrationService migrationService;

    private volatile ProgressionConfig activeConfig;
    private volatile List<ValidationIssue> lastValidationIssues =
            Collections.emptyList();

    public QuestManager(Kfaction plugin) {
        this.plugin = plugin;
        this.migrationService = new ProgressionMigrationService(plugin);
    }

    public void initialize() {
        loadConfig();
    }

    /**
     * Charge un candidat complet avant de remplacer le snapshot actif.
     * Un reload invalide conserve toujours le dernier snapshot valide.
     */
    public void loadConfig() {
        plugin.saveResource("progression.example.yml", false);
        File file = new File(plugin.getDataFolder(), "progression.yml");
        if (!file.isFile()) {
            if (activeConfig == null) {
                plugin.getLogger().warning("progression.yml absent: progression "
                        + "faction v2 désactivée. progression.example.yml a été copié "
                        + "sans être activé automatiquement.");
            } else {
                plugin.getLogger().severe("progression.yml absent au reload: "
                        + "snapshot actif précédent conservé.");
            }
            return;
        }

        int maxMembers = plugin.getConfigManager()
                .getInt("factions.members.max-per-faction", 50);
        ProgressionConfigLoader loader = new ProgressionConfigLoader(typeRegistry,
                runtimeValidationEnvironment(), maxMembers);
        ProgressionConfigLoader.LoadResult result = loader.load(file);
        List<ValidationIssue> issues =
                new ArrayList<ValidationIssue>(result.getIssues());
        if (result.isValid()) {
            validateLiveCompatibility(result.getConfig(), issues);
        }
        lastValidationIssues = Collections.unmodifiableList(issues);
        if (!result.isValid() || hasErrors(issues)) {
            logValidationIssues(issues);
            plugin.getLogger().severe("progression.yml refusé; "
                    + (activeConfig == null ? "progression désactivée."
                            : "snapshot actif précédent conservé."));
            return;
        }

        activeConfig = result.getConfig();
        logValidationIssues(issues);
        plugin.getLogger().info("Progression v2 chargée: "
                + activeConfig.getLevels().size() + " niveaux, "
                + activeConfig.getTiers().size() + " tranches, "
                + countQuests(activeConfig) + " définitions de quêtes fixes.");
        migrateLoadedFactions();
    }

    public void migrateLoadedFactions() {
        ProgressionConfig config = activeConfig;
        if (config == null || plugin.getFactionManager() == null) return;
        for (Faction faction : plugin.getFactionManager().getAllFactions()) {
            ensureState(faction, config);
        }
    }

    public boolean isEnabled() {
        ProgressionConfig config = activeConfig;
        return config != null && config.isEnabled();
    }

    public ProgressionConfig getActiveConfig() {
        return activeConfig;
    }

    public List<ValidationIssue> getLastValidationIssues() {
        return lastValidationIssues;
    }

    /** Valide le fichier disque sans remplacer le snapshot actif. */
    public List<ValidationIssue> validateCandidate() {
        File file = new File(plugin.getDataFolder(), "progression.yml");
        int maxMembers = plugin.getConfigManager()
                .getInt("factions.members.max-per-faction", 50);
        ProgressionConfigLoader.LoadResult result = new ProgressionConfigLoader(
                typeRegistry, runtimeValidationEnvironment(), maxMembers).load(file);
        List<ValidationIssue> issues =
                new ArrayList<ValidationIssue>(result.getIssues());
        if (result.isValid()) validateLiveCompatibility(result.getConfig(), issues);
        return Collections.unmodifiableList(issues);
    }

    public List<QuestProgressView> getQuestViews(Faction faction) {
        ProgressionConfig config = activeConfig;
        if (!ensureState(faction, config)) return Collections.emptyList();
        Object lock = lockFor(faction);
        synchronized (lock) {
            int previousRank = faction.getProgressionState().getLockedTierRank();
            List<QuestProgressView> views = ProgressionPolicy.views(config,
                    faction.getProgressionState(), faction.getLevel(),
                    memberCount(faction));
            if (previousRank != faction.getProgressionState().getLockedTierRank()) {
                plugin.getStorageManager().markDirty(faction);
            }
            return views;
        }
    }

    public MemberTierDefinition getCurrentTier(Faction faction) {
        ProgressionConfig config = activeConfig;
        if (!ensureState(faction, config)) return null;
        synchronized (lockFor(faction)) {
            return ProgressionPolicy.refreshLockedTier(config,
                    faction.getProgressionState(), memberCount(faction));
        }
    }

    public ProgressionUpdate applyAction(Faction faction, QuestAction action) {
        return applyActions(faction, Collections.singletonList(action));
    }

    /**
     * Applique toutes les facettes d'un même événement avant d'évaluer le
     * level-up. Un BlockBreak MINE+BREAK ne peut donc pas toucher deux niveaux.
     */
    public ProgressionUpdate applyActions(Faction faction,
            List<QuestAction> actions) {
        ProgressionConfig config = activeConfig;
        if (!ensureState(faction, config)) return ProgressionUpdate.NONE;
        synchronized (lockFor(faction)) {
            List<String> progressed = new ArrayList<String>();
            List<String> completed = new ArrayList<String>();
            boolean levelComplete = false;
            for (QuestAction action : actions) {
                ProgressionUpdate part = ProgressionEngine.apply(config,
                        faction.getProgressionState(), faction.getLevel(),
                        memberCount(faction), action);
                progressed.addAll(part.getProgressedQuestIds());
                completed.addAll(part.getNewlyCompletedQuestIds());
                levelComplete |= part.isLevelComplete();
            }
            ProgressionUpdate update = progressed.isEmpty()
                    ? ProgressionUpdate.NONE
                    : new ProgressionUpdate(progressed, completed, levelComplete);
            if (!update.hasProgress()) return update;

            plugin.getStorageManager().markDirty(faction);
            for (String id : update.getNewlyCompletedQuestIds()) {
                faction.broadcast("§a✓ §7Quête obligatoire terminée: §e" + id);
            }
            if (update.isLevelComplete()) transitionToNextLevel(faction, config);
            return update;
        }
    }

    public int progressEntityAction(Faction faction, String type,
            EntityType entityType, long amount, String world,
            Set<String> regions) {
        return applyAction(faction, QuestAction.entity(type, entityType, amount,
                world, regions)).getProgressedQuestIds().size();
    }

    public int progressPlayerKill(Player killer, Player victim, String world,
            Set<String> regions, long timestampMillis) {
        if (killer == null || victim == null
                || killer.getUniqueId().equals(victim.getUniqueId())) return 0;
        Faction killerFaction = plugin.getFactionManager().getPlayerFaction(killer);
        if (killerFaction == null) return 0;
        Faction victimFaction = plugin.getFactionManager().getPlayerFaction(victim);
        boolean sameFaction = victimFaction != null
                && victimFaction.getId().equals(killerFaction.getId());
        Relation relation = victimFaction == null ? Relation.NEUTRAL
                : killerFaction.getRelationTo(victimFaction.getId());
        boolean allied = relation == Relation.ALLY || relation == Relation.TRUCE;
        boolean npc = victim.hasMetadata("NPC")
                || victim.hasMetadata("CitizensNPC");
        boolean sameIp = killer.getAddress() != null
                && victim.getAddress() != null
                && killer.getAddress().getAddress() != null
                && killer.getAddress().getAddress()
                        .equals(victim.getAddress().getAddress());
        QuestAction action = QuestAction.playerKill(1L, world, regions,
                victim.getUniqueId(), sameFaction, allied, npc, sameIp,
                timestampMillis);
        return applyAction(killerFaction, action)
                .getProgressedQuestIds().size();
    }

    public boolean shouldTrackPlayerPlaced(Material material, int data) {
        ProgressionConfig config = activeConfig;
        if (config == null || material == null) return false;
        for (LevelDefinition level : config.getLevels().values()) {
            for (me.krunsh.kfaction.progression.TierLevelDefinition tier
                    : level.getTiers().values()) {
                for (me.krunsh.kfaction.progression.QuestDefinition quest
                        : tier.getQuests().values()) {
                    if (!quest.getConditions().isCountPlayerPlacedBlocks()
                            && quest.getTarget().matchesMaterial(material, data, null)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public int progressBlockAction(Faction faction, String type, Material material,
            int data, String sparrowItemId, long amount, String world,
            Set<String> regions, boolean silkTouch, boolean mature,
            boolean playerPlaced) {
        ProgressionUpdate update = applyAction(faction,
                QuestAction.material(type, material, data, sparrowItemId, amount,
                        world, regions, silkTouch, mature, playerPlaced));
        return update.getProgressedQuestIds().size();
    }

    /** Compatibilité API: un BlockBreak historique est maintenant une action MINE. */
    public int progressBlockBreak(Faction faction, Material material, int amount) {
        return progressBlockAction(faction, "MINE", material, 0, null, amount,
                null, Collections.<String>emptySet(), false, true, false);
    }

    public int progressEntityKill(Faction faction, EntityType entityType,
            boolean witherSkeleton, int amount) {
        String type = entityType == EntityType.PLAYER ? "PLAYER_KILL" : "MOB_KILL";
        QuestAction action = entityType == EntityType.PLAYER
                ? QuestAction.playerKill(amount)
                : QuestAction.entity(type, entityType, amount);
        return applyAction(faction, action).getProgressedQuestIds().size();
    }

    public int progressItemSmelt(Faction faction, Material result, int amount) {
        return applyAction(faction, QuestAction.material("SMELT", result, 0,
                null, amount)).getProgressedQuestIds().size();
    }

    public int progressItemSell(Faction faction, Material material, int amount) {
        return progressItemSell(faction, material, null, amount);
    }

    public int progressItemSell(Faction faction, Material material,
            String sparrowItemId, int amount) {
        return applyAction(faction, QuestAction.material("SELL", material, 0,
                sparrowItemId, amount)).getProgressedQuestIds().size();
    }

    public void onItemSell(Player player, Material material, int amount) {
        onItemSell(player, material, null, amount);
    }

    public void onItemSell(Player player, Material material,
            String sparrowItemId, int amount) {
        if (player == null) return;
        Faction faction = plugin.getFactionManager().getPlayerFaction(player);
        if (faction != null) {
            progressItemSell(faction, material, sparrowItemId, amount);
        }
    }

    /**
     * Ancienne API neutralisée: les catégories sont désormais uniquement
     * visuelles et toutes les quêtes du niveau sont actives.
     */
    @Deprecated
    public void selectCategory(Faction faction, QuestCategory ignored) {
        warnLegacyCommand("selectCategory");
    }

    /** Ancien reroll neutralisé pour garantir qu'aucune progression ne soit perdue. */
    @Deprecated
    public void assignRandomQuests(Faction faction, QuestCategory ignored) {
        warnLegacyCommand("assignRandomQuests");
    }

    public int getActiveQuestsCount() {
        return 0;
    }

    private boolean ensureState(Faction faction, ProgressionConfig config) {
        if (faction == null || faction.isSystemFaction()
                || config == null || !config.isEnabled()) return false;
        synchronized (lockFor(faction)) {
            FactionProgressState state = faction.getProgressionState();
            if (state.getSchemaVersion()
                    < FactionProgressState.CURRENT_SCHEMA_VERSION) {
                ProgressionMigrationService.Result result =
                        migrationService.migrate(faction, config);
                if (result != ProgressionMigrationService.Result.MIGRATED
                        && result != ProgressionMigrationService.Result.SKIPPED) {
                    return false;
                }
            }
            if (state.getLevelStarted() != faction.getLevel()) {
                plugin.getLogger().severe("Progression bloquée pour "
                        + faction.getName() + ": niveau faction="
                        + faction.getLevel() + ", état progression="
                        + state.getLevelStarted() + ".");
                return false;
            }
            if (!state.getPendingRewards().isEmpty()
                    && warnedPending.add(faction.getId())) {
                plugin.getLogger().severe("Récompenses en état ambigu pour "
                        + faction.getName() + ": " + state.getPendingRewards()
                        + ". Aucun rejeu automatique afin d'éviter une duplication.");
            }
            return true;
        }
    }

    private void transitionToNextLevel(Faction faction, ProgressionConfig config) {
        int oldLevel = faction.getLevel();
        int nextLevel = oldLevel + 1;
        LevelDefinition next = config.getLevel(nextLevel);
        if (next == null) {
            faction.broadcast("§6✦ §eToutes les quêtes du niveau maximal sont terminées.");
            return;
        }

        FactionProgressState state = faction.getProgressionState();
        FactionProgressState.Snapshot before = state.snapshot();
        state.setPendingTransition(oldLevel + "->" + nextLevel + ":PREPARED");
        for (RewardDefinition reward : next.getRewardsOnEnter()) {
            String key = rewardKey(nextLevel, reward);
            if (!faction.hasAppliedReward(key)) state.addPendingReward(key);
        }
        faction.setLevel(nextLevel);
        ProgressionPolicy.beginNextLevel(config, state, nextLevel,
                memberCount(faction));

        if (!plugin.getStorageManager().saveFactionNow(faction)) {
            faction.setLevel(oldLevel);
            state.restore(before);
            plugin.getLogger().severe("Level-up annulé pour " + faction.getName()
                    + ": phase PREPARED non persistée.");
            return;
        }

        RewardManager rewardManager = plugin.getRewardManager();
        for (RewardDefinition reward : next.getRewardsOnEnter()) {
            String key = rewardKey(nextLevel, reward);
            if (faction.hasAppliedReward(key)) {
                state.removePendingReward(key);
                continue;
            }
            RewardManager.ApplyResult result = rewardManager == null
                    ? RewardManager.ApplyResult.UNAVAILABLE
                    : rewardManager.applyProgressionReward(faction, nextLevel, reward);
            if (result == RewardManager.ApplyResult.APPLIED) {
                faction.addAppliedReward(key);
                state.removePendingReward(key);
                if (!plugin.getStorageManager().saveFactionNow(faction)) {
                    plugin.getLogger().severe("Récompense " + key
                            + " appliquée mais accusé de sauvegarde impossible; "
                            + "elle restera à auditer, sans rejeu automatique.");
                }
            } else {
                plugin.getLogger().severe("Récompense " + key + " en attente pour "
                        + faction.getName() + " (" + result + ").");
            }
        }

        if (state.getPendingRewards().isEmpty()) {
            state.setPendingTransition(null);
        } else {
            state.setPendingTransition(oldLevel + "->" + nextLevel
                    + ":REWARD_PENDING");
        }
        plugin.getStorageManager().saveFactionNow(faction);
        if (config.isBroadcastLevelUp()) {
            faction.broadcast("§6§l✦ §eVotre faction passe au §6niveau "
                    + nextLevel + "§e. Nouvelle tranche recalculée: §6"
                    + state.getLockedTierId() + "§e.");
        }
    }

    private void validateLiveCompatibility(ProgressionConfig candidate,
            List<ValidationIssue> issues) {
        if (plugin.getFactionManager() == null) return;
        for (Faction faction : plugin.getFactionManager().getAllFactions()) {
            FactionProgressState state = faction.getProgressionState();
            if (state.getSchemaVersion()
                    < FactionProgressState.CURRENT_SCHEMA_VERSION) continue;
            LevelDefinition level = candidate.getLevel(faction.getLevel());
            String tierId = state.getLockedTierId();
            if (level == null) {
                issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR,
                        "progression.yml.levels." + faction.getLevel(),
                        "niveau utilisé par la faction " + faction.getName()
                                + " mais absent du candidat."));
            } else if (tierId == null || candidate.getTier(tierId) == null
                    || level.getTier(tierId) == null) {
                issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR,
                        "progression.yml.levels." + faction.getLevel()
                                + ".tiers." + tierId,
                        "tranche verrouillée utilisée par la faction "
                                + faction.getName() + " mais absente du candidat."));
            }
        }
    }

    private ValidationEnvironment runtimeValidationEnvironment() {
        return new ValidationEnvironment() {
            @Override public Status worldExists(String world) {
                return Bukkit.getWorld(world) == null ? Status.INVALID : Status.VALID;
            }
            @Override public Status regionExists(String world, String region) {
                return Status.UNKNOWN;
            }
            @Override public Status customItemExists(String itemId) {
                return Status.UNKNOWN;
            }
            @Override public Status kcraftRecipeExists(String recipeId) {
                return Status.UNKNOWN;
            }
        };
    }

    private Object lockFor(Faction faction) {
        Object created = new Object();
        Object existing = factionLocks.putIfAbsent(faction.getId(), created);
        return existing == null ? created : existing;
    }

    private int memberCount(Faction faction) {
        return Math.max(1, faction.getMemberCount());
    }

    private String rewardKey(int level, RewardDefinition reward) {
        return "level_" + level + "_reward_" + reward.getId();
    }

    private boolean hasErrors(List<ValidationIssue> issues) {
        for (ValidationIssue issue : issues) {
            if (issue.getSeverity() == ValidationIssue.Severity.ERROR) return true;
        }
        return false;
    }

    private void logValidationIssues(List<ValidationIssue> issues) {
        for (ValidationIssue issue : issues) {
            String message = "[progression] " + issue;
            if (issue.getSeverity() == ValidationIssue.Severity.ERROR) {
                plugin.getLogger().severe(message);
            } else plugin.getLogger().warning(message);
        }
    }

    private int countQuests(ProgressionConfig config) {
        int count = 0;
        for (LevelDefinition level : config.getLevels().values()) {
            for (me.krunsh.kfaction.progression.TierLevelDefinition tier
                    : level.getTiers().values()) {
                count += tier.getQuests().size();
            }
        }
        return count;
    }

    private void warnLegacyCommand(String operation) {
        if (warnedLegacyCommands.add(operation)) {
            plugin.getLogger().warning("Opération legacy " + operation
                    + " ignorée: catégories choisies et rerolls ont été supprimés "
                    + "du gameplay progression v2.");
        }
    }
}
