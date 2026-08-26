package me.krunsh.kfaction.managers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class TerritoryMessageFormatterTest {

    @Test
    public void replacesCanonicalRelationColorBeforeColorization() {
        String message =
                TerritoryMessageFormatter.formatFaction(
                        "{relation_color}~ {faction}",
                        "Test",
                        "Membre",
                        "&a",
                        "Description"
                );

        assertEquals("&a~ Test", message);
    }

    @Test
    public void acceptsLegacyAndMalformedPlaceholderAliases() {
        String[] templates = {
                "{color}~ {faction}",
                "<color>~ {faction}",
                "<relation_color>~ {faction}"
        };

        for (String template : templates) {
            String message =
                    TerritoryMessageFormatter.formatFaction(
                            template,
                            "Test",
                            "Ennemi",
                            "&c",
                            ""
                    );

            assertEquals("&c~ Test", message);
            assertFalse(message.contains("color"));
        }
    }

    @Test
    public void replacesAllFactionFieldsWithoutLeavingTokens() {
        String message =
                TerritoryMessageFormatter.formatFaction(
                        "{relation_color}{faction} {relation} {description}",
                        "Volkaria",
                        "Allié",
                        "&c",
                        "Description"
                );

        assertEquals(
                "&cVolkaria Allié Description",
                message
        );
        assertFalse(message.contains("{"));
        assertFalse(message.contains("<"));
    }
}
