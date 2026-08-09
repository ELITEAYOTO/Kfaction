package me.krunsh.kfaction.services;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.api.event.FactionProgressionEvent;
import me.krunsh.kfaction.api.event.FactionProgressionEvent.Action;
import me.krunsh.kfaction.api.event.FactionProgressionEvent.Phase;
import me.krunsh.kfaction.audit.AuditCategory;
import me.krunsh.kfaction.audit.AuditOutcome;
import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.core.operation.OperationSource;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.QuestCategory;
import me.krunsh.kfaction.data.Relation;
import me.krunsh.kfaction.managers.RewardManager;
import me.krunsh.kfaction.progression.FactionProgressState;
import me.krunsh.kfaction.progression.FactionProgressState.PendingRewardStatus;
import me.krunsh.kfaction.progression.LevelDefinition;
import me.krunsh.kfaction.progression.MemberTierDefinition;
import me.krunsh.kfaction.progression.ProgressionConfig;
import me.krunsh.kfaction.progression.ProgressionConfigLoader;
import me.krunsh.kfaction.progression.ProgressionEngine;
import me.krunsh.kfaction.progression.ProgressionMigrationService;
import me.krunsh.kfaction.progression.ProgressionPolicy;
import me.krunsh.kfaction.progression.ProgressionStatus;
import me.krunsh.kfaction.progression.ProgressionUpdate;
import me.krunsh.kfaction.progression.QuestAction;
import me.krunsh.kfaction.progression.QuestProgressView;
import me.krunsh.kfaction.progression.QuestTypeRegistry;
import me.krunsh.kfaction.progression.RewardDefinition;
import me.krunsh.kfaction.progression.ValidationEnvironment;
import me.krunsh.kfaction.progression.ValidationIssue;

/**
 * Couche métier Progression V2.
 *
 * QuestManager devient une façade de compatibilité autour de ce service.
 *
 * Garanties:
 * - mutations de progression uniquement sur le main thread Bukkit;
 * - progression.yml candidat validé avant swap;
 * - quêtes d'un même événement appliquées avant le level-up;
 * - ids de quêtes dédupliqués par événement;
 * - barrière de persistance avant application des récompenses;
 * - aucune récompense pending n'est rejouée automatiquement;
 * - une faction avec reward pending est bloquée pour éviter d'empiler
 *   plusieurs transitions ambiguës;
 * - audit uniquement sur jalons, jamais sur chaque bloc/mob.
 */
public final class ProgressionService {

    private final Kfaction plugin;

    private final QuestTypeRegistry typeRegistry =
            QuestTypeRegistry.builtIns();

    private final Map<String, Object> factionLocks =
            new ConcurrentHashMap<String, Object>();

    private final Set<String> warnedPending =
            Collections.newSetFromMap(
                    new ConcurrentHashMap<String, Boolean>()
            );

    private final Set<String> warnedMismatch =
            Collections.newSetFromMap(
                    new ConcurrentHashMap<String, Boolean>()
            );

    private final Set<String> warnedLegacyCommands =
            Collections.newSetFromMap(
                    new ConcurrentHashMap<String, Boolean>()
            );

    private final Set<String> warnedAsync =
            Collections.newSetFromMap(
                    new ConcurrentHashMap<String, Boolean>()
            );

    private final ProgressionMigrationService migrationService;

    private final LongAdder actionsProcessed =
            new LongAdder();

    private final LongAdder questCompletions =
            new LongAdder();

    private final LongAdder levelUps =
            new LongAdder();

    private final LongAdder blockedMutations =
            new LongAdder();

    private volatile ProgressionConfig activeConfig;

    private volatile List<ValidationIssue> lastValidationIssues =
            Collections.emptyList();

    public ProgressionService(
            Kfaction plugin
    ) {
        if (plugin == null) {
            throw new IllegalArgumentException(
                    "plugin cannot be null"
            );
        }

        this.plugin = plugin;

        this.migrationService =
                new ProgressionMigrationService(
                        plugin
                );
    }

    // ============================================================
    // Lifecycle / config
    // ============================================================

    public void initialize() {
        reloadConfig(
                OperationContext.system()
        );
    }

    public void shutdown() {
        factionLocks.clear();
        warnedPending.clear();
        warnedMismatch.clear();
        warnedLegacyCommands.clear();
        warnedAsync.clear();
    }

    /**
     * Libère tous les caches runtime associés à une faction supprimée.
     *
     * Aucun état durable n'est modifié.
     */
    public void releaseFactionRuntimeState(
            String factionId
    ) {
        if (factionId == null) {
            return;
        }

        factionLocks.remove(
                factionId
        );

        warnedPending.remove(
                factionId
        );

        warnedMismatch.remove(
                factionId
        );

        warnedLegacyCommands.remove(
                factionId
        );

        warnedAsync.remove(
                factionId
        );
    }

    /**
     * Compatibilité V1/manager.
     */
    public void loadConfig() {
        reloadConfig(
                OperationContext.system()
        );
    }

