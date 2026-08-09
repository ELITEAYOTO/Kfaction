package me.krunsh.kfaction.diagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class DiagnosticScopeTest {

    @Test
    public void parsesStableAliases() {
        assertEquals(
                DiagnosticScope.ALL,
                DiagnosticScope.parse(null)
        );

        assertEquals(
                DiagnosticScope.ALL,
                DiagnosticScope.parse("full")
        );

        assertEquals(
                DiagnosticScope.STORAGE,
                DiagnosticScope.parse("database")
        );

        assertEquals(
                DiagnosticScope.INTEGRATIONS,
                DiagnosticScope.parse("hooks")
        );

        assertEquals(
                DiagnosticScope.INDEXES,
                DiagnosticScope.parse("integrity")
        );

        assertEquals(
                DiagnosticScope.PROGRESSION,
                DiagnosticScope.parse("quests")
        );

        assertNull(
                DiagnosticScope.parse(
                        "unknown-scope"
                )
        );
    }
}
