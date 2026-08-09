package me.krunsh.kfaction.hooks;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.Test;

import me.krunsh.kfaction.api.KfactionAPI;
import me.krunsh.kfaction.api.v2.KfactionApiV2;
import me.krunsh.kfaction.api.v2.KfactionApis;
import me.krunsh.kfaction.hooks.IntegrationState.Status;

/**
 * Contrats stables utilisés par Kgui et les plugins de l'écosystème.
 */
public class IntegrationCompatibilityContractTest {

    @Test
    public void kfactionDoesNotOwnTheKguiIntegration() throws Exception {
        InputStream stream = getClass().getResourceAsStream("/plugin.yml");
        assertNotNull(stream);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = stream.read(buffer)) >= 0) bytes.write(buffer, 0, read);
        stream.close();
        String pluginYml = new String(bytes.toByteArray(), "UTF-8");
        assertFalse(pluginYml.contains("  - Kgui"));

        assertClassAbsent("me.krunsh.kfaction.hooks.KguiHook");
        assertClassAbsent("me.krunsh.kfaction.hooks.KguiContentProviders");
    }

    @Test
    public void integrationStatusesRemainStable() {
        assertArrayEquals(
                new Status[] {
                        Status.MISSING,
                        Status.STARTING,
                        Status.ACTIVE,
                        Status.FAILED,
                        Status.DISABLED
                },
                Status.values()
        );
    }

    @Test
    public void luckPermsContextKeysRemainStable() {
        assertEquals(
                "kfaction:has-faction",
                LuckPermsHook.CONTEXT_HAS_FACTION
        );

        assertEquals(
                "kfaction:faction",
                LuckPermsHook.CONTEXT_FACTION
        );

        assertEquals(
                "kfaction:faction-tag",
                LuckPermsHook.CONTEXT_FACTION_TAG
        );

        assertEquals(
                "kfaction:role",
                LuckPermsHook.CONTEXT_ROLE
        );
    }

    @Test
    public void legacyApiKeepsV2Bridge() throws Exception {
        Method v2 =
                KfactionAPI.class
                        .getMethod("v2");

        Method getV2 =
                KfactionAPI.class
                        .getMethod("getV2");

        assertEquals(
                KfactionApiV2.class,
                v2.getReturnType()
        );

        assertEquals(
                KfactionApiV2.class,
                getV2.getReturnType()
        );
    }

    @Test
    public void servicesManagerHelperContractRemainsAvailable()
            throws Exception {
        Method get =
                KfactionApis.class
                        .getMethod("get");

        Method available =
                KfactionApis.class
                        .getMethod("isAvailable");

        assertEquals(
                KfactionApiV2.class,
                get.getReturnType()
        );

        assertEquals(
                boolean.class,
                available.getReturnType()
        );

        assertTrue(
                Modifier.isStatic(
                        get.getModifiers()
                )
        );

        assertTrue(
                Modifier.isStatic(
                        available.getModifiers()
                )
        );

        assertNotNull(
                get
        );
    }

    private static void assertClassAbsent(String className) throws Exception {
        try {
            Class.forName(className);
        } catch (ClassNotFoundException expected) {
            return;
        }
        throw new AssertionError("Legacy Kgui bridge must stay absent: " + className);
    }
}