    /**
     * Charge un snapshot candidat entier puis effectue un swap atomique de la
     * référence active uniquement s'il est valide.
     */
    public boolean reloadConfig(
            OperationContext context
    ) {
        OperationContext safeContext =
                context != null
                        ? context
                        : OperationContext.system();

        File file =
                new File(
                        plugin.getDataFolder(),
                        "progression.yml"
                );

        /*
         * progression.yml est désormais la ressource active fournie avec
         * Kfaction. On ne génère plus progression.example.yml.
         *
         * IMPORTANT:
         * saveResource(..., false) n'est appelé QUE si le fichier n'existe
         * pas. Cela supprime les faux WARN Bukkit "already exists".
         */
        if (!file.isFile()) {
            try {
                plugin.saveResource(
                        "progression.yml",
                        false
                );
            } catch (IllegalArgumentException exception) {
                me.krunsh.kfaction.utils.KfactionLogger.error(
                        plugin,
                        "Ressource progression.yml absente du JAR: "
                                + exception.getMessage()
                );
            }
        } else {
            me.krunsh.kfaction.utils.KfactionLogger.debug(
                    plugin,
                    "progression.yml externe détecté; fichier existant conservé."
            );
        }

        if (!file.isFile()) {
            if (activeConfig == null) {
                me.krunsh.kfaction.utils.KfactionLogger.error(
                        plugin,
                        "progression.yml introuvable: progression faction désactivée."
                );
            } else {
                me.krunsh.kfaction.utils.KfactionLogger.error(
                        plugin,
                        "progression.yml absent au reload: snapshot actif précédent conservé."
                );
            }

            audit(
                    safeContext,
                    null,
                    "PROGRESSION_CONFIG_RELOAD",
                    AuditOutcome.FAILED,
                    "progression.yml absent"
            );

            firePost(
                    Action.CONFIG_RELOAD,
                    null,
                    0,
                    0,
                    null,
                    "FAILED: progression.yml absent",
                    safeContext
            );

            return false;
        }

        int maxMembers =
                plugin.getConfigManager()
                        .getInt(
                                "factions.members.max-per-faction",
                                50
                        );

        ProgressionConfigLoader loader =
                new ProgressionConfigLoader(
                        typeRegistry,
                        runtimeValidationEnvironment(),
                        maxMembers
                );

        ProgressionConfigLoader.LoadResult result =
                loader.load(file);

        List<ValidationIssue> issues =
                new ArrayList<ValidationIssue>(
                        result.getIssues()
                );

        if (result.isValid()) {
            validateLiveCompatibility(
                    result.getConfig(),
                    issues
            );
        }

        lastValidationIssues =
                Collections.unmodifiableList(
                        new ArrayList<ValidationIssue>(
                                issues
                        )
                );

        if (!result.isValid()
                || hasErrors(issues)) {
            logValidationIssues(issues);

            plugin.getLogger().severe(
                    "progression.yml refusé; "
                            + (activeConfig == null
                                    ? "progression désactivée."
                                    : "snapshot actif précédent conservé.")
            );

            audit(
                    safeContext,
                    null,
                    "PROGRESSION_CONFIG_RELOAD",
                    AuditOutcome.FAILED,
                    "validation-errors="
                            + countErrors(issues)
            );

            firePost(
                    Action.CONFIG_RELOAD,
                    null,
                    0,
                    0,
                    null,
                    "FAILED validation-errors="
                            + countErrors(issues),
                    safeContext
            );

            return false;
        }

        activeConfig =
                result.getConfig();

        logValidationIssues(issues);

        me.krunsh.kfaction.utils.KfactionLogger.success(
                plugin,
                "Progression chargée — "
                        + activeConfig.getLevels()
                                .size()
                        + " niveaux"
                        + " | "
                        + activeConfig.getTiers()
                                .size()
                        + " tranches"
                        + " | "
                        + countQuests(activeConfig)
                        + " quêtes"
        );

        migrateLoadedFactions();

        audit(
                safeContext,
                null,
                "PROGRESSION_CONFIG_RELOAD",
                AuditOutcome.SUCCESS,
                "levels="
                        + activeConfig.getLevels()
                                .size()
                        + ";tiers="
                        + activeConfig.getTiers()
                                .size()
                        + ";quests="
                        + countQuests(activeConfig)
        );

        firePost(
                Action.CONFIG_RELOAD,
                null,
                0,
                0,
                null,
                "SUCCESS",
                safeContext
        );

        return true;
    }

    public void migrateLoadedFactions() {
        ProgressionConfig config =
                activeConfig;

        if (config == null
                || plugin.getFactionManager() == null) {
            return;
        }

        for (Faction faction
                : plugin.getFactionManager()
                        .getAllFactions()) {
            ensureReadable(
                    faction,
                    config
            );
        }
    }

    public boolean isEnabled() {
        ProgressionConfig config =
                activeConfig;

        return config != null
                && config.isEnabled();
    }

    public ProgressionConfig getActiveConfig() {
        return activeConfig;
    }

    public List<ValidationIssue>
            getLastValidationIssues() {
        return lastValidationIssues;
    }

    /**
     * Valide le fichier disque sans remplacer le snapshot actif.
     */
    public List<ValidationIssue> validateCandidate() {
        File file =
                new File(
                        plugin.getDataFolder(),
                        "progression.yml"
                );

        int maxMembers =
                plugin.getConfigManager()
                        .getInt(
                                "factions.members.max-per-faction",
                                50
                        );

        ProgressionConfigLoader.LoadResult result =
                new ProgressionConfigLoader(
                        typeRegistry,
                        runtimeValidationEnvironment(),
                        maxMembers
                ).load(file);

        List<ValidationIssue> issues =
                new ArrayList<ValidationIssue>(
                        result.getIssues()
                );

        if (result.isValid()) {
            validateLiveCompatibility(
                    result.getConfig(),
                    issues
            );
        }

        return Collections.unmodifiableList(
                issues
        );
    }

    // ============================================================
    // Read views
    // ============================================================

    public List<QuestProgressView> getQuestViews(
            Faction faction
    ) {
        ProgressionConfig config =
                activeConfig;

        if (!ensureReadable(
                faction,
                config
        )) {
            return Collections.emptyList();
        }

        Object lock =
                lockFor(faction);

        synchronized (lock) {
            int previousRank =
                    faction.getProgressionState()
                            .getLockedTierRank();

            List<QuestProgressView> views =
                    ProgressionPolicy.views(
                            config,
                            faction.getProgressionState(),
                            faction.getLevel(),
                            memberCount(faction)
                    );

            if (previousRank
                    != faction.getProgressionState()
                            .getLockedTierRank()) {
                plugin.getStorageManager()
                        .markDirty(faction);
            }

            return views;
        }
    }

    public MemberTierDefinition getCurrentTier(
            Faction faction
    ) {
        ProgressionConfig config =
                activeConfig;

        if (!ensureReadable(
                faction,
                config
        )) {
            return null;
        }

        synchronized (lockFor(faction)) {
            int previousRank =
                    faction.getProgressionState()
                            .getLockedTierRank();

            MemberTierDefinition tier =
                    ProgressionPolicy.refreshLockedTier(
                            config,
                            faction.getProgressionState(),
                            memberCount(faction)
                    );

            if (previousRank
                    != faction.getProgressionState()
                            .getLockedTierRank()) {
                plugin.getStorageManager()
                        .markDirty(faction);
            }

            return tier;
        }
    }

