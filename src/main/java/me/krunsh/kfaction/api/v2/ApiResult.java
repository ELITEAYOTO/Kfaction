package me.krunsh.kfaction.api.v2;

import me.krunsh.kfaction.core.operation.OperationResult;

/**
 * Résultat public et stable d'une mutation API.
 *
 * Aucune classe de service interne n'est exposée au consommateur.
 */
public final class ApiResult<T> {

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

    public ApiResult(
            Status status,
            T value,
            String messageKey,
            String detail
    ) {
        if (status == null) {
            throw new IllegalArgumentException(
                    "status cannot be null"
            );
        }

        this.status = status;
        this.value = value;
        this.messageKey = normalize(messageKey);
        this.detail = normalize(detail);
    }

    public static <T> ApiResult<T> from(
            OperationResult<?> source,
            T value
    ) {
        if (source == null) {
            return new ApiResult<T>(
                    Status.FAILED,
                    null,
                    "api.null-result",
                    "Internal operation returned null"
            );
        }

        Status mapped;

        try {
            mapped =
                    Status.valueOf(
                            source.getStatus()
                                    .name()
                    );
        } catch (IllegalArgumentException exception) {
            mapped = Status.FAILED;
        }

        return new ApiResult<T>(
                mapped,
                value,
                source.getMessageKey(),
                source.getDetail()
        );
    }

    public static <T> ApiResult<T> success(
            T value
    ) {
        return new ApiResult<T>(
                Status.SUCCESS,
                value,
                null,
                null
        );
    }

    public static <T> ApiResult<T> failure(
            Status status,
            String messageKey,
            String detail
    ) {
        if (status == null) {
            throw new IllegalArgumentException(
                    "status cannot be null"
            );
        }

        if (status == Status.SUCCESS
                || status == Status.NO_CHANGE) {
            throw new IllegalArgumentException(
                    "failure() cannot use status "
                            + status
            );
        }

        return new ApiResult<T>(
                status,
                null,
                messageKey,
                detail
        );
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

    public boolean hasValue() {
        return value != null;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean isSuccessful() {
        return status == Status.SUCCESS
                || status == Status.NO_CHANGE;
    }

    public boolean isFailure() {
        return !isSuccessful();
    }

    private static String normalize(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();

        return trimmed.isEmpty()
                ? null
                : trimmed;
    }

    @Override
    public String toString() {
        return "ApiResult{" +
                "status=" + status +
                ", value=" + value +
                ", messageKey='" + messageKey + '\'' +
                ", detail='" + detail + '\'' +
                '}';
    }
}
