package me.krunsh.kfaction.api.v2;

import static org.junit.Assert.assertSame;

import org.junit.Test;

public class ApiClassLoaderCompatibilityTest {

    @Test
    public void dependentChildResolvesTheParentsApiIdentity() throws Exception {
        ClassLoader parent = KfactionApiV2.class.getClassLoader();
        ClassLoader dependentPlugin = new ClassLoader(parent) { };
        Class<?> resolved = Class.forName(KfactionApiV2.class.getName(), true, dependentPlugin);
        assertSame(KfactionApiV2.class, resolved);
        assertSame(parent, resolved.getClassLoader());
    }
}
