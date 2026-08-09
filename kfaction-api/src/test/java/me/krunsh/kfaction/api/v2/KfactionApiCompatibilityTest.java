package me.krunsh.kfaction.api.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.junit.Test;

public class KfactionApiCompatibilityTest {

    @Test
    public void absenceAndWrongMajorAreExplicit() {
        KfactionApiCompatibility absent = KfactionApiCompatibility.evaluate(null);
        assertEquals(KfactionApiCompatibility.Status.ABSENT, absent.getStatus());
        assertFalse(absent.isReady());

        KfactionApiV2 wrong = proxy(3);
        KfactionApiCompatibility incompatible = KfactionApiCompatibility.evaluate(wrong);
        assertEquals(KfactionApiCompatibility.Status.INCOMPATIBLE, incompatible.getStatus());
        assertFalse(incompatible.isReady());
    }

    @Test
    public void compatibleV22IsAcceptedWithoutPretendingV23() {
        KfactionApiCompatibility compatible = KfactionApiCompatibility.evaluate(proxy(2));
        assertEquals(KfactionApiCompatibility.Status.READY_2_2, compatible.getStatus());
        assertTrue(compatible.isReady());
        assertFalse(compatible.hasV23());
    }

    private static KfactionApiV2 proxy(final int major) {
        return (KfactionApiV2) Proxy.newProxyInstance(
                KfactionApiV2.class.getClassLoader(),
                new Class<?>[] { KfactionApiV2.class },
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        if ("getApiMajor".equals(method.getName())) return Integer.valueOf(major);
                        if ("getApiVersion".equals(method.getName())) return major + ".0.0";
                        if (method.getReturnType() == boolean.class) return Boolean.FALSE;
                        if (method.getReturnType() == int.class) return Integer.valueOf(0);
                        return null;
                    }
                });
    }
}
