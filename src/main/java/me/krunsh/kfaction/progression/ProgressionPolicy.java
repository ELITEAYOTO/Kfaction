package me.krunsh.kfaction.progression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Règles pures de tranche, complétion et recalcul de niveau. */
public final class ProgressionPolicy {
    private ProgressionPolicy() {}

    public static MemberTierDefinition refreshLockedTier(ProgressionConfig config,
            FactionProgressState state, int currentMembers) {
        MemberTierDefinition current = config.findTier(currentMembers);
        if (current == null) return null;
        MemberTierDefinition locked = state.getLockedTierId() == null ? null
                : config.getTier(state.getLockedTierId());
        if (locked == null || current.getRank() > locked.getRank()) {
            state.lockTier(current);
            return current;
        }
        return locked;
    }

    public static List<QuestProgressView> views(ProgressionConfig config,
            FactionProgressState state, int level, int currentMembers) {
        MemberTierDefinition tier = refreshLockedTier(config, state, currentMembers);
        LevelDefinition levelDefinition = config.getLevel(level);
        if (tier == null || levelDefinition == null) return Collections.emptyList();
        TierLevelDefinition tierLevel = levelDefinition.getTier(tier.getId());
        if (tierLevel == null) return Collections.emptyList();
        List<QuestProgressView> result = new ArrayList<QuestProgressView>();
        for (QuestDefinition quest : tierLevel.getQuests().values()) {
            result.add(new QuestProgressView(quest, state.getProgress(quest.getId())));
        }
        return Collections.unmodifiableList(result);
    }

    public static boolean isComplete(ProgressionConfig config,
            FactionProgressState state, int level, int currentMembers) {
        List<QuestProgressView> views =
                views(config, state, level, currentMembers);
        if (views.isEmpty()) return false;
        for (QuestProgressView view : views) {
            if (!view.isCompleted()) return false;
        }
        return true;
    }

    public static void beginNextLevel(ProgressionConfig config,
            FactionProgressState state, int nextLevel, int currentMembers) {
        MemberTierDefinition recalculated = config.findTier(currentMembers);
        if (recalculated == null) {
            throw new IllegalArgumentException(
                    "Aucune tranche pour " + currentMembers + " membres.");
        }
        state.beginLevel(nextLevel, recalculated);
    }
}
