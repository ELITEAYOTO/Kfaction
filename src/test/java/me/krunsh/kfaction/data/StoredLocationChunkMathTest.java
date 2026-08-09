package me.krunsh.kfaction.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StoredLocationChunkMathTest {

    @Test
    public void negativeCoordinatesUseMinecraftFloorSemantics() {
        StoredLocation minusTiny =
                new StoredLocation(
                        "world",
                        -0.01D,
                        64.0D,
                        -0.01D,
                        0.0F,
                        0.0F
                );

        assertEquals(
                -1,
                minusTiny.getChunkX()
        );

        assertEquals(
                -1,
                minusTiny.getChunkZ()
        );

        StoredLocation minusSixteen =
                new StoredLocation(
                        "world",
                        -16.0D,
                        64.0D,
                        -16.0D,
                        0.0F,
                        0.0F
                );

        assertEquals(
                -1,
                minusSixteen.getChunkX()
        );

        assertEquals(
                -1,
                minusSixteen.getChunkZ()
        );

        StoredLocation belowMinusSixteen =
                new StoredLocation(
                        "world",
                        -16.01D,
                        64.0D,
                        -16.01D,
                        0.0F,
                        0.0F
                );

        assertEquals(
                -2,
                belowMinusSixteen.getChunkX()
        );

        assertEquals(
                -2,
                belowMinusSixteen.getChunkZ()
        );
    }
}
