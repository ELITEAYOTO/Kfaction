package me.krunsh.kfaction.economy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class MoneyAmountHardeningTest {

    @Test
    public void decimalArithmeticRemainsExact() {
        MoneyAmount first =
                MoneyAmount.parse("10.10");

        MoneyAmount second =
                MoneyAmount.parse("0.20");

        assertEquals(
                1030L,
                first.plus(second)
                        .getMinorUnits()
        );

        assertEquals(
                "10.30",
                first.plus(second)
                        .toPlainString()
        );
    }

    @Test
    public void acceptsCommaButRejectsUnsafeFormats() {
        assertEquals(
                125L,
                MoneyAmount.parse("1,25")
                        .getMinorUnits()
        );

        assertInvalid("1.001");
        assertInvalid("-1");
        assertInvalid("1e3");
        assertInvalid("NaN");
        assertInvalid("Infinity");
        assertInvalid("");
    }

    @Test
    public void subtractionCannotBecomeNegative() {
        try {
            MoneyAmount.parse("1.00")
                    .minus(
                            MoneyAmount.parse(
                                    "2.00"
                            )
                    );

            fail("Expected ArithmeticException");

        } catch (ArithmeticException expected) {
            // expected
        }
    }

    private static void assertInvalid(
            String value
    ) {
        try {
            MoneyAmount.parse(value);
            fail(
                    "Expected invalid amount: "
                            + value
            );

        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
