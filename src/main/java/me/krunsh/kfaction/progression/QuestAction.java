package me.krunsh.kfaction.progression;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

/**
 * Action de gameplay normalisée. Les listeners traduisent les événements Bukkit
 * vers ce modèle; le moteur de progression reste ainsi testable sans événement.
 */
public final class QuestAction {
    private final String type;
    private final Material material;
    private final int data;
    private final String sparrowItemId;
    private final EntityType entityType;
    private final long amount;
    private final String worldName;
    private final Set<String> regionIds;
    private final boolean silkTouch;
    private final boolean mature;
    private final boolean playerPlaced;
    private final UUID victimId;
    private final boolean sameFactionVictim;
    private final boolean alliedVictim;
    private final boolean npcVictim;
    private final boolean sameIpVictim;
    private final long timestampMillis;
    private final String stringTarget;

    private QuestAction(String type, Material material, int data,
            String sparrowItemId, EntityType entityType, long amount,
            String worldName, Set<String> regionIds, boolean silkTouch,
            boolean mature, boolean playerPlaced, UUID victimId,
            boolean sameFactionVictim, boolean alliedVictim,
            boolean npcVictim, boolean sameIpVictim, long timestampMillis) {
        this.type = type;
        this.material = material;
        this.data = data;
        this.sparrowItemId = normalize(sparrowItemId);
        this.entityType = entityType;
        this.amount = amount;
        this.worldName = normalizeLower(worldName);
        this.regionIds = normalizeSet(regionIds);
        this.silkTouch = silkTouch;
        this.mature = mature;
        this.playerPlaced = playerPlaced;
        this.victimId = victimId;
        this.sameFactionVictim = sameFactionVictim;
        this.alliedVictim = alliedVictim;
        this.npcVictim = npcVictim;
        this.sameIpVictim = sameIpVictim;
        this.timestampMillis = timestampMillis;
        this.stringTarget = null;
    }

    private QuestAction(String type, String stringTarget, long amount,
            String worldName, Set<String> regionIds) {
        this.type = type;
        this.material = null;
        this.data = 0;
        this.sparrowItemId = null;
        this.entityType = null;
        this.amount = amount;
        this.worldName = normalizeLower(worldName);
        this.regionIds = normalizeSet(regionIds);
        this.silkTouch = false;
        this.mature = true;
        this.playerPlaced = false;
        this.victimId = null;
        this.sameFactionVictim = false;
        this.alliedVictim = false;
        this.npcVictim = false;
        this.sameIpVictim = false;
        this.timestampMillis = 0L;
        this.stringTarget = normalize(stringTarget);
    }

    public static QuestAction material(String type, Material material, int data,
            String sparrowItemId, long amount) {
        return material(type, material, data, sparrowItemId, amount,
                null, Collections.<String>emptySet(), false, true, false);
    }

    public static QuestAction material(String type, Material material, int data,
            String sparrowItemId, long amount, String worldName,
            Set<String> regionIds, boolean silkTouch, boolean mature,
            boolean playerPlaced) {
        return new QuestAction(type, material, data, sparrowItemId, null, amount,
                worldName, regionIds, silkTouch, mature, playerPlaced,
                null, false, false, false, false, 0L);
    }

    public static QuestAction entity(String type, EntityType entityType, long amount) {
        return entity(type, entityType, amount, null,
                Collections.<String>emptySet());
    }

    public static QuestAction entity(String type, EntityType entityType, long amount,
            String worldName, Set<String> regionIds) {
        return new QuestAction(type, null, 0, null, entityType, amount,
                worldName, regionIds, false, true, false,
                null, false, false, false, false, 0L);
    }

    public static QuestAction playerKill(long amount) {
        return new QuestAction("PLAYER_KILL", null, 0, null, null, amount,
                null, Collections.<String>emptySet(), false, true, false,
                null, false, false, false, false, 0L);
    }

    public static QuestAction playerKill(long amount, String worldName,
            Set<String> regionIds, UUID victimId, boolean sameFactionVictim,
            boolean alliedVictim, boolean npcVictim, boolean sameIpVictim,
            long timestampMillis) {
        return new QuestAction("PLAYER_KILL", null, 0, null, null, amount,
                worldName, regionIds, false, true, false, victimId,
                sameFactionVictim, alliedVictim, npcVictim, sameIpVictim,
                timestampMillis);
    }

    public static QuestAction string(String type, String target, long amount,
            String worldName, Set<String> regionIds) {
        return new QuestAction(type, target, amount, worldName, regionIds);
    }

    public String getType() { return type; }
    public Material getMaterial() { return material; }
    public int getData() { return data; }
    public String getSparrowItemId() { return sparrowItemId; }
    public EntityType getEntityType() { return entityType; }
    public long getAmount() { return amount; }
    public String getWorldName() { return worldName; }
    public Set<String> getRegionIds() { return regionIds; }
    public boolean isSilkTouch() { return silkTouch; }
    public boolean isMature() { return mature; }
    public boolean isPlayerPlaced() { return playerPlaced; }
    public UUID getVictimId() { return victimId; }
    public boolean isSameFactionVictim() { return sameFactionVictim; }
    public boolean isAlliedVictim() { return alliedVictim; }
    public boolean isNpcVictim() { return npcVictim; }
    public boolean isSameIpVictim() { return sameIpVictim; }
    public long getTimestampMillis() { return timestampMillis; }
    public String getStringTarget() { return stringTarget; }

    private static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeLower(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static Set<String> normalizeSet(Set<String> values) {
        LinkedHashSet<String> normalized = new LinkedHashSet<String>();
        if (values != null) {
            for (String value : values) {
                String id = normalizeLower(value);
                if (id != null) normalized.add(id);
            }
        }
        return Collections.unmodifiableSet(normalized);
    }
}
