package me.krunsh.kfaction.core.operation;

import java.util.Objects;
import java.util.UUID;

/**
 * Contexte immuable associé à une mutation du domaine Kfaction.
 *
 * Une opération conserve les mêmes métadonnées qu'elle provienne d'une
 * commande, de Kgui, de l'API, d'un outil staff ou d'une tâche interne.
 *
 * Aucun objet Bukkit n'est conservé dans cette classe.
 */
public final class OperationContext {

    private final UUID actorId;
    private final String actorName;
    private final OperationSource source;
    private final long createdAt;
    private final String correlationId;

    private OperationContext(
            UUID actorId,
            String actorName,
            OperationSource source,
            long createdAt,
            String correlationId
    ) {
        this.actorId = actorId;
        this.actorName = normalizeActorName(actorName);
        this.source = Objects.requireNonNull(source, "source");

        if (createdAt <= 0L) {
            throw new IllegalArgumentException(
                    "createdAt must be > 0"
            );
        }

        this.createdAt = createdAt;
        this.correlationId =
                requireText(
                        correlationId,
                        "correlationId"
                );
    }

    // ============================================================
    // Factories utilisateur / commande / API
    // ============================================================

    /**
     * Crée le contexte d'une action effectuée par un acteur identifié.
     */
    public static OperationContext actor(
            UUID actorId,
            String actorName,
            OperationSource source
    ) {
        Objects.requireNonNull(
                actorId,
                "actorId"
        );

        return create(
                actorId,
                actorName,
                source
        );
    }

    /**
     * Contexte d'une action admin effectuée par un joueur.
     */
    public static OperationContext admin(
            UUID actorId,
            String actorName
    ) {
        Objects.requireNonNull(
                actorId,
                "actorId"
        );

        return create(
                actorId,
                actorName,
                OperationSource.ADMIN
        );
    }

    /**
     * Contexte d'une action admin effectuée par la console ou un sender
     * sans UUID Bukkit.
     */
    public static OperationContext admin(
            String actorName
    ) {
        return create(
                null,
                actorName,
                OperationSource.ADMIN
        );
    }

    // ============================================================
    // Factories internes
    // ============================================================

    /**
     * Crée le contexte d'une opération interne à Kfaction.
     */
    public static OperationContext system() {
        return create(
                null,
                null,
                OperationSource.SYSTEM
        );
    }

    /**
     * Crée le contexte d'une tâche planifiée.
     */
    public static OperationContext task() {
        return create(
                null,
                null,
                OperationSource.TASK
        );
    }

    private static OperationContext create(
            UUID actorId,
            String actorName,
            OperationSource source
    ) {
        return new OperationContext(
                actorId,
                actorName,
                Objects.requireNonNull(
                        source,
                        "source"
                ),
                System.currentTimeMillis(),
                UUID.randomUUID().toString()
        );
    }

    /**
     * Factory complète pour tests, imports ou bridges externes.
     */
    public static OperationContext of(
            UUID actorId,
            String actorName,
            OperationSource source,
            long createdAt,
            String correlationId
    ) {
        return new OperationContext(
                actorId,
                actorName,
                source,
                createdAt,
                correlationId
        );
    }

    // ============================================================
    // Getters
    // ============================================================

    public UUID getActorId() {
        return actorId;
    }

    public String getActorName() {
        return actorName;
    }

    public OperationSource getSource() {
        return source;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public boolean hasActor() {
        return actorId != null;
    }

    public boolean hasActorName() {
        return actorName != null;
    }

    public boolean isSystemOperation() {
        return actorId == null
                && (source == OperationSource.SYSTEM
                || source == OperationSource.TASK);
    }

    // ============================================================
    // Helpers
    // ============================================================

    private static String normalizeActorName(
            String actorName
    ) {
        if (actorName == null) {
            return null;
        }

        String trimmed =
                actorName.trim();

        return trimmed.isEmpty()
                ? null
                : trimmed;
    }

    private static String requireText(
            String value,
            String fieldName
    ) {
        Objects.requireNonNull(
                value,
                fieldName
        );

        String trimmed =
                value.trim();

        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(
                    fieldName
                            + " cannot be empty"
            );
        }

        return trimmed;
    }

    @Override
    public String toString() {
        return "OperationContext{" +
                "actorId=" + actorId +
                ", actorName='" + actorName + '\'' +
                ", source=" + source +
                ", createdAt=" + createdAt +
                ", correlationId='" + correlationId + '\'' +
                '}';
    }
}