package me.krunsh.kfaction.data;

import static org.junit.Assert.assertEquals;

import java.util.UUID;

import org.junit.Test;

public class FPlayerPowerContractTest {

    @Test
    public void legacyStorageSetterPreservesNegativePower() {
        FPlayer player =
                new FPlayer(
                        UUID.randomUUID()
                );

        player.setMaxPower(10.0D);
        player.setPower(-5.0D);

        assertEquals(
                -5.0D,
                player.getPower(),
                0.000001D
        );
    }

    @Test
    public void effectiveMaximumCanExceedPersistedBaseMaximum() {
        FPlayer player =
                new FPlayer(
                        UUID.randomUUID()
                );

        player.setMaxPower(10.0D);
        player.setPowerWithEffectiveMax(
                14.0D,
                -10.0D,
                15.0D
        );

        assertEquals(
                14.0D,
                player.getPower(),
                0.000001D
        );

        assertEquals(
                10.0D,
                player.getMaxPower(),
                0.000001D
        );
    }

    @Test
    public void effectiveSetterClampsBothBounds() {
        FPlayer player =
                new FPlayer(
                        UUID.randomUUID()
                );

        player.setPowerWithEffectiveMax(
                -99.0D,
                -10.0D,
                15.0D
        );

        assertEquals(
                -10.0D,
                player.getPower(),
                0.000001D
        );

        player.setPowerWithEffectiveMax(
                99.0D,
                -10.0D,
                15.0D
        );

        assertEquals(
                15.0D,
                player.getPower(),
                0.000001D
        );
    }
}
