package me.krunsh.kfaction.storage;

import java.util.Objects;

/**
 * Payload de persistance immuable.
 *
 * Le domaine vivant est sérialisé AVANT de quitter le thread principal.
 * Le writer asynchrone ne reçoit donc plus jamais Faction/FPlayer.
 */
public final class StorageSnapshot {

    public static final int CURRENT_SCHEMA_VERSION = 9;

    public enum EntityType {
        FACTION,
        FPLAYER,
        GLOBAL_ZONES,
        GRACE_STATE
    }

    private final EntityType entityType;
    private final String entityId;
    private final String payloadJson;
    private final long capturedAt;
    private final int schemaVersion;

    private StorageSnapshot(
            EntityType entityType,
            String entityId,
            String payloadJson,
            long capturedAt,
            int schemaVersion
    ) {
        this.entityType =
                Objects.requireNonNull(entityType, "entityType");
        this.entityId = requireText(entityId, "entityId");
        this.payloadJson = requireText(payloadJson, "payloadJson");

        if (capturedAt <= 0L) {
            throw new IllegalArgumentException(
                    "capturedAt must be > 0"
            );
        }

        if (schemaVersion <= 0) {
            throw new IllegalArgumentException(
                    "schemaVersion must be > 0"
            );
        }

        this.capturedAt = capturedAt;
        this.schemaVersion = schemaVersion;
    }

    public static StorageSnapshot faction(
            String factionId,
            String payloadJson
    ) {
        return new StorageSnapshot(
                EntityType.FACTION,
                factionId,
                payloadJson,
                System.currentTimeMillis(),
                CURRENT_SCHEMA_VERSION
        );
    }

    public static StorageSnapshot player(
            String playerId,
            String payloadJson
    ) {
        return new StorageSnapshot(
                EntityType.FPLAYER,
                playerId,
                payloadJson,
                System.currentTimeMillis(),
                CURRENT_SCHEMA_VERSION
        );
    }

    public static StorageSnapshot globalZones(
            String payloadJson
    ) {
        return new StorageSnapshot(
                EntityType.GLOBAL_ZONES,
                "global",
                payloadJson,
                System.currentTimeMillis(),
                CURRENT_SCHEMA_VERSION
        );
    }

    public static StorageSnapshot graceState(
            String payloadJson
    ) {
        return new StorageSnapshot(
                EntityType.GRACE_STATE,
                "global",
                payloadJson,
                System.currentTimeMillis(),
                CURRENT_SCHEMA_VERSION
        );
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public long getCapturedAt() {
        return capturedAt;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    private static String requireText(
            String value,
            String fieldName
    ) {
        Objects.requireNonNull(value, fieldName);

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(
                    fieldName + " cannot be empty"
            );
        }

        return value;
    }

    @Override
    public String toString() {
        return "StorageSnapshot{" +
                "entityType=" + entityType +
                ", entityId='" + entityId + '\'' +
                ", capturedAt=" + capturedAt +
                ", schemaVersion=" + schemaVersion +
                '}';
    }
}
