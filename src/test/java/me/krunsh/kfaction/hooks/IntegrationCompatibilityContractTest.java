package me.krunsh.kfaction.hooks;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
}
