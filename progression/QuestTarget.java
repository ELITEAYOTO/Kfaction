package me.krunsh.kfaction.progression;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

/** Cible validée et typée d'une quête. */
public final class QuestTarget {
    public enum Kind { MATERIAL, ENTITY, STRING, NONE }

    private final Kind kind;
    private final String raw;
    private final Material material;
    private final int data;
    private final boolean dataSpecified;
    private final EntityType entityType;
    private final String sparrowItemId;
    private final String stringValue;

    private QuestTarget(Kind kind, String raw, Material material, int data,
            boolean dataSpecified, EntityType entityType, String sparrowItemId) {
        this.kind = kind;
        this.raw = raw;
        this.material = material;
        this.data = data;
        this.dataSpecified = dataSpecified;
        this.entityType = entityType;
        this.sparrowItemId = emptyToNull(sparrowItemId);
        this.stringValue = kind == Kind.STRING ? emptyToNull(raw) : null;
    }

    public static QuestTarget material(String raw, Material material, int data,
            boolean dataSpecified, String sparrowItemId) {
        return new QuestTarget(Kind.MATERIAL, raw, material, data, dataSpecified,
                null, sparrowItemId);
    }

    public static QuestTarget entity(String raw, EntityType entityType) {
        return new QuestTarget(Kind.ENTITY, raw, null, 0, false, entityType, null);
    }

    public static QuestTarget none() {
        return new QuestTarget(Kind.NONE, "*", null, 0, false, null, null);
    }

    public static QuestTarget string(String value) {
        return new QuestTarget(Kind.STRING, value, null, 0, false, null, null);
    }

    public Kind getKind() { return kind; }
    public String getRaw() { return raw; }
    public Material getMaterial() { return material; }
    public int getData() { return data; }
    public boolean isDataSpecified() { return dataSpecified; }
    public EntityType getEntityType() { return entityType; }
    public String getSparrowItemId() { return sparrowItemId; }
    public String getStringValue() { return stringValue; }

    public boolean matchesMaterial(Material actual, int actualData, String actualCit) {
        if (kind != Kind.MATERIAL || material != actual) return false;
        if (dataSpecified && data != actualData) return false;
        return sparrowItemId == null || sparrowItemId.equals(emptyToNull(actualCit));
    }

    public boolean matchesEntity(EntityType actual) {
        return kind == Kind.ENTITY && entityType == actual;
    }

    public boolean matchesString(String actual) {
        String normalized = emptyToNull(actual);
        return kind == Kind.STRING && stringValue != null
                && stringValue.equalsIgnoreCase(normalized);
    }

    private static String emptyToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
