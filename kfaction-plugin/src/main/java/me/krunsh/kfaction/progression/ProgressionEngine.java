package me.krunsh.kfaction.progression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Moteur pur qui applique une action à toutes les quêtes obligatoires compatibles. */
public final class ProgressionEngine {
    private ProgressionEngine() {}

    public static ProgressionUpdate apply(ProgressionConfig config,
            FactionProgressState state, int level, int memberCount,
            QuestAction action) {
        if (config == null || !config.isEnabled() || state == null
                || action == null || action.getAmount() <= 0L) {
            return ProgressionUpdate.NONE;
        }
        List<QuestProgressView> views =
                ProgressionPolicy.views(config, state, level, memberCount);
        if (views.isEmpty()) return ProgressionUpdate.NONE;

        List<String> progressed = new ArrayList<String>();
        List<String> completed = new ArrayList<String>();
        for (QuestProgressView view : views) {
            QuestDefinition quest = view.getDefinition();
            if (!sameType(quest.getType(), action.getType())
                    || !matches(quest.getTarget(), action)) {
                continue;
            }
            long before = state.getProgress(quest.getId());
            if (before >= quest.getAmount()) continue;
            if (!matchesConditions(state, quest, action)) continue;
            long after = state.addProgress(quest.getId(), action.getAmount());
            progressed.add(quest.getId());
            if (before < quest.getAmount() && after >= quest.getAmount()) {
                completed.add(quest.getId());
            }
        }
        if (progressed.isEmpty()) return ProgressionUpdate.NONE;
        return new ProgressionUpdate(progressed, completed,
                ProgressionPolicy.isComplete(config, state, level, memberCount));
    }

    private static boolean matchesConditions(FactionProgressState state,
            QuestDefinition quest,
            QuestAction action) {
        QuestConditions conditions = quest.getConditions();
        if (conditions == null) return true;
        if (!conditions.isCountPlayerPlacedBlocks() && action.isPlayerPlaced()) {
            return false;
        }
        if (conditions.isMatureOnly() && !action.isMature()) return false;
        if (!conditions.isAllowSilkTouch() && action.isSilkTouch()) return false;
        if (!conditions.getAllowedWorlds().isEmpty()
                && (action.getWorldName() == null
                || !conditions.getAllowedWorlds().contains(action.getWorldName()))) {
            return false;
        }
        if (!conditions.getBlockedRegions().isEmpty()
                && intersects(conditions.getBlockedRegions(), action.getRegionIds())) {
            return false;
        }
        if (!conditions.getAllowedRegions().isEmpty()
                && !intersects(conditions.getAllowedRegions(),
                        action.getRegionIds())) return false;
        if ("PLAYER_KILL".equals(quest.getType())) {
            if (action.getVictimId() == null) return false;
            if (conditions.isExcludeSameFaction()
                    && action.isSameFactionVictim()) return false;
            if (conditions.isExcludeAllies() && action.isAlliedVictim()) return false;
            if (conditions.isExcludeNpcs() && action.isNpcVictim()) return false;
            if (conditions.isExcludeSameIp() && action.isSameIpVictim()) return false;
            return state.tryRecordPvpKill(quest.getId(),
                    action.getVictimId().toString(), action.getTimestampMillis(),
                    conditions.getVictimCooldownSeconds(),
                    conditions.getMaxPerVictimPerDay());
        }
        return true;
    }

    private static boolean intersects(java.util.Set<String> expected,
            java.util.Set<String> actual) {
        for (String value : actual) if (expected.contains(value)) return true;
        return false;
    }

    private static boolean matches(QuestTarget target, QuestAction action) {
        if (target == null) return false;
        switch (target.getKind()) {
            case MATERIAL:
                return target.matchesMaterial(action.getMaterial(), action.getData(),
                        action.getSparrowItemId());
            case ENTITY:
                return target.matchesEntity(action.getEntityType());
            case STRING:
                return target.matchesString(action.getStringTarget());
            case NONE:
                return true;
            default:
                return false;
        }
    }

    private static boolean sameType(String expected, String actual) {
        if (expected == null || actual == null) return false;
        return expected.toUpperCase(Locale.ROOT)
                .equals(actual.toUpperCase(Locale.ROOT));
    }
}
