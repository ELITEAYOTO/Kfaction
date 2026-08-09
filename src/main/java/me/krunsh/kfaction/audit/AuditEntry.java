package me.krunsh.kfaction.audit;

import java.util.UUID;

import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.core.operation.OperationSource;

/**
 * Entrée d'audit immuable.
 *
 * Aucun objet Bukkit vivant n'est conservé.
 */
public final class AuditEntry {

    private final String id;
    private final long timestamp;
    private final AuditCategory category;
    private final String action;
    private final AuditOutcome outcome;

    private final String factionId;

    private final UUID actorId;
    private final String actorName;

    private final UUID targetId;
    private final String targetName;

    private final OperationSource source;
    private final String correlationId;

    private final String details;

    public AuditEntry(
            String id,
            long timestamp,
            AuditCategory category,
            String action,
            AuditOutcome outcome,
            String factionId,
            UUID actorId,
            String actorName,
            UUID targetId,
            String targetName,
            OperationSource source,
            String correlationId,
            String details
    ) {
        this.id = requireText(id, "id");

        if (timestamp <= 0L) {
            throw new IllegalArgumentException(
                    "timestamp must be > 0"
            );
        }

        if (category == null
                || outcome == null
                || source == null) {
            throw new IllegalArgumentException(
                    "category/outcome/source cannot be null"
            );
        }

        this.timestamp = timestamp;
        this.category = category;
        this.action = normalizeRequired(
                action,
                96,
                "action"
        );
        this.outcome = outcome;
        this.factionId =
                normalizeNullable(
                        factionId,
                        128
                );
        this.actorId = actorId;
        this.actorName =
                normalizeNullable(
                        actorName,
                        96
                );
        this.targetId = targetId;
        this.targetName =
                normalizeNullable(
                        targetName,
                        96
                );
        this.source = source;
        this.correlationId =
                normalizeRequired(
                        correlationId,
                        128,
                        "correlationId"
                );
        this.details =
                normalizeNullable(
                        details,
                        4096
                );
    }

    public static AuditEntry create(
            AuditCategory category,
            String action,
            AuditOutcome outcome,
            String factionId,
            OperationContext context,
            UUID targetId,
            String targetName,
            String details
    ) {
        OperationContext safeContext =
                context != null
                        ? context
                        : OperationContext.system();

        return new AuditEntry(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                category,
                action,
                outcome,
                factionId,
                safeContext.getActorId(),
                safeContext.getActorName(),
                targetId,
                targetName,
                safeContext.getSource(),
                safeContext.getCorrelationId(),
                details
        );
    }

    public String getId() {
        return id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public AuditCategory getCategory() {
        return category;
    }

    public String getAction() {
        return action;
    }

    public AuditOutcome getOutcome() {
        return outcome;
    }

    public String getFactionId() {
        return factionId;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getActorName() {
        return actorName;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public String getTargetName() {
        return targetName;
    }

    public OperationSource getSource() {
        return source;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getDetails() {
        return details;
    }

    private static String requireText(
            String value,
            String name
    ) {
        if (value == null
                || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    name + " cannot be empty"
            );
        }

        return value.trim();
    }

    private static String normalizeRequired(
            String value,
            int max,
            String name
    ) {
        String normalized =
                normalizeNullable(
                        value,
                        max
                );

        if (normalized == null) {
            throw new IllegalArgumentException(
                    name + " cannot be empty"
            );
        }

        return normalized;
    }

    private static String normalizeNullable(
            String value,
            int max
    ) {
        if (value == null) {
            return null;
        }

        String normalized =
                value.trim();

        if (normalized.isEmpty()) {
            return null;
        }

        return normalized.length() > max
                ? normalized.substring(0, max)
                : normalized;
    }
}
