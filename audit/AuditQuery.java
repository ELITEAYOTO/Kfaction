package me.krunsh.kfaction.audit;

import java.util.UUID;

/**
 * Filtres d'une recherche audit.
 */
public final class AuditQuery {

    private final String factionId;
    private final UUID playerId;
    private final AuditCategory category;
    private final String action;
    private final String correlationId;
    private final long sinceTimestamp;
    private final int limit;

    private AuditQuery(Builder builder) {
        this.factionId =
                normalize(builder.factionId);
        this.playerId = builder.playerId;
        this.category = builder.category;
        this.action =
                normalize(builder.action);
        this.correlationId =
                normalize(builder.correlationId);
        this.sinceTimestamp =
                Math.max(
                        0L,
                        builder.sinceTimestamp
                );
        this.limit =
                Math.max(
                        1,
                        builder.limit
                );
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getFactionId() {
        return factionId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public AuditCategory getCategory() {
        return category;
    }

    public String getAction() {
        return action;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public long getSinceTimestamp() {
        return sinceTimestamp;
    }

    public int getLimit() {
        return limit;
    }

    private static String normalize(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String trimmed =
                value.trim();

        return trimmed.isEmpty()
                ? null
                : trimmed;
    }

    public static final class Builder {

        private String factionId;
        private UUID playerId;
        private AuditCategory category;
        private String action;
        private String correlationId;
        private long sinceTimestamp;
        private int limit = 50;

        private Builder() {
        }

        public Builder factionId(
                String factionId
        ) {
            this.factionId = factionId;
            return this;
        }

        /**
         * Match actor_uuid OU target_uuid.
         */
        public Builder playerId(
                UUID playerId
        ) {
            this.playerId = playerId;
            return this;
        }

        public Builder category(
                AuditCategory category
        ) {
            this.category = category;
            return this;
        }

        public Builder action(
                String action
        ) {
            this.action = action;
            return this;
        }

        public Builder correlationId(
                String correlationId
        ) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder sinceTimestamp(
                long sinceTimestamp
        ) {
            this.sinceTimestamp = sinceTimestamp;
            return this;
        }

        public Builder limit(
                int limit
        ) {
            this.limit = limit;
            return this;
        }

        public AuditQuery build() {
            return new AuditQuery(this);
        }
    }
}