    public ProgressionStatus getStatus(
            Faction faction
    ) {
        ProgressionConfig config =
                activeConfig;

        if (faction == null
                || faction.isSystemFaction()
                || config == null
                || !config.isEnabled()) {
            return new ProgressionStatus(
                    ProgressionStatus.Health.DISABLED,
                    faction != null
                            ? faction.getLevel()
                            : 0,
                    faction != null
                            ? faction.getProgressionState()
                                    .getLevelStarted()
                            : 0,
                    config != null
                            ? config.getMaxLevel()
                            : 0,
                    null,
                    0,
                    0,
                    0,
                    null,
                    Collections.<String>
                            emptyList(),
                    0L,
                    0L,
                    0L
            );
        }

        boolean readable =
                ensureReadable(
                        faction,
                        config
                );

        FactionProgressState state =
                faction.getProgressionState();

        ProgressionStatus.Health health;

        if (!readable
                || state.getLevelStarted()
                        != faction.getLevel()) {
            health =
                    ProgressionStatus.Health
                            .BLOCKED_STATE_MISMATCH;

        } else if (!state.getPendingRewards()
                .isEmpty()) {
            health =
                    ProgressionStatus.Health
                            .BLOCKED_PENDING_REWARD;

        } else {
            health =
                    ProgressionStatus.Health.READY;
        }

        List<QuestProgressView> views =
                readable
                        ? getQuestViews(faction)
                        : Collections.<QuestProgressView>
                                emptyList();

        int completed = 0;
        int totalPercent = 0;

        for (QuestProgressView view : views) {
            if (view.isCompleted()) {
                completed++;
            }

            totalPercent +=
                    Math.max(
                            0,
                            Math.min(
                                    100,
                                    view.getPercent()
                            )
                    );
        }

        int percent =
                views.isEmpty()
                        ? 0
                        : totalPercent
                                / views.size();

        return new ProgressionStatus(
                health,
                faction.getLevel(),
                state.getLevelStarted(),
                config.getMaxLevel(),
                state.getLockedTierId(),
                completed,
                views.size(),
                percent,
                state.getPendingTransition(),
                new ArrayList<String>(
                        state.getPendingRewards()
                ),
                state.getLastProgressAt(),
                state.getLastLevelUpAt(),
                state.getTransitionRevision()
        );
    }

    /**
     * Snapshot de quêtes strictement read-only.
     *
     * Aucune migration, reconciliation, lockTier, markDirty, audit ou event.
     * Utilisé par l'API V2 afin que "read" signifie réellement "read".
     */
    public List<QuestProgressView> peekQuestViews(
            Faction faction
    ) {
        ProgressionConfig config =
                activeConfig;

        if (faction == null
                || faction.isSystemFaction()
                || config == null
                || !config.isEnabled()) {
            return Collections.emptyList();
        }

        synchronized (lockFor(faction)) {
            return ProgressionPolicy.viewsReadOnly(
                    config,
                    faction.getProgressionState(),
                    faction.getLevel(),
                    memberCount(faction)
            );
        }
    }

    /**
     * Etat progression strictement read-only destiné aux consumers API.
     *
     * Les commandes/admin internes peuvent continuer d'utiliser getStatus()
     * lorsqu'elles veulent le comportement de reconciliation V2.
     */
    public ProgressionStatus peekStatus(
            Faction faction
    ) {
        ProgressionConfig config =
                activeConfig;

        if (faction == null
                || faction.isSystemFaction()
                || config == null
                || !config.isEnabled()) {
            return new ProgressionStatus(
                    ProgressionStatus.Health.DISABLED,
                    faction != null
                            ? faction.getLevel()
                            : 0,
                    faction != null
                            ? faction.getProgressionState()
                                    .getLevelStarted()
                            : 0,
                    config != null
                            ? config.getMaxLevel()
                            : 0,
                    null,
                    0,
                    0,
                    0,
                    null,
                    Collections.<String>emptyList(),
                    0L,
                    0L,
                    0L
            );
        }

        synchronized (lockFor(faction)) {
            FactionProgressState state =
                    faction.getProgressionState();

            ProgressionStatus.Health health;

            if (state.getLevelStarted()
                    != faction.getLevel()) {
                health =
                        ProgressionStatus.Health
                                .BLOCKED_STATE_MISMATCH;

            } else if (!state.getPendingRewards()
                    .isEmpty()) {
                health =
                        ProgressionStatus.Health
                                .BLOCKED_PENDING_REWARD;

            } else {
                health =
                        ProgressionStatus.Health.READY;
            }

            List<QuestProgressView> views =
                    state.getLevelStarted()
                            == faction.getLevel()
                                    ? ProgressionPolicy.viewsReadOnly(
                                            config,
                                            state,
                                            faction.getLevel(),
                                            memberCount(faction)
                                    )
                                    : Collections.<QuestProgressView>
                                            emptyList();

            int completed =
                    0;

            int totalPercent =
                    0;

            for (QuestProgressView view
                    : views) {
                if (view.isCompleted()) {
                    completed++;
                }

                totalPercent +=
                        Math.max(
                                0,
                                Math.min(
                                        100,
                                        view.getPercent()
                                )
                        );
            }

            int percent =
                    views.isEmpty()
                            ? 0
                            : totalPercent
                                    / views.size();

            MemberTierDefinition readOnlyTier =
                    ProgressionPolicy.resolveLockedTierReadOnly(
                            config,
                            state,
                            memberCount(faction)
                    );

            return new ProgressionStatus(
                    health,
                    faction.getLevel(),
                    state.getLevelStarted(),
                    config.getMaxLevel(),
                    readOnlyTier != null
                            ? readOnlyTier.getId()
                            : state.getLockedTierId(),
                    completed,
                    views.size(),
                    percent,
                    state.getPendingTransition(),
                    new ArrayList<String>(
                            state.getPendingRewards()
                    ),
                    state.getLastProgressAt(),
                    state.getLastLevelUpAt(),
                    state.getTransitionRevision()
            );
        }
    }

    // ============================================================
    // Mutations
    // ============================================================

    public ProgressionUpdate applyAction(
            Faction faction,
            QuestAction action
    ) {
        return applyAction(
                faction,
                action,
                OperationContext.system()
        );
    }

    public ProgressionUpdate applyAction(
            Faction faction,
            QuestAction action,
            OperationContext context
    ) {
        if (action == null) {
            return ProgressionUpdate.NONE;
        }

        return applyActions(
                faction,
                Collections.singletonList(
                        action
                ),
                context
        );
    }

