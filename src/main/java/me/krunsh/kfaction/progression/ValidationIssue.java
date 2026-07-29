package me.krunsh.kfaction.progression;

/** Erreur ou avertissement de configuration avec chemin YAML complet. */
public final class ValidationIssue {
    public enum Severity { ERROR, WARNING }

    private final Severity severity;
    private final String path;
    private final String message;

    public ValidationIssue(Severity severity, String path, String message) {
        this.severity = severity;
        this.path = path == null ? "progression.yml" : path;
        this.message = message == null ? "" : message;
    }

    public Severity getSeverity() { return severity; }
    public String getPath() { return path; }
    public String getMessage() { return message; }

    @Override
    public String toString() {
        return path + ": " + message;
    }
}
