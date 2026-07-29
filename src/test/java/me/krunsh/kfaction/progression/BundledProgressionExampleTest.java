package me.krunsh.kfaction.progression;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.junit.Test;

public class BundledProgressionExampleTest {
    @Test
    public void bundledExampleAlwaysPassesTheStrictLoader() throws Exception {
        File candidate = File.createTempFile("kfaction-progression-example", ".yml");
        candidate.deleteOnExit();
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("progression.example.yml")) {
            assertTrue("progression.example.yml absent du JAR", input != null);
            Files.copy(input, candidate.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        ProgressionConfigLoader.LoadResult result =
                new ProgressionConfigLoader(QuestTypeRegistry.builtIns(),
                        ValidationEnvironment.PERMISSIVE, 50).load(candidate);

        assertTrue(result.getIssues().toString(), result.isValid());
        assertEquals(2, result.getConfig().getLevels().size());
        assertEquals(3, result.getConfig().getTiers().size());
    }
}
