package me.krunsh.kfaction.progression;

/** Récompense de niveau avec identifiant stable pour l'idempotence. */
public final class RewardDefinition {
    private final String id;
    private final String type;
    private final Object value;
    private final String description;

    public RewardDefinition(String id, String type, Object value, String description) {
        this.id = id;
        this.type = type;
        this.value = value;
        this.description = description;
    }

    public String getId() { return id; }
    public String getType() { return type; }
    public Object getValue() { return value; }
    public String getDescription() { return description; }
}
