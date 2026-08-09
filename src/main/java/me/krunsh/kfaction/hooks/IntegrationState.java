package me.krunsh.kfaction.hooks;

/**
 * État immutable d'une intégration optionnelle.
 *
 * Utilisé maintenant par HookManager et plus tard par /kf doctor.
 */
public final class IntegrationState {

    public enum Status {
        MISSING,
        STARTING,
        ACTIVE,
        FAILED,
        DISABLED
    }

    private final String id;
    private final String pluginName;
    private final Status status;
    private final String detail;

    public IntegrationState(
            String id,
            String pluginName,
            Status status,
            String detail
    ) {
        if (id == null || pluginName == null || status == null) {
            throw new IllegalArgumentException(
                    "id/pluginName/status cannot be null"
            );
        }

        this.id = id;
        this.pluginName = pluginName;
        this.status = status;
        this.detail = normalize(detail);
    }

    public String getId() {
        return id;
    }

    public String getPluginName() {
        return pluginName;
    }

    public Status getStatus() {
        return status;
    }

    public String getDetail() {
        return detail;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public boolean isPresent() {
        return status != Status.MISSING;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Override
    public String toString() {
        return id + "=" + status
                + (detail != null ? "(" + detail + ")" : "");
    }
}
