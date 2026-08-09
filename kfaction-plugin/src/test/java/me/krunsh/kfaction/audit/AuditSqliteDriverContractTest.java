package me.krunsh.kfaction.audit;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

public class AuditSqliteDriverContractTest {
    @Test
    public void shadedDriverIsLoadedThroughThePluginClassLoaderBeforeConnecting() throws Exception {
        Path source = Paths.get(System.getProperty("basedir"), "src", "main", "java", "me",
            "krunsh", "kfaction", "audit", "AuditStore.java");
        String code = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        int bootstrap = code.indexOf("ensureSqliteDriver();");
        int connection = code.indexOf("DriverManager.getConnection", bootstrap);
        assertTrue(bootstrap >= 0 && connection > bootstrap);
        assertTrue(code.contains("plugin.getClass().getClassLoader()"));
        assertTrue(code.contains("Class.forName("));
    }
}
