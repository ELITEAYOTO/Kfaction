package me.krunsh.kfaction.policy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HomeClaimPolicyTest {

    @Test
    public void acceptsOnlyOwnClaimNormally() {
        assertTrue(HomeClaimPolicy.canSetHome("faction-a", "faction-a", false));
        assertFalse(HomeClaimPolicy.canSetHome("faction-a", "faction-b", false));
        assertFalse(HomeClaimPolicy.canSetHome("faction-a", "wilderness", false));
    }

    @Test
    public void explicitStaffBypassAllowsOutsideClaim() {
        assertTrue(HomeClaimPolicy.canSetHome("faction-a", "wilderness", true));
    }
}
