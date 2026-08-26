package me.krunsh.kfaction.resources;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

public class BundledResourceContractTest {

    @Test
    public void progressionYmlIsBundledAsActiveDefault() {
        ClassLoader loader =
                Thread.currentThread()
                        .getContextClassLoader();

        assertNotNull(
                loader.getResource(
                        "progression.yml"
                )
        );
    }

    @Test
    public void legacyProgressionResourcesAreNotBundled() {
        ClassLoader loader =
                Thread.currentThread()
                        .getContextClassLoader();

        assertNull(
                loader.getResource(
                        "progression.example.yml"
                )
        );

        assertNull(
                loader.getResource(
                        "levels.yml"
                )
        );

        assertNull(
                loader.getResource(
                        "quests.yml"
                )
        );
    }

    @Test
    public void displayDefaultsAreValidAndBundled() throws Exception {
        YamlConfiguration config = yaml("config.yml");
        YamlConfiguration messages = yaml("messages.yml");

        assertFalse(config.getBoolean("territory.use-titles", true));
        assertEquals(8, config.getInt("faction-show.names-per-line"));
        assertEquals(
                "&7~ Wilderness",
                messages.getString("territory.enter.wilderness")
        );
        assertNotNull(messages.getString("show.display.member-hover"));
    }

    private static YamlConfiguration yaml(String resource) throws Exception {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        InputStream stream = loader.getResourceAsStream(resource);
        assertNotNull(stream);

        try (InputStreamReader reader = new InputStreamReader(
                stream,
                StandardCharsets.UTF_8
        )) {
            return YamlConfiguration.loadConfiguration(reader);
        }
    }
}
