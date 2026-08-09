package me.krunsh.kfaction.progression;

import me.krunsh.kfaction.data.FactionQuest;
import me.krunsh.kfaction.data.QuestType;

/** Correspondance pure et conservative des compteurs legacy vers une tranche v2. */
public final class LegacyProgressMapper {
    private LegacyProgressMapper() {}

    public static void apply(Iterable<FactionQuest> legacyQuests, int legacyXp,
            TierLevelDefinition active, FactionProgressState state) {
        if (legacyQuests != null) {
            for (FactionQuest legacy : legacyQuests) {
                QuestDefinition candidate = active.getQuests().get(legacy.getId());
                if (candidate != null
                        && canonicalType(legacy).equals(candidate.getType())) {
                    state.setProgress(candidate.getId(), legacy.getProgress());
                } else {
                    state.archiveLegacy(legacy.getId(), legacy.getProgress());
                }
            }
        }
        if (legacyXp > 0) state.archiveLegacy("current-xp", legacyXp);
    }

    static String canonicalType(FactionQuest legacy) {
        QuestType type = legacy.getType();
        if (type == QuestType.BLOCK_BREAK) return "MINE";
        if (type == QuestType.ITEM_SMELT) return "SMELT";
        if (type == QuestType.ITEM_SELL) return "SELL";
        if (type == QuestType.ENTITY_KILL) {
            return "PLAYER".equalsIgnoreCase(legacy.getTarget())
                    ? "PLAYER_KILL" : "MOB_KILL";
        }
        return type == null ? "" : type.name();
    }
}
