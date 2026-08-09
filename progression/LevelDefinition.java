package me.krunsh.kfaction.progression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Niveau configurable et toutes ses variantes par tranche. */
public final class LevelDefinition {
    private final int number;
    private final String displayName;
    private final Map<String, TierLevelDefinition> tiers;
    private final List<RewardDefinition> rewardsOnEnter;

    public LevelDefinition(int number, String displayName,
            Map<String, TierLevelDefinition> tiers,
            List<RewardDefinition> rewardsOnEnter) {
        this.number = number;
        this.displayName = displayName;
        this.tiers = Collections.unmodifiableMap(
                new LinkedHashMap<String, TierLevelDefinition>(tiers));
        this.rewardsOnEnter = Collections.unmodifiableList(
                new ArrayList<RewardDefinition>(rewardsOnEnter));
    }

    public int getNumber() { return number; }
    public String getDisplayName() { return displayName; }
    public Map<String, TierLevelDefinition> getTiers() { return tiers; }
    public TierLevelDefinition getTier(String tierId) { return tiers.get(tierId); }
    public List<RewardDefinition> getRewardsOnEnter() { return rewardsOnEnter; }
}
