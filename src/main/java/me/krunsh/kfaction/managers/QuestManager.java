package me.krunsh.kfaction.managers;

import java.util.List;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.QuestCategory;
import me.krunsh.kfaction.progression.MemberTierDefinition;
import me.krunsh.kfaction.progression.ProgressionConfig;
import me.krunsh.kfaction.progression.ProgressionStatus;
import me.krunsh.kfaction.progression.ProgressionUpdate;
import me.krunsh.kfaction.progression.QuestAction;
import me.krunsh.kfaction.progression.QuestProgressView;
import me.krunsh.kfaction.progression.ValidationIssue;
import me.krunsh.kfaction.services.ProgressionService;

/**
 * Façade de compatibilité QuestManager.
 *
 * Toute la logique métier vit désormais dans ProgressionService.
 *
 * Les listeners/intégrations V1 peuvent continuer à utiliser getQuestManager()
 * sans connaître la nouvelle architecture.
 */
public final class QuestManager {

    private final ProgressionService service;

    public QuestManager(
            Kfaction plugin
    ) {
        this.service =
                new ProgressionService(
                        plugin
                );
    }

    public void initialize() {
        service.initialize();
    }

    public void shutdown() {
        service.shutdown();
    }

    public ProgressionService getService() {
        return service;
    }

    // ============================================================
    // Config / diagnostics
    // ============================================================

    public void loadConfig() {
        service.loadConfig();
    }

    public boolean reloadConfig(
            OperationContext context
    ) {
        return service.reloadConfig(context);
    }

    public void migrateLoadedFactions() {
        service.migrateLoadedFactions();
    }

    public boolean isEnabled() {
        return service.isEnabled();
    }

    public ProgressionConfig getActiveConfig() {
        return service.getActiveConfig();
    }

    public List<ValidationIssue>
            getLastValidationIssues() {
        return service.getLastValidationIssues();
    }

    public List<ValidationIssue> validateCandidate() {
        return service.validateCandidate();
    }

    public ProgressionStatus getStatus(
            Faction faction
    ) {
        return service.getStatus(faction);
    }

    // ============================================================
    // Views
    // ============================================================

    public List<QuestProgressView> getQuestViews(
            Faction faction
    ) {
        return service.getQuestViews(faction);
    }

    /**
     * Lecture API pure: aucune reconciliation/persistence implicite.
     */
    public ProgressionStatus peekStatus(
            Faction faction
    ) {
        return service.peekStatus(
                faction
        );
    }

    /**
     * Lecture API pure: aucune modification du tier verrouillé.
     */
    public List<QuestProgressView> peekQuestViews(
            Faction faction
    ) {
        return service.peekQuestViews(
                faction
        );
    }

    public MemberTierDefinition getCurrentTier(
            Faction faction
    ) {
        return service.getCurrentTier(faction);
    }

    // ============================================================
    // Core progression
    // ============================================================

    public ProgressionUpdate applyAction(
            Faction faction,
            QuestAction action
    ) {
        return service.applyAction(
                faction,
                action
        );
    }

    public ProgressionUpdate applyAction(
            Faction faction,
            QuestAction action,
            OperationContext context
    ) {
        return service.applyAction(
                faction,
                action,
                context
        );
    }

    public ProgressionUpdate applyActions(
            Faction faction,
            List<QuestAction> actions
    ) {
        return service.applyActions(
                faction,
                actions
        );
    }

    public ProgressionUpdate applyActions(
            Faction faction,
            List<QuestAction> actions,
            OperationContext context
    ) {
        return service.applyActions(
                faction,
                actions,
                context
        );
    }

    // ============================================================
    // Listener / integration compatibility
    // ============================================================

    public int progressEntityAction(
            Faction faction,
            String type,
            EntityType entityType,
            long amount,
            String world,
            Set<String> regions
    ) {
        return service.progressEntityAction(
                faction,
                type,
                entityType,
                amount,
                world,
                regions
        );
    }

    public int progressPlayerKill(
            Player killer,
            Player victim,
            String world,
            Set<String> regions,
            long timestampMillis
    ) {
        return service.progressPlayerKill(
                killer,
                victim,
                world,
                regions,
                timestampMillis
        );
    }

    public boolean shouldTrackPlayerPlaced(
            Material material,
            int data
    ) {
        return service.shouldTrackPlayerPlaced(
                material,
                data
        );
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
        return service.progressBlockAction(
                faction,
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
        );
    }

    public int progressBlockBreak(
            Faction faction,
            Material material,
            int amount
    ) {
        return service.progressBlockBreak(
                faction,
                material,
                amount
        );
    }

    public int progressEntityKill(
            Faction faction,
            EntityType entityType,
            boolean witherSkeleton,
            int amount
    ) {
        return service.progressEntityKill(
                faction,
                entityType,
                witherSkeleton,
                amount
        );
    }

    public int progressItemSmelt(
            Faction faction,
            Material result,
            int amount
    ) {
        return service.progressItemSmelt(
                faction,
                result,
                amount
        );
    }

    public int progressItemSell(
            Faction faction,
            Material material,
            int amount
    ) {
        return service.progressItemSell(
                faction,
                material,
                amount
        );
    }

    public int progressItemSell(
            Faction faction,
            Material material,
            String sparrowItemId,
            int amount
    ) {
        return service.progressItemSell(
                faction,
                material,
                sparrowItemId,
                amount
        );
    }

    public void onItemSell(
            Player player,
            Material material,
            int amount
    ) {
        service.onItemSell(
                player,
                material,
                amount
        );
    }

    public void onItemSell(
            Player player,
            Material material,
            String sparrowItemId,
            int amount
    ) {
        service.onItemSell(
                player,
                material,
                sparrowItemId,
                amount
        );
    }

    @Deprecated
    public void selectCategory(
            Faction faction,
            QuestCategory ignored
    ) {
        service.selectCategory(
                faction,
                ignored
        );
    }

    @Deprecated
    public void assignRandomQuests(
            Faction faction,
            QuestCategory ignored
    ) {
        service.assignRandomQuests(
                faction,
                ignored
        );
    }

    public int getActiveQuestsCount() {
        return service.getActiveQuestsCount();
    }
}
