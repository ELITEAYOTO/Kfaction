package me.krunsh.kfaction.diagnostics;

/**
 * Niveau de gravité d'un contrôle /kf doctor.
 */
public enum DiagnosticSeverity {

    OK(0),
    INFO(0),
    WARNING(1),
    ERROR(2);

    private final int weight;

    DiagnosticSeverity(
            int weight
    ) {
        this.weight = weight;
    }

    public int getWeight() {
        return weight;
    }

    public boolean isProblem() {
        return this == WARNING
                || this == ERROR;
    }

    public boolean isError() {
        return this == ERROR;
    }
}
