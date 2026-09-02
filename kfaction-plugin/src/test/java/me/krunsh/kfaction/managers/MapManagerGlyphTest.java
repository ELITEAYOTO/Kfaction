package me.krunsh.kfaction.managers;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MapManagerGlyphTest {

    @Test
    public void keepsOnlyOneVisibleGlyph() {
        assertEquals(
                "■",
                MapManager.normalizeCellSymbol("&a■■", "?")
        );
    }

    @Test
    public void preservesConfiguredDirectionCase() {
        assertEquals(
                "v",
                MapManager.normalizeCellSymbol("vextra", "?")
        );
    }

    @Test
    public void usesFallbackForBlankOrColorOnlyValues() {
        assertEquals(
                "▲",
                MapManager.normalizeCellSymbol("&a", "▲")
        );

        assertEquals(
                "▲",
                MapManager.normalizeCellSymbol("   ", "▲")
        );
    }

    @Test
    public void alwaysReturnsASafeGlyph() {
        assertEquals(
                "■",
                MapManager.normalizeCellSymbol(null, null)
        );
    }

    @Test
    public void uniformModeIgnoresTerritorySpecificSymbols() {
        assertEquals(
                "■",
                MapManager.selectCellSymbol(true, "■", "FactionInitial")
        );
    }

    @Test
    public void legacyModePreservesTerritorySpecificSymbols() {
        assertEquals(
                "FactionInitial",
                MapManager.selectCellSymbol(false, "■", "FactionInitial")
        );
    }
}
