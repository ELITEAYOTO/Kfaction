package me.krunsh.kfaction.api.v2;

/** Relation d'une faction vers une autre. */
public final class RelationView {

    private final String factionId;
    private final String otherFactionId;
    private final String relation;

    public RelationView(String factionId, String otherFactionId, String relation) {
        if (factionId == null || otherFactionId == null || relation == null) {
            throw new IllegalArgumentException("relation fields are required");
        }
        this.factionId = factionId;
        this.otherFactionId = otherFactionId;
        this.relation = relation;
    }

    public String getFactionId() { return factionId; }
    public String getOtherFactionId() { return otherFactionId; }
    public String getRelation() { return relation; }
}
