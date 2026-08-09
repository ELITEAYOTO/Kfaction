package me.krunsh.kfaction.progression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Snapshot immuable d'une configuration progression.yml entièrement validée. */
public final class ProgressionConfig {
    private final int schemaVersion;
    private final boolean enabled;
    private final int startingLevel;
    private final boolean broadcastLevelUp;
    private final Map<String, MemberTierDefinition> tiers;
    private final List<MemberTierDefinition> tiersByRank;
    private final Map<String, CategoryDefinition> categories;
    private final Map<Integer, LevelDefinition> levels;

    public ProgressionConfig(int schemaVersion, boolean enabled, int startingLevel,
            boolean broadcastLevelUp, Map<String, MemberTierDefinition> tiers,
            Map<String, CategoryDefinition> categories,
            Map<Integer, LevelDefinition> levels) {
        this.schemaVersion = schemaVersion;
        this.enabled = enabled;
        this.startingLevel = startingLevel;
        this.broadcastLevelUp = broadcastLevelUp;
        this.tiers = Collections.unmodifiableMap(
                new LinkedHashMap<String, MemberTierDefinition>(tiers));
        this.tiersByRank = Collections.unmodifiableList(
                new ArrayList<MemberTierDefinition>(tiers.values()));
        this.categories = Collections.unmodifiableMap(
                new LinkedHashMap<String, CategoryDefinition>(categories));
        this.levels = Collections.unmodifiableMap(
                new LinkedHashMap<Integer, LevelDefinition>(levels));
    }

    public int getSchemaVersion() { return schemaVersion; }
    public boolean isEnabled() { return enabled; }
    public int getStartingLevel() { return startingLevel; }
    public boolean isBroadcastLevelUp() { return broadcastLevelUp; }
    public Map<String, MemberTierDefinition> getTiers() { return tiers; }
    public List<MemberTierDefinition> getTiersByRank() { return tiersByRank; }
    public Map<String, CategoryDefinition> getCategories() { return categories; }
    public Map<Integer, LevelDefinition> getLevels() { return levels; }
    public LevelDefinition getLevel(int level) { return levels.get(level); }
    public MemberTierDefinition getTier(String id) { return tiers.get(id); }

    public MemberTierDefinition findTier(int memberCount) {
        for (MemberTierDefinition tier : tiersByRank) {
            if (tier.contains(memberCount)) return tier;
        }
        return null;
    }

    public int getMaxLevel() {
        int max = 0;
        for (Integer level : levels.keySet()) if (level > max) max = level;
        return max;
    }
}
