package me.krunsh.kfaction.diagnostics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Rapport immutable de diagnostic.
 */
public final class DiagnosticReport {

    private final DiagnosticScope scope;
    private final boolean full;
    private final long startedAt;
    private final long durationMillis;

    private final List<DiagnosticCheck> checks;

    public DiagnosticReport(
            DiagnosticScope scope,
            boolean full,
            long startedAt,
            long durationMillis,
            List<DiagnosticCheck> checks
    ) {
        this.scope =
                scope != null
                        ? scope
                        : DiagnosticScope.ALL;

        this.full = full;
        this.startedAt = Math.max(0L, startedAt);
        this.durationMillis = Math.max(0L, durationMillis);

        this.checks =
                Collections.unmodifiableList(
                        new ArrayList<DiagnosticCheck>(
                                checks != null
                                        ? checks
                                        : Collections.<DiagnosticCheck>
                                                emptyList()
                        )
                );
    }

    public DiagnosticScope getScope() {
        return scope;
    }

    public boolean isFull() {
        return full;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public List<DiagnosticCheck> getChecks() {
        return checks;
    }

    public int count(
            DiagnosticSeverity severity
    ) {
        int count = 0;

        for (DiagnosticCheck check : checks) {
            if (check != null
                    && check.getSeverity() == severity) {
                count++;
            }
        }

        return count;
    }

    public boolean hasErrors() {
        return count(
                DiagnosticSeverity.ERROR
        ) > 0;
    }

    public boolean hasWarnings() {
        return count(
                DiagnosticSeverity.WARNING
        ) > 0;
    }

    public DiagnosticSeverity getOverallSeverity() {
        DiagnosticSeverity result =
                DiagnosticSeverity.OK;

        for (DiagnosticCheck check : checks) {
            if (check != null
                    && check.getSeverity()
                            .getWeight()
                    > result.getWeight()) {
                result =
                        check.getSeverity();
            }
        }

        return result;
    }
}
