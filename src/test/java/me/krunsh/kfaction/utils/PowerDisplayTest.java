package me.krunsh.kfaction.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PowerDisplayTest {
    @Test
    public void displaysWholePowerWithoutLocaleDecimalSeparator() {
        assertEquals("10", PowerDisplay.format(10.0));
        assertEquals("0", PowerDisplay.format(0.0));
        assertEquals("-2", PowerDisplay.format(-2.0));
    }

    @Test
    public void legacyFractionIsRoundedInsteadOfExposed() {
        assertEquals("10", PowerDisplay.format(10.1));
        assertEquals("11", PowerDisplay.format(10.6));
    }
}
