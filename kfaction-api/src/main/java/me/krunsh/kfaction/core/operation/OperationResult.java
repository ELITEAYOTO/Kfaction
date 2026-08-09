package me.krunsh.kfaction.core.operation;

import java.util.Objects;

/**
 * Résultat immuable d'une opération métier.
 *
 * Cette classe remplace progressivement les retours boolean ambigus.
 * Un service pourra ainsi indiquer :
 * - si l'action a réussi ;
 * - pourquoi elle a échoué ;
 * - quelle clé de message afficher ;
 * - éventuellement retourner une valeur métier.
 *
 * Les codes très spécifiques (claim, économie, warp, etc.) pourront ensuite
 * avoir leurs propres Result dédiés lorsque cela apporte de la valeur.
 */
public final class OperationResult<T> {

    public enum Status {
        SUCCESS,
        NO_CHANGE,
        CANCELLED,
        INVALID_INPUT,
        NOT_FOUND,
        FORBIDDEN,
        CONFLICT,
        LIMIT_REACHED,
        UNAVAILABLE,
        FAILED
    }

    private final Status status;
    private final T value;
    private final String messageKey;
    private final String detail;

    private OperationResult(
            Status status,
            T value,
            String messageKey,
            String detail
    ) {
        this.status = Objects.requireNonNull(status, "status");
        this.value = value;
        this.messageKey = normalize(messageKey);
        this.detail = normalize(detail);
    }

    public static <T> OperationResult<T> success(T value) {
        return new OperationResult<>(
                Status.SUCCESS,
                value,
                null,
                null
        );
    }

    public static OperationResult<Void> success() {
        return new OperationResult<>(
                Status.SUCCESS,
                null,
                null,
                null
        );
    }

    public static <T> OperationResult<T> noChange(String messageKey) {
        return new OperationResult<>(
                Status.NO_CHANGE,
                null,
                messageKey,
                null
        );
    }

    public static <T> OperationResult<T> failure(
            Status status,
            String messageKey
    ) {
        return failure(status, messageKey, null);
    }

    public static <T> OperationResult<T> failure(
            Status status,
            String messageKey,
            String detail
    ) {
        Objects.requireNonNull(status, "status");

        if (status == Status.SUCCESS || status == Status.NO_CHANGE) {
            throw new IllegalArgumentException(
                    "failure() cannot use status " + status
            );
        }

        return new OperationResult<>(
                status,
                null,
                messageKey,
                detail
        );
    }

    /**
     * SUCCESS uniquement.
     */
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    /**
     * SUCCESS ou NO_CHANGE.
     *
     * NO_CHANGE signifie que l'état final est acceptable mais qu'aucune
     * mutation n'était nécessaire.
     */
    public boolean isSuccessful() {
        return status == Status.SUCCESS || status == Status.NO_CHANGE;
    }

    public boolean isFailure() {
        return !isSuccessful();
    }

    public boolean hasValue() {
        return value != null;
    }

    public boolean hasMessageKey() {
        return messageKey != null;
    }

    public boolean hasDetail() {
        return detail != null;
    }

    public Status getStatus() {
        return status;
    }

    public T getValue() {
        return value;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public String getDetail() {
        return detail;
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
        return "OperationResult{" +
                "status=" + status +
                ", value=" + value +
                ", messageKey='" + messageKey + '\'' +
                ", detail='" + detail + '\'' +
                '}';
    }
}
