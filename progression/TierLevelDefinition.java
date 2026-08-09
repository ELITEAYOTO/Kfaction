package me.krunsh.kfaction.progression;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Quêtes obligatoires d'un niveau pour une tranche donnée. */
public final class TierLevelDefinition {
    private final String tierId;
    private final Map<String, QuestDefinition> quests;

    public TierLevelDefinition(String tierId, Map<String, QuestDefinition> quests) {
        this.tierId = tierId;
        this.quests = Collections.unmodifiableMap(
                new LinkedHashMap<String, QuestDefinition>(quests));
    }

    public String getTierId() { return tierId; }
    public Map<String, QuestDefinition> getQuests() { return quests; }
}
