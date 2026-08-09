package me.krunsh.kfaction.api.v2;

/** Résolution explicite d'une API absente, incompatible ou compatible. */
public final class KfactionApiCompatibility {

    public enum Status {
        ABSENT,
        INCOMPATIBLE,
        READY_2_2,
        READY_2_3
    }

    private final Status status;
    private final KfactionApiV2 api;

    private KfactionApiCompatibility(Status status, KfactionApiV2 api) {
        this.status = status;
        this.api = api;
    }

    public static KfactionApiCompatibility evaluate(KfactionApiV2 api) {
        if (api == null) return new KfactionApiCompatibility(Status.ABSENT, null);
        int major;
        String version;
        try {
            major = api.getApiMajor();
            version = api.getApiVersion();
        } catch (LinkageError | RuntimeException failure) {
            return new KfactionApiCompatibility(Status.INCOMPATIBLE, null);
        }
        if (major != KfactionApiV2.API_MAJOR || parseMajor(version) != KfactionApiV2.API_MAJOR) {
            return new KfactionApiCompatibility(Status.INCOMPATIBLE, null);
        }
        return api instanceof KfactionApiV23
                ? new KfactionApiCompatibility(Status.READY_2_3, api)
                : new KfactionApiCompatibility(Status.READY_2_2, api);
    }

    public Status getStatus() { return status; }
    public KfactionApiV2 getApi() { return api; }
    public boolean isReady() { return status == Status.READY_2_2 || status == Status.READY_2_3; }
    public boolean hasV23() { return status == Status.READY_2_3; }

    private static int parseMajor(String version) {
        if (version == null || version.trim().isEmpty()) return -1;
        int separator = version.indexOf('.');
        String major = separator < 0 ? version : version.substring(0, separator);
        try {
            return Integer.parseInt(major);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}
