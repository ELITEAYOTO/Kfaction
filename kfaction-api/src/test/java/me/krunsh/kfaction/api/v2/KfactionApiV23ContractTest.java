package me.krunsh.kfaction.api.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;

import org.junit.Test;

public class KfactionApiV23ContractTest {

    @Test
    public void v23IsStrictlyAdditiveToFrozenV22() {
        assertEquals("2.2.0", KfactionApiV2.API_VERSION);
        assertEquals("2.3.0", KfactionApiV23.API_VERSION);
        assertTrue(KfactionApiV2.class.isAssignableFrom(KfactionApiV23.class));
        assertEquals(8, KfactionApiV23.class.getDeclaredMethods().length);
    }

    @Test
    public void publicSignaturesNeverExposePluginInternals() {
        assertSafe(KfactionApiV2.class);
        assertSafe(KfactionApiV23.class);
        assertSafe(KfactionPlayerActions.class);
    }

    private static void assertSafe(Class<?> contract) {
        for (Method method : contract.getDeclaredMethods()) {
            assertSafeType(method.getReturnType());
            for (Class<?> type : method.getParameterTypes()) assertSafeType(type);
        }
    }

    private static void assertSafeType(Class<?> type) {
        String name = type.getName();
        assertFalse(name, name.startsWith("me.krunsh.kfaction.managers."));
        assertFalse(name, name.startsWith("me.krunsh.kfaction.services."));
        assertFalse(name, name.equals("me.krunsh.kfaction.data.Faction"));
        assertFalse(name, name.equals("me.krunsh.kfaction.data.FPlayer"));
        assertFalse(name, name.equals("me.krunsh.kfaction.Kfaction"));
    }
}
