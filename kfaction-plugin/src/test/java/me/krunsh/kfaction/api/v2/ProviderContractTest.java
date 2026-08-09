package me.krunsh.kfaction.api.v2;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProviderContractTest {

    @Test
    public void providersImplementOnlyPublicApiContracts() {
        assertTrue(KfactionApiV2.class.isAssignableFrom(KfactionApiProvider.class));
        assertTrue(KfactionApiV23.class.isAssignableFrom(KfactionApiProvider.class));
        assertTrue(KfactionPlayerActions.class.isAssignableFrom(KfactionPlayerActionsProvider.class));
    }
}
