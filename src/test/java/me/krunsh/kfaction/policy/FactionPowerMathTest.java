package me.krunsh.kfaction.policy;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FactionPowerMathTest {

    @Test
    public void aggregatesOneThenTwoMembers() {
        double total = FactionPowerMath.start(0.0);
        total = FactionPowerMath.addMember(total, 10.0);
        assertEquals(10.0, total, 0.0);
        total = FactionPowerMath.addMember(total, 10.0);
        assertEquals(20.0, total, 0.0);
    }

    @Test
    public void configuredBoostIsAddedOnlyOnce() {
        double total = FactionPowerMath.start(5.0);
        total = FactionPowerMath.addMember(total, 10.0);
        total = FactionPowerMath.addMember(total, 10.0);
        assertEquals(25.0, total, 0.0);
    }
}
