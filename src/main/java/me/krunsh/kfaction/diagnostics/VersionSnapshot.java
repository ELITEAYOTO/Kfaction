package me.krunsh.kfaction.diagnostics;

/**
 * Snapshot immutable des informations /kf version.
 */
public final class VersionSnapshot {

    private final String pluginVersion;
    private final String apiVersion;

    private final String javaVersion;
    private final String serverVersion;

    private final String storageType;
    private final boolean storageConnected;

    private final int storagePayloadSchema;
    private final int storageDatabaseSchema;

    private final String dataFolder;

    private final long uptimeMillis;

    private final long usedMemoryBytes;
    private final long maxMemoryBytes;

    public VersionSnapshot(
            String pluginVersion,
            String apiVersion,
            String javaVersion,
            String serverVersion,
            String storageType,
            boolean storageConnected,
            int storagePayloadSchema,
            int storageDatabaseSchema,
            String dataFolder,
            long uptimeMillis,
            long usedMemoryBytes,
            long maxMemoryBytes
    ) {
        this.pluginVersion = pluginVersion;
        this.apiVersion = apiVersion;
        this.javaVersion = javaVersion;
        this.serverVersion = serverVersion;
        this.storageType = storageType;
        this.storageConnected = storageConnected;
        this.storagePayloadSchema = storagePayloadSchema;
        this.storageDatabaseSchema = storageDatabaseSchema;
        this.dataFolder = dataFolder;
        this.uptimeMillis = Math.max(0L, uptimeMillis);
        this.usedMemoryBytes = Math.max(0L, usedMemoryBytes);
        this.maxMemoryBytes = Math.max(0L, maxMemoryBytes);
    }

    public String getPluginVersion() {
        return pluginVersion;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    public String getServerVersion() {
        return serverVersion;
    }

    public String getStorageType() {
        return storageType;
    }

    public boolean isStorageConnected() {
        return storageConnected;
    }

    public int getStoragePayloadSchema() {
        return storagePayloadSchema;
    }

    public int getStorageDatabaseSchema() {
        return storageDatabaseSchema;
    }

    public String getDataFolder() {
        return dataFolder;
    }

    public long getUptimeMillis() {
        return uptimeMillis;
    }

    public long getUsedMemoryBytes() {
        return usedMemoryBytes;
    }

    public long getMaxMemoryBytes() {
        return maxMemoryBytes;
    }
}
