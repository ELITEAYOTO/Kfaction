package me.krunsh.kfaction.diagnostics;

/**
 * Résultat immutable d'un contrôle individuel.
 */
public final class DiagnosticCheck {

    private final String id;
    private final DiagnosticSeverity severity;
    private final String title;
    private final String detail;
    private final String suggestion;

    public DiagnosticCheck(
            String id,
            DiagnosticSeverity severity,
            String title,
            String detail,
            String suggestion
    ) {
        if (id == null
                || severity == null
                || title == null) {
            throw new IllegalArgumentException(
                    "id/severity/title cannot be null"
            );
        }

        this.id = id;
        this.severity = severity;
        this.title = title;
        this.detail = normalize(detail);
        this.suggestion = normalize(suggestion);
    }

    public static DiagnosticCheck of(
            String id,
            DiagnosticSeverity severity,
            String title,
            String detail
    ) {
        return new DiagnosticCheck(
                id,
                severity,
                title,
                detail,
                null
        );
    }

    public static DiagnosticCheck problem(
            String id,
            DiagnosticSeverity severity,
            String title,
            String detail,
            String suggestion
    ) {
        return new DiagnosticCheck(
                id,
                severity,
                title,
                detail,
                suggestion
        );
    }

    public String getId() {
        return id;
    }

    public DiagnosticSeverity getSeverity() {
        return severity;
    }

    public String getTitle() {
        return title;
    }

    public String getDetail() {
        return detail;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public boolean hasDetail() {
        return detail != null;
    }

    public boolean hasSuggestion() {
        return suggestion != null;
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
}
