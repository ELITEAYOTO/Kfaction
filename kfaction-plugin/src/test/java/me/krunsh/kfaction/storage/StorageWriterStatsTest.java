package me.krunsh.kfaction.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StorageWriterStatsTest {

    @Test
    public void computesQueuePercentAndPendingDeletes() {
        StorageWriterStats stats =
                new StorageWriterStats(
                        75,
                        100,
                        20L,
                        2L,
                        18L,
                        0L,
                        3,
                        4
                );

        assertEquals(
                75,
                stats.getQueuePercent()
        );

        assertEquals(
                7,
                stats.getPendingDeleteCount()
        );

        assertTrue(
                stats.hasBackpressure()
        );
    }

    @Test
    public void zeroCapacityIsSafe() {
        StorageWriterStats stats =
                new StorageWriterStats(
                        10,
                        0,
                        0L,
                        0L,
                        0L,
                        0L,
                        0,
                        0
                );

        assertEquals(
                0,
                stats.getQueuePercent()
        );

        assertFalse(
                stats.hasBackpressure()
        );
    }
}
