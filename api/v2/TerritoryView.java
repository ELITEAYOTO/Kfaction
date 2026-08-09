package me.krunsh.kfaction.api.v2;

/**
 * Snapshot immutable d'un territoire.
 */
public final class TerritoryView {

    public enum Type {
        WILDERNESS,
        FACTION,
        SAFEZONE,
        WARZONE,
        GLOBAL_ZONE
    }

    private final ChunkView chunk;
    private final Type type;

    private final String factionId;
    private final String factionName;
    private final String factionTag;

    private final String relation;
    private final String claimGroupId;

    private final String zoneId;
    private final String zoneDisplayName;
    private final String zoneColor;
    private final String zoneMapSymbol;

    public TerritoryView(
            ChunkView chunk,
            Type type,
            String factionId,
            String factionName,
            String factionTag,
            String relation,
            String claimGroupId
    ) {
        this(
                chunk,
                type,
                factionId,
                factionName,
                factionTag,
                relation,
                claimGroupId,
                null,
                null,
                null,
                null
        );
    }

    public TerritoryView(
            ChunkView chunk,
            Type type,
            String factionId,
            String factionName,
            String factionTag,
            String relation,
            String claimGroupId,
            String zoneId,
            String zoneDisplayName,
            String zoneColor,
            String zoneMapSymbol
    ) {
        if (chunk == null || type == null) {
            throw new IllegalArgumentException(
                    "chunk/type cannot be null"
            );
        }

        this.chunk = chunk;
        this.type = type;
        this.factionId = factionId;
        this.factionName = factionName;
        this.factionTag = factionTag;
        this.relation = relation;
        this.claimGroupId = claimGroupId;
        this.zoneId = zoneId;
        this.zoneDisplayName = zoneDisplayName;
        this.zoneColor = zoneColor;
        this.zoneMapSymbol = zoneMapSymbol;
    }

    public ChunkView getChunk() {
        return chunk;
    }

    public Type getType() {
        return type;
    }

    public String getFactionId() {
        return factionId;
    }

    public String getFactionName() {
        return factionName;
    }

    public String getFactionTag() {
        return factionTag;
    }

    public String getRelation() {
        return relation;
    }

    public String getClaimGroupId() {
        return claimGroupId;
    }

    public String getZoneId() {
        return zoneId;
    }

    public String getZoneDisplayName() {
        return zoneDisplayName;
    }

    public String getZoneColor() {
        return zoneColor;
    }

    public String getZoneMapSymbol() {
        return zoneMapSymbol;
    }

    public boolean isGlobalZone() {
        return zoneId != null;
    }

    public boolean isWilderness() {
        return type == Type.WILDERNESS;
    }

    public boolean isFaction() {
        return type == Type.FACTION;
    }

    public boolean isSafezone() {
        return type == Type.SAFEZONE;
    }

    public boolean isWarzone() {
        return type == Type.WARZONE;
    }
}
