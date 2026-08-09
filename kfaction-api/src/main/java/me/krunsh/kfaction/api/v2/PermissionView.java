package me.krunsh.kfaction.api.v2;

/**
 * Décision de permission explicable, indépendante du modèle interne.
 */
public final class PermissionView {

    private final boolean allowed;
    private final String reason;
    private final String territoryFactionId;
    private final String role;
    private final String relation;

    public PermissionView(
            boolean allowed,
            String reason,
            String territoryFactionId,
            String role,
            String relation
    ) {
        this.allowed = allowed;
        this.reason = reason;
        this.territoryFactionId = territoryFactionId;
        this.role = role;
        this.relation = relation;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public boolean isDenied() {
        return !allowed;
    }

    public String getReason() {
        return reason;
    }

    public String getTerritoryFactionId() {
        return territoryFactionId;
    }

    public String getRole() {
        return role;
    }

    public String getRelation() {
        return relation;
    }
}
