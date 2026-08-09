package me.krunsh.kfaction.api.v2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Snapshot immutable d'une faction.
 *
 * Lot 21 ajoute les métriques d'intégration nécessaires à PlaceholderAPI,
 * Kchat/Kclassement et Kgui sans exposer Faction live.
 */
public final class FactionView {

    private final String id;
    private final String name;
    private final String tag;
    private final String description;

    private final UUID leader;

    private final List<MemberView> members;

    private final int onlineMemberCount;

    private final int claimCount;
    private final int maxClaims;
    private final int claimGroupCount;

    private final int warpCount;
    private final int maxWarps;

    private final int maxMembers;

    private final int allyCount;
    private final int enemyCount;
    private final int truceCount;

    private final double power;
    private final double maxPower;

    private final long bankMinor;
    private final double bankBalance;

    private final int level;

    private final boolean open;
    private final boolean permanent;
    private final boolean raidable;

    private final boolean chestUnlocked;
    private final boolean factionFlyEnabled;
    private final boolean antiSethomeEnabled;

    private final long createdAt;
    private final long lastActivity;

    /**
     * Constructeur Lot20 conservé pour compatibilité source.
     */
    public FactionView(
            String id,
            String name,
            String tag,
            String description,
            UUID leader,
            List<MemberView> members,
            int claimCount,
            int claimGroupCount,
            int warpCount,
            double power,
            double maxPower,
            long bankMinor,
            int level,
            boolean open,
            boolean permanent,
            long createdAt,
            long lastActivity
    ) {
        this(
                id,
                name,
                tag,
                description,
                leader,
                members,
                countOnline(members),
                claimCount,
                0,
                claimGroupCount,
                warpCount,
                0,
                0,
                0,
                0,
                0,
                power,
                maxPower,
                bankMinor,
                bankMinor / 100.0D,
                level,
                open,
                permanent,
                false,
                false,
                false,
                false,
                createdAt,
                lastActivity
        );
    }

    public FactionView(
            String id,
            String name,
            String tag,
            String description,
            UUID leader,
            List<MemberView> members,
            int onlineMemberCount,
            int claimCount,
            int maxClaims,
            int claimGroupCount,
            int warpCount,
            int maxWarps,
            int maxMembers,
            int allyCount,
            int enemyCount,
            int truceCount,
            double power,
            double maxPower,
            long bankMinor,
            double bankBalance,
            int level,
            boolean open,
            boolean permanent,
            boolean raidable,
            boolean chestUnlocked,
            boolean factionFlyEnabled,
            boolean antiSethomeEnabled,
            long createdAt,
            long lastActivity
    ) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.description = description;
        this.leader = leader;

        this.members =
                Collections.unmodifiableList(
                        new ArrayList<MemberView>(
                                members != null
                                        ? members
                                        : Collections.<MemberView>emptyList()
                        )
                );

        this.onlineMemberCount = Math.max(0, onlineMemberCount);
        this.claimCount = Math.max(0, claimCount);
        this.maxClaims = Math.max(0, maxClaims);
        this.claimGroupCount = Math.max(0, claimGroupCount);
        this.warpCount = Math.max(0, warpCount);
        this.maxWarps = Math.max(0, maxWarps);
        this.maxMembers = Math.max(0, maxMembers);
        this.allyCount = Math.max(0, allyCount);
        this.enemyCount = Math.max(0, enemyCount);
        this.truceCount = Math.max(0, truceCount);
        this.power = power;
        this.maxPower = maxPower;
        this.bankMinor = bankMinor;
        this.bankBalance = bankBalance;
        this.level = Math.max(0, level);
        this.open = open;
        this.permanent = permanent;
        this.raidable = raidable;
        this.chestUnlocked = chestUnlocked;
        this.factionFlyEnabled = factionFlyEnabled;
        this.antiSethomeEnabled = antiSethomeEnabled;
        this.createdAt = Math.max(0L, createdAt);
        this.lastActivity = Math.max(0L, lastActivity);
    }

    private static int countOnline(
            List<MemberView> members
    ) {
        if (members == null) {
            return 0;
        }

        int count = 0;

        for (MemberView member : members) {
            if (member != null && member.isOnline()) {
                count++;
            }
        }

        return count;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTag() {
        return tag;
    }

    public String getDescription() {
        return description;
    }

    public UUID getLeader() {
        return leader;
    }

    public List<MemberView> getMembers() {
        return members;
    }

    public int getMemberCount() {
        return members.size();
    }

    public int getOnlineMemberCount() {
        return onlineMemberCount;
    }

    public int getClaimCount() {
        return claimCount;
    }

    public int getMaxClaims() {
        return maxClaims;
    }

    public int getClaimGroupCount() {
        return claimGroupCount;
    }

    public int getWarpCount() {
        return warpCount;
    }

    public int getMaxWarps() {
        return maxWarps;
    }

    public int getMaxMembers() {
        return maxMembers;
    }

    public int getAllyCount() {
        return allyCount;
    }

    public int getEnemyCount() {
        return enemyCount;
    }

    public int getTruceCount() {
        return truceCount;
    }

    public double getPower() {
        return power;
    }

    public double getMaxPower() {
        return maxPower;
    }

    public long getBankMinor() {
        return bankMinor;
    }

    public double getBankBalance() {
        return bankBalance;
    }

    public int getLevel() {
        return level;
    }

    public boolean isOpen() {
        return open;
    }

    public boolean isPermanent() {
        return permanent;
    }

    public boolean isRaidable() {
        return raidable;
    }

    public boolean isChestUnlocked() {
        return chestUnlocked;
    }

    public boolean isFactionFlyEnabled() {
        return factionFlyEnabled;
    }

    public boolean isAntiSethomeEnabled() {
        return antiSethomeEnabled;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getLastActivity() {
        return lastActivity;
    }
}
