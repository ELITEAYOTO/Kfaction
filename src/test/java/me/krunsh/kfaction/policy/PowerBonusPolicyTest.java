package me.krunsh.kfaction.policy;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PowerBonusPolicyTest {

    @Test
    public void disabledPermissionBonusesKeepBasePower() {
        assertEquals(10.0, PowerBonusPolicy.apply(
                10.0, false, true, 2.0, true, 5.0, true, 10.0), 0.0);
    }

    @Test
    public void enabledPermissionBonusesAccumulateGrantedRanks() {
        assertEquals(17.0, PowerBonusPolicy.apply(
                10.0, true, true, 2.0, true, 5.0, false, 10.0), 0.0);
    }

    @Test
    public void enabledWithoutPermissionsKeepsBasePower() {
        assertEquals(10.0, PowerBonusPolicy.apply(
                10.0, true, false, 2.0, false, 5.0, false, 10.0), 0.0);
    }
}