    public ProgressionUpdate applyActions(
            Faction faction,
            List<QuestAction> actions
    ) {
        return applyActions(
                faction,
                actions,
                OperationContext.system()
        );
    }

    /**
     * Applique toutes les facettes d'un même événement avant d'évaluer le
     * level-up.
     *
     * Un BlockBreak MINE+BREAK ne peut donc pas toucher deux niveaux.
     */
    public ProgressionUpdate applyActions(
            final Faction faction,
            final List<QuestAction> actions,
            final OperationContext context
    ) {
        if (faction == null
                || actions == null
                || actions.isEmpty()) {
            return ProgressionUpdate.NONE;
        }

        if (!Bukkit.isPrimaryThread()) {
            final List<QuestAction> immutableCopy =
                    new ArrayList<QuestAction>(
                            actions
                    );

            if (warnedAsync.add(
                    faction.getId()
            )) {
                plugin.getLogger().warning(
                        "Progression reçue hors main thread pour "
                                + faction.getName()
                                + ": action replanifiée sur Bukkit."
                );
            }

            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            new Runnable() {
                                @Override
                                public void run() {
                                    applyActions(
                                            faction,
                                            immutableCopy,
                                            context
                                    );
                                }
                            }
                    );

            return ProgressionUpdate.NONE;
        }

        ProgressionConfig config =
                activeConfig;

        if (!ensureHealthyForMutation(
                faction,
                config,
                context
        )) {
            blockedMutations.increment();
            return ProgressionUpdate.NONE;
        }

        int maxActions =
                Math.max(
                        1,
                        Math.min(
                                64,
                                plugin.getConfigManager()
                                        .getInt(
                                                "progression-runtime.max-actions-per-event",
                                                16
                                        )
                        )
                );

        if (actions.size() > maxActions) {
            blockedMutations.increment();

            plugin.getLogger().warning(
                    "Batch progression refusé pour "
                            + faction.getName()
                            + ": "
                            + actions.size()
                            + " actions > "
                            + maxActions
            );

            return ProgressionUpdate.NONE;
        }

        OperationContext safeContext =
                context != null
                        ? context
                        : OperationContext.system();

