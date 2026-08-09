package me.krunsh.kfaction.progression;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Conditions génériques et protections anti-abus d'une quête. */
public final class QuestConditions {
    private final boolean countPlayerPlacedBlocks;
    private final boolean matureOnly;
    private final boolean allowSilkTouch;
    private final Set<String> allowedWorlds;
    private final Set<String> allowedRegions;
    private final Set<String> blockedRegions;
    private final int victimCooldownSeconds;
    private final int maxPerVictimPerDay;
    private final boolean excludeSameFaction;
    private final boolean excludeAllies;
    private final boolean excludeNpcs;
    private final boolean excludeSameIp;

    public QuestConditions(boolean countPlayerPlacedBlocks, boolean matureOnly,
            boolean allowSilkTouch, Set<String> allowedWorlds,
            Set<String> allowedRegions, Set<String> blockedRegions,
            int victimCooldownSeconds, int maxPerVictimPerDay,
            boolean excludeSameFaction, boolean excludeAllies,
            boolean excludeNpcs, boolean excludeSameIp) {
        this.countPlayerPlacedBlocks = countPlayerPlacedBlocks;
        this.matureOnly = matureOnly;
        this.allowSilkTouch = allowSilkTouch;
        this.allowedWorlds = immutableLowercaseSet(allowedWorlds);
        this.allowedRegions = immutableLowercaseSet(allowedRegions);
        this.blockedRegions = immutableLowercaseSet(blockedRegions);
        this.victimCooldownSeconds = victimCooldownSeconds;
        this.maxPerVictimPerDay = maxPerVictimPerDay;
        this.excludeSameFaction = excludeSameFaction;
        this.excludeAllies = excludeAllies;
        this.excludeNpcs = excludeNpcs;
        this.excludeSameIp = excludeSameIp;
    }

    public boolean isCountPlayerPlacedBlocks() { return countPlayerPlacedBlocks; }
    public boolean isMatureOnly() { return matureOnly; }
    public boolean isAllowSilkTouch() { return allowSilkTouch; }
    public Set<String> getAllowedWorlds() { return allowedWorlds; }
    public Set<String> getAllowedRegions() { return allowedRegions; }
    public Set<String> getBlockedRegions() { return blockedRegions; }
    public int getVictimCooldownSeconds() { return victimCooldownSeconds; }
    public int getMaxPerVictimPerDay() { return maxPerVictimPerDay; }
    public boolean isExcludeSameFaction() { return excludeSameFaction; }
    public boolean isExcludeAllies() { return excludeAllies; }
    public boolean isExcludeNpcs() { return excludeNpcs; }
    public boolean isExcludeSameIp() { return excludeSameIp; }

    private static Set<String> immutableLowercaseSet(Set<String> input) {
        LinkedHashSet<String> values = new LinkedHashSet<String>();
        for (String value : input) {
            if (value != null && !value.trim().isEmpty()) {
                values.add(value.trim().toLowerCase(java.util.Locale.ROOT));
            }
        }
        return Collections.unmodifiableSet(values);
    }
}
