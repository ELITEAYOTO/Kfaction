package me.krunsh.kfaction.resources;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

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
}