        synchronized (lockFor(faction)) {
            LinkedHashSet<String> progressed =
                    new LinkedHashSet<String>();

            LinkedHashSet<String> completed =
                    new LinkedHashSet<String>();

            boolean levelComplete = false;

            for (QuestAction action : actions) {
                if (action == null) {
                    continue;
                }

                ProgressionUpdate part =
                        ProgressionEngine.apply(
                                config,
                                faction.getProgressionState(),
                                faction.getLevel(),
                                memberCount(faction),
                                action
                        );

                progressed.addAll(
                        part.getProgressedQuestIds()
                );

                completed.addAll(
                        part.getNewlyCompletedQuestIds()
                );

                levelComplete =
                        levelComplete
                                || part.isLevelComplete();

                actionsProcessed.increment();
            }

            if (progressed.isEmpty()) {
                return ProgressionUpdate.NONE;
            }

            FactionProgressState state =
                    faction.getProgressionState();

            state.markProgress(
                    System.currentTimeMillis()
            );

            plugin.getStorageManager()
                    .markDirty(faction);

            for (String questId : completed) {
                onQuestCompleted(
                        faction,
                        questId,
                        safeContext
                );
            }

            if (levelComplete) {
                transitionToNextLevel(
                        faction,
                        config,
                        safeContext
                );
            }

            return new ProgressionUpdate(
                    new ArrayList<String>(
                            progressed
                    ),
                    new ArrayList<String>(
                            completed
                    ),
                    levelComplete
            );
        }
    }

    private void onQuestCompleted(
            Faction faction,
            String questId,
            OperationContext context
    ) {
        questCompletions.increment();

        if (plugin.getConfigManager()
                .getBoolean(
                        "progression-runtime.broadcast-quest-complete",
                        true
                )) {
            faction.broadcast(
                    "§a✓ §7Quête obligatoire terminée: §e"
                            + questId
            );
        }

        if (plugin.getConfigManager()
                .getBoolean(
                        "progression-runtime.audit-quest-complete",
                        true
                )) {
            audit(
                    context,
                    faction,
                    "QUEST_COMPLETED",
                    AuditOutcome.SUCCESS,
                    "quest="
                            + questId
                            + ";level="
                            + faction.getLevel()
            );
        }

        firePost(
                Action.QUEST_COMPLETED,
                faction,
                faction.getLevel(),
                faction.getLevel(),
                questId,
                null,
                context
        );
    }

    /**
     * Transition durable:
     *
     * 1. PRE event
     * 2. état PREPARED + pending rewards
     * 3. changement de niveau + beginLevel
     * 4. SAVE BARRIER
     * 5. application reward une seule fois
     * 6. accusé durable appliedRewards
     * 7. cleanup pending
     *
     * Aucun pending reward n'est rejoué automatiquement au redémarrage.
     */
    private void transitionToNextLevel(
            Faction faction,
            ProgressionConfig config,
            OperationContext context
    ) {
        int oldLevel =
                faction.getLevel();

        int nextLevel =
                oldLevel + 1;

        LevelDefinition next =
                config.getLevel(
                        nextLevel
                );

        if (next == null) {
            faction.broadcast(
                    "§6✦ §eToutes les quêtes du niveau maximal sont terminées."
            );

            return;
        }

        FactionProgressionEvent pre =
                new FactionProgressionEvent(
                        Phase.PRE,
                        Action.LEVEL_UP,
                        faction,
                        oldLevel,
                        nextLevel,
                        null,
                        null,
                        context
                );

        Bukkit.getPluginManager()
                .callEvent(pre);

        if (pre.isCancelled()) {
            audit(
                    context,
                    faction,
                    "LEVEL_UP",
                    AuditOutcome.CANCELLED,
                    "old="
                            + oldLevel
                            + ";new="
                            + nextLevel
            );

            return;
        }

        FactionProgressState state =
                faction.getProgressionState();

        FactionProgressState.Snapshot before =
                state.snapshot();

        long revision =
                state.nextTransitionRevision();

        state.setPendingTransition(
                oldLevel
                        + "->"
                        + nextLevel
                        + ":PREPARED:r"
                        + revision
        );

        for (RewardDefinition reward
                : next.getRewardsOnEnter()) {
            String key =
                    rewardKey(
                            nextLevel,
                            reward
                    );

            if (!faction.hasAppliedReward(key)) {
                state.addPendingReward(key);
            }
        }

        faction.setLevel(nextLevel);

        ProgressionPolicy.beginNextLevel(
                config,
                state,
                nextLevel,
                memberCount(faction)
        );

        state.markLevelUp(
                System.currentTimeMillis()
        );

        /*
         * Barrière durable avant toute récompense externe/additive.
         */
        if (!plugin.getStorageManager()
                .saveFactionNow(faction)) {
            faction.setLevel(oldLevel);
            state.restore(before);

            plugin.getLogger().severe(
                    "Level-up annulé pour "
                            + faction.getName()
                            + ": phase PREPARED non persistée."
            );

            audit(
                    context,
                    faction,
                    "LEVEL_UP",
                    AuditOutcome.FAILED,
                    "old="
                            + oldLevel
                            + ";new="
                            + nextLevel
                            + ";reason=prepared-save-failed"
            );

            return;
        }

        RewardManager rewardManager =
                plugin.getRewardManager();

        for (RewardDefinition reward
                : next.getRewardsOnEnter()) {
            String key =
                    rewardKey(
                            nextLevel,
                            reward
                    );

            if (faction.hasAppliedReward(key)) {
                state.removePendingReward(key);
                continue;
            }

            RewardManager.ApplyResult result;

            try {
                result =
                        rewardManager == null
                                ? RewardManager.ApplyResult
                                        .UNAVAILABLE
                                : rewardManager
                                        .applyProgressionReward(
                                                faction,
                                                nextLevel,
                                                reward
                                        );
            } catch (Throwable throwable) {
                result =
                        RewardManager.ApplyResult.UNAVAILABLE;

                plugin.getLogger().severe(
                        "Exception récompense progression "
                                + key
                                + " pour "
                                + faction.getName()
                                + ": "
                                + throwable.getMessage()
                );
            }

            long now =
                    System.currentTimeMillis();

            if (result
                    == RewardManager.ApplyResult.APPLIED) {
                /*
                 * On garde la clé pending pendant l'ACK.
                 * appliedRewards + pending sont persistés ensemble.
                 *
                 * Si le serveur crash après ce save mais avant le cleanup,
                 * le prochain démarrage peut retirer le pending sans rejouer
                 * la récompense car appliedRewards constitue la preuve durable.
                 */
                faction.addAppliedReward(key);

                state.markPendingReward(
                        key,
                        PendingRewardStatus
                                .APPLIED_ACK_PENDING,
                        "reward-applied-awaiting-save",
                        now
                );

                if (plugin.getStorageManager()
                        .saveFactionNow(faction)) {
                    state.removePendingReward(key);

                    audit(
                            context,
                            faction,
                            "REWARD_APPLIED",
                            AuditOutcome.SUCCESS,
                            "key="
                                    + key
                                    + ";level="
                                    + nextLevel
                    );

                    firePost(
                            Action.REWARD_APPLIED,
                            faction,
                            oldLevel,
                            nextLevel,
                            key,
                            null,
                            context
                    );

                } else {
                    state.markPendingReward(
                            key,
                            PendingRewardStatus
                                    .ACK_FAILED,
                            "reward-applied-but-durable-ack-failed",
                            now
                    );

                    plugin.getLogger().severe(
                            "Récompense "
                                    + key
                                    + " appliquée mais ACK durable impossible pour "
                                    + faction.getName()
                                    + ". Aucun rejeu automatique."
                    );

                    audit(
                            context,
                            faction,
                            "REWARD_PENDING",
                            AuditOutcome.FAILED,
                            "key="
                                    + key
                                    + ";status=ACK_FAILED"
                    );

                    firePost(
                            Action.REWARD_PENDING,
                            faction,
                            oldLevel,
                            nextLevel,
                            key,
                            "ACK_FAILED",
                            context
                    );
                }

            } else {
                PendingRewardStatus status =
                        result
                                == RewardManager.ApplyResult.INVALID
                                ? PendingRewardStatus.INVALID
                                : PendingRewardStatus.UNAVAILABLE;

                state.markPendingReward(
                        key,
                        status,
                        String.valueOf(result),
                        now
                );

                /*
                 * Persister le diagnostic autant que possible.
                 */
                plugin.getStorageManager()
                        .saveFactionNow(faction);

                plugin.getLogger().severe(
                        "Récompense "
                                + key
                                + " en attente pour "
                                + faction.getName()
                                + " ("
                                + result
                                + "). Progression bloquée jusqu'à résolution."
                );

                audit(
                        context,
                        faction,
                        "REWARD_PENDING",
                        AuditOutcome.FAILED,
                        "key="
                                + key
                                + ";status="
                                + status.name()
                );

                firePost(
                        Action.REWARD_PENDING,
                        faction,
                        oldLevel,
                        nextLevel,
                        key,
                        status.name(),
                        context
                );
            }
        }

        reconcileDurablyAppliedPending(
                faction,
                false
        );

        if (state.getPendingRewards()
                .isEmpty()) {
            state.setPendingTransition(null);
        } else {
            state.setPendingTransition(
                    oldLevel
                            + "->"
                            + nextLevel
                            + ":REWARD_PENDING:r"
                            + revision
            );
        }

        /*
         * Persiste le cleanup des pending et l'état final.
         * Si ce save échoue, appliedRewards déjà ACKées permettent une
         * réconciliation sûre au prochain démarrage.
         */
        boolean finalSaved =
                plugin.getStorageManager()
                        .saveFactionNow(faction);

        if (!finalSaved) {
            plugin.getLogger().severe(
                    "Sauvegarde finale de transition impossible pour "
                            + faction.getName()
                            + ". L'état restera bloqué/réconciliable au prochain chargement."
            );
        } else {
            /*
             * À partir d'ici appliedRewards est une preuve durable, même pour
             * un ACK_FAILED antérieur du même runtime.
             */
            int reconciled =
                    reconcileDurablyAppliedPending(
                            faction,
                            true
                    );

            if (reconciled > 0) {
                if (state.getPendingRewards()
                        .isEmpty()) {
                    state.setPendingTransition(null);
                }

                /*
                 * Persiste le cleanup. Si ce dernier save échoue, la preuve
                 * appliedRewards est déjà durable: le prochain chargement
                 * refera le cleanup sans rejouer le reward.
                 */
                plugin.getStorageManager()
                        .saveFactionNow(faction);
            }
        }

        levelUps.increment();

        audit(
                context,
                faction,
                "LEVEL_UP",
                state.getPendingRewards()
                        .isEmpty()
                        ? AuditOutcome.SUCCESS
                        : AuditOutcome.INFO,
                "old="
                        + oldLevel
                        + ";new="
                        + nextLevel
                        + ";tier="
                        + state.getLockedTierId()
                        + ";pending="
                        + state.getPendingRewards()
                                .size()
        );

        firePost(
                Action.LEVEL_UP,
                faction,
                oldLevel,
                nextLevel,
                null,
                state.getPendingRewards()
                        .isEmpty()
                        ? "SUCCESS"
                        : "REWARD_PENDING",
                context
        );

        if (config.isBroadcastLevelUp()) {
            faction.broadcast(
                    "§6§l✦ §eVotre faction passe au §6niveau "
                            + nextLevel
                            + "§e. Nouvelle tranche: §6"
                            + state.getLockedTierId()
                            + "§e."
            );

            if (!state.getPendingRewards()
                    .isEmpty()) {
                faction.broadcast(
                        "§c⚠ Certaines récompenses sont en attente. "
                                + "La progression est temporairement bloquée."
                );
            }
        }
    }

    // ============================================================
    // Event bridges / compatibility
    // ============================================================

    public int progressEntityAction(
            Faction faction,
            String type,
            EntityType entityType,
            long amount,
            String world,
            Set<String> regions
    ) {
        return applyAction(
                faction,
                QuestAction.entity(
                        type,
                        entityType,
                        amount,
                        world,
                        regions
                )
        ).getProgressedQuestIds()
                .size();
    }

    public int progressPlayerKill(
            Player killer,
            Player victim,
            String world,
            Set<String> regions,
            long timestampMillis
    ) {
        if (killer == null
                || victim == null
                || killer.getUniqueId()
                        .equals(
                                victim.getUniqueId()
                        )) {
            return 0;
        }

        Faction killerFaction =
                plugin.getFactionManager()
                        .getPlayerFaction(
                                killer
                        );

        if (killerFaction == null) {
            return 0;
        }

        Faction victimFaction =
                plugin.getFactionManager()
                        .getPlayerFaction(
                                victim
                        );

        boolean sameFaction =
                victimFaction != null
                        && victimFaction.getId()
                                .equals(
                                        killerFaction.getId()
                                );

        Relation relation =
                victimFaction == null
                        ? Relation.NEUTRAL
                        : killerFaction.getRelationTo(
                                victimFaction.getId()
                        );

        boolean allied =
                relation == Relation.ALLY
                        || relation == Relation.TRUCE;

        boolean npc =
                victim.hasMetadata("NPC")
                        || victim.hasMetadata(
                                "CitizensNPC"
                        );

        boolean sameIp =
                killer.getAddress() != null
                        && victim.getAddress() != null
                        && killer.getAddress()
                                .getAddress() != null
                        && killer.getAddress()
                                .getAddress()
                                .equals(
                                        victim.getAddress()
                                                .getAddress()
                                );

        QuestAction action =
                QuestAction.playerKill(
                        1L,
                        world,
                        regions,
                        victim.getUniqueId(),
                        sameFaction,
                        allied,
                        npc,
                        sameIp,
                        timestampMillis
                );

        return applyAction(
                killerFaction,
                action,
                OperationContext.actor(
                        killer.getUniqueId(),
                        killer.getName(),
                        OperationSource.SYSTEM
                )
        ).getProgressedQuestIds()
                .size();
    }

    public boolean shouldTrackPlayerPlaced(
            Material material,
            int data
    ) {
        ProgressionConfig config =
                activeConfig;

        if (config == null
                || material == null) {
            return false;
        }

        for (LevelDefinition level
                : config.getLevels()
                        .values()) {
            for (me.krunsh.kfaction.progression.TierLevelDefinition tier
                    : level.getTiers()
                            .values()) {
                for (me.krunsh.kfaction.progression.QuestDefinition quest
                        : tier.getQuests()
                                .values()) {
                    if (!quest.getConditions()
                            .isCountPlayerPlacedBlocks()
                            && quest.getTarget()
                                    .matchesMaterial(
                                            material,
                                            data,
                                            null
                                    )) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public int progressBlockAction(
            Faction faction,
            String type,
            Material material,
            int data,
            String sparrowItemId,
            long amount,
            String world,
            Set<String> regions,
            boolean silkTouch,
            boolean mature,
            boolean playerPlaced
    ) {
        ProgressionUpdate update =
                applyAction(
                        faction,
                        QuestAction.material(
                                type,
                                material,
                                data,
                                sparrowItemId,
                                amount,
                                world,
                                regions,
                                silkTouch,
                                mature,
                                playerPlaced
                        )
                );

        return update.getProgressedQuestIds()
                .size();
    }

    /**
     * Compatibilité API:
     * un BlockBreak historique est une action MINE.
     */
    public int progressBlockBreak(
            Faction faction,
            Material material,
            int amount
    ) {
        return progressBlockAction(
                faction,
                "MINE",
                material,
                0,
                null,
                amount,
                null,
                Collections.<String>
                        emptySet(),
                false,
                true,
                false
        );
    }

    public int progressEntityKill(
            Faction faction,
            EntityType entityType,
            boolean witherSkeleton,
            int amount
    ) {
        String type =
                entityType == EntityType.PLAYER
                        ? "PLAYER_KILL"
                        : "MOB_KILL";

        QuestAction action =
                entityType == EntityType.PLAYER
                        ? QuestAction.playerKill(
                                amount
                        )
                        : QuestAction.entity(
                                type,
                                entityType,
                                amount
                        );

        return applyAction(
                faction,
                action
        ).getProgressedQuestIds()
                .size();
    }

    public int progressItemSmelt(
            Faction faction,
            Material result,
            int amount
    ) {
        return applyAction(
                faction,
                QuestAction.material(
                        "SMELT",
                        result,
                        0,
                        null,
                        amount
                )
        ).getProgressedQuestIds()
                .size();
    }

    public int progressItemSell(
            Faction faction,
            Material material,
            int amount
    ) {
        return progressItemSell(
                faction,
                material,
                null,
                amount
        );
    }

    public int progressItemSell(
            Faction faction,
            Material material,
            String sparrowItemId,
            int amount
    ) {
        return applyAction(
                faction,
                QuestAction.material(
                        "SELL",
                        material,
                        0,
                        sparrowItemId,
                        amount
                )
        ).getProgressedQuestIds()
                .size();
    }

    public void onItemSell(
            Player player,
            Material material,
            int amount
    ) {
        onItemSell(
                player,
                material,
                null,
                amount
        );
    }

    public void onItemSell(
            Player player,
            Material material,
            String sparrowItemId,
            int amount
    ) {
        if (player == null) {
            return;
        }

        Faction faction =
                plugin.getFactionManager()
                        .getPlayerFaction(
                                player
                        );

        if (faction != null) {
            ProgressionUpdate update =
                    applyAction(
                            faction,
                            QuestAction.material(
                                    "SELL",
                                    material,
                                    0,
                                    sparrowItemId,
                                    amount
                            ),
                            OperationContext.actor(
                                    player.getUniqueId(),
                                    player.getName(),
                                    OperationSource.INTEGRATION
                            )
                    );

            if (!update.hasProgress()) {
                return;
            }
        }
    }

    @Deprecated
    public void selectCategory(
            Faction faction,
            QuestCategory ignored
    ) {
        warnLegacyCommand(
                "selectCategory"
        );
    }

    @Deprecated
    public void assignRandomQuests(
            Faction faction,
            QuestCategory ignored
    ) {
        warnLegacyCommand(
                "assignRandomQuests"
        );
    }

    /**
     * Nombre de définitions fixes chargées.
     */
    public int getActiveQuestsCount() {
        ProgressionConfig config =
                activeConfig;

        return config != null
                ? countQuests(config)
                : 0;
    }

    // ============================================================
    // Health / recovery
    // ============================================================

    private boolean ensureReadable(
            Faction faction,
            ProgressionConfig config
    ) {
        if (faction == null
                || faction.isSystemFaction()
                || config == null
                || !config.isEnabled()) {
            return false;
        }

        synchronized (lockFor(faction)) {
            FactionProgressState state =
                    faction.getProgressionState();

            if (state.getSchemaVersion()
                    < FactionProgressState
                            .CURRENT_SCHEMA_VERSION) {
                ProgressionMigrationService.Result result =
                        migrationService.migrate(
                                faction,
                                config
                        );

                if (result
                        != ProgressionMigrationService.Result.MIGRATED
                        && result
                        != ProgressionMigrationService.Result.SKIPPED) {
                    return false;
                }
            }

            /*
             * Réconciliation 100% sûre:
             * si appliedRewards contient déjà la clé durable, le reward ne
             * doit jamais être rejoué et son pending peut être nettoyé.
             */
            reconcileDurablyAppliedPending(
                    faction,
                    true
            );

            if (state.getLevelStarted()
                    != faction.getLevel()) {
                if (warnedMismatch.add(
                        faction.getId()
                )) {
                    plugin.getLogger().severe(
                            "Progression bloquée pour "
                                    + faction.getName()
                                    + ": niveau faction="
                                    + faction.getLevel()
                                    + ", état progression="
                                    + state.getLevelStarted()
                                    + "."
                    );

                    audit(
                            OperationContext.system(),
                            faction,
                            "STATE_BLOCKED",
                            AuditOutcome.FAILED,
                            "reason=level-mismatch;factionLevel="
                                    + faction.getLevel()
                                    + ";levelStarted="
                                    + state.getLevelStarted()
                    );

                    firePost(
                            Action.STATE_BLOCKED,
                            faction,
                            faction.getLevel(),
                            faction.getLevel(),
                            null,
                            "LEVEL_MISMATCH",
                            OperationContext.system()
                    );
                }

                return false;
            }

            warnedMismatch.remove(
                    faction.getId()
            );

            return true;
        }
    }

    private boolean ensureHealthyForMutation(
            Faction faction,
            ProgressionConfig config,
            OperationContext context
    ) {
        if (!ensureReadable(
                faction,
                config
        )) {
            return false;
        }

        FactionProgressState state =
                faction.getProgressionState();

        if (!state.getPendingRewards()
                .isEmpty()) {
            if (warnedPending.add(
                    faction.getId()
            )) {
                plugin.getLogger().severe(
                        "Progression suspendue pour "
                                + faction.getName()
                                + ": récompenses pending "
                                + state.getPendingRewards()
                                + ". Aucun rejeu automatique."
                );

                audit(
                        context != null
                                ? context
                                : OperationContext.system(),
                        faction,
                        "STATE_BLOCKED",
                        AuditOutcome.DENIED,
                        "reason=pending-reward;count="
                                + state.getPendingRewards()
                                        .size()
                );

                firePost(
                        Action.STATE_BLOCKED,
                        faction,
                        faction.getLevel(),
                        faction.getLevel(),
                        null,
                        "PENDING_REWARD",
                        context != null
                                ? context
                                : OperationContext.system()
                );
            }

            return false;
        }

        warnedPending.remove(
                faction.getId()
        );

        return true;
    }

    private int reconcileDurablyAppliedPending(
            Faction faction,
            boolean appliedRewardsAreDurable
    ) {
        FactionProgressState state =
                faction.getProgressionState();

        if (state.getPendingRewards()
                .isEmpty()) {
            return 0;
        }

        Map<String, FactionProgressState.PendingRewardRecord> records =
                state.snapshotPendingRewardRecords();

        List<String> clear =
                new ArrayList<String>();

        for (String key
                : state.getPendingRewards()) {
            if (!faction.hasAppliedReward(key)) {
                continue;
            }

            FactionProgressState.PendingRewardRecord record =
                    records.get(key);

            /*
             * ACK_FAILED + appliedRewards seulement en RAM n'est PAS une preuve
             * durable. On garde donc le blocage jusqu'à un save réussi ou un
             * prochain chargement depuis disque.
             */
            if (!appliedRewardsAreDurable
                    && record != null
                    && record.getStatus()
                            == PendingRewardStatus.ACK_FAILED) {
                continue;
            }

            clear.add(key);
        }

        if (clear.isEmpty()) {
            return 0;
        }

        for (String key : clear) {
            state.removePendingReward(key);
        }

        if (state.getPendingRewards()
                .isEmpty()) {
            state.setPendingTransition(null);
        }

        plugin.getStorageManager()
                .markDirty(faction);

        plugin.getLogger().info(
                "Progression: "
                        + clear.size()
                        + " pending récompense(s) réconciliée(s) pour "
                        + faction.getName()
                        + " grâce à appliedRewards durable."
        );

        return clear.size();
    }

    // ============================================================
    // Validation
    // ============================================================

    private void validateLiveCompatibility(
            ProgressionConfig candidate,
            List<ValidationIssue> issues
    ) {
        if (plugin.getFactionManager() == null) {
            return;
        }

        for (Faction faction
                : plugin.getFactionManager()
                        .getAllFactions()) {
            if (faction == null
                    || faction.isSystemFaction()) {
                continue;
            }

            FactionProgressState state =
                    faction.getProgressionState();

            if (state.getSchemaVersion()
                    < 2) {
                continue;
            }

            LevelDefinition level =
                    candidate.getLevel(
                            faction.getLevel()
                    );

            String tierId =
                    state.getLockedTierId();

            if (level == null) {
                issues.add(
                        new ValidationIssue(
                                ValidationIssue.Severity.ERROR,
                                "progression.yml.levels."
                                        + faction.getLevel(),
                                "niveau utilisé par la faction "
                                        + faction.getName()
                                        + " mais absent du candidat."
                        )
                );

            } else if (tierId == null
                    || candidate.getTier(tierId)
                            == null
                    || level.getTier(tierId)
                            == null) {
                issues.add(
                        new ValidationIssue(
                                ValidationIssue.Severity.ERROR,
                                "progression.yml.levels."
                                        + faction.getLevel()
                                        + ".tiers."
                                        + tierId,
                                "tranche verrouillée utilisée par la faction "
                                        + faction.getName()
                                        + " mais absente du candidat."
                        )
                );
            }
        }
    }

    private ValidationEnvironment
            runtimeValidationEnvironment() {
        return new ValidationEnvironment() {

            @Override
            public Status worldExists(
                    String world
            ) {
                return Bukkit.getWorld(world) == null
                        ? Status.INVALID
                        : Status.VALID;
            }

            @Override
            public Status regionExists(
                    String world,
                    String region
            ) {
                return Status.UNKNOWN;
            }

            @Override
            public Status customItemExists(
                    String itemId
            ) {
                return Status.UNKNOWN;
            }

            @Override
            public Status kcraftRecipeExists(
                    String recipeId
            ) {
                return Status.UNKNOWN;
            }
        };
    }

    // ============================================================
    // Diagnostics / metrics
    // ============================================================

    public long getActionsProcessed() {
        return actionsProcessed.sum();
    }

    public long getQuestCompletions() {
        return questCompletions.sum();
    }

    public long getLevelUps() {
        return levelUps.sum();
    }

    public long getBlockedMutations() {
        return blockedMutations.sum();
    }

    // ============================================================
    // Helpers
    // ============================================================

    private Object lockFor(
            Faction faction
    ) {
        Object created =
                new Object();

        Object existing =
                factionLocks.putIfAbsent(
                        faction.getId(),
                        created
                );

        return existing == null
                ? created
                : existing;
    }

    private int memberCount(
            Faction faction
    ) {
        return Math.max(
                1,
                faction.getMemberCount()
        );
    }

    private String rewardKey(
            int level,
            RewardDefinition reward
    ) {
        return "level_"
                + level
                + "_reward_"
                + reward.getId();
    }

    private boolean hasErrors(
            List<ValidationIssue> issues
    ) {
        for (ValidationIssue issue : issues) {
            if (issue.getSeverity()
                    == ValidationIssue.Severity.ERROR) {
                return true;
            }
        }

        return false;
    }

    private int countErrors(
            List<ValidationIssue> issues
    ) {
        int count = 0;

        for (ValidationIssue issue : issues) {
            if (issue.getSeverity()
                    == ValidationIssue.Severity.ERROR) {
                count++;
            }
        }

        return count;
    }

    private void logValidationIssues(
            List<ValidationIssue> issues
    ) {
        for (ValidationIssue issue : issues) {
            String message =
                    "[progression] "
                            + issue;

            if (issue.getSeverity()
                    == ValidationIssue.Severity.ERROR) {
                plugin.getLogger()
                        .severe(message);
            } else {
                plugin.getLogger()
                        .warning(message);
            }
        }
    }

    private int countQuests(
            ProgressionConfig config
    ) {
        int count = 0;

        for (LevelDefinition level
                : config.getLevels()
                        .values()) {
            for (me.krunsh.kfaction.progression.TierLevelDefinition tier
                    : level.getTiers()
                            .values()) {
                count +=
                        tier.getQuests()
                                .size();
            }
        }

        return count;
    }

    private void warnLegacyCommand(
            String operation
    ) {
        if (warnedLegacyCommands.add(
                operation
        )) {
            plugin.getLogger().warning(
                    "Opération legacy "
                            + operation
                            + " ignorée: catégories choisies et rerolls ont été supprimés "
                            + "du gameplay progression V2."
            );
        }
    }

    private void audit(
            OperationContext context,
            Faction faction,
            String action,
            AuditOutcome outcome,
            String details
    ) {
        if (plugin.getLogManager() == null) {
            return;
        }

        plugin.getLogManager()
                .audit(
                        context != null
                                ? context
                                : OperationContext.system(),
                        AuditCategory.PROGRESSION,
                        action,
                        outcome,
                        faction != null
                                ? faction.getId()
                                : null,
                        null,
                        null,
                        details
                );
    }

    private void firePost(
            Action action,
            Faction faction,
            int oldLevel,
            int newLevel,
            String identifier,
            String detail,
            OperationContext context
    ) {
        Bukkit.getPluginManager()
                .callEvent(
                        new FactionProgressionEvent(
                                Phase.POST,
                                action,
                                faction,
                                oldLevel,
                                newLevel,
                                identifier,
                                detail,
                                context != null
                                        ? context
                                        : OperationContext.system()
                        )
                );
    }
}
