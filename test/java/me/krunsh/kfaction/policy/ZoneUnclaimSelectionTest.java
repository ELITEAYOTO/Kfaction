package me.krunsh.kfaction.policy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import me.krunsh.kfaction.data.FLocation;

public class ZoneUnclaimSelectionTest {

    @Test
    public void radiusStaysInWorldAndBounds() {
        FLocation center = new FLocation("world", 10, 10);
        List<FLocation> selected = ZoneUnclaimSelection.radius(Arrays.asList(
            new FLocation("world", 10, 10),
            new FLocation("world", 12, 8),
            new FLocation("world", 13, 10),
            new FLocation("other", 10, 10)), center, 2);

        assertEquals(2, selected.size());
        assertTrue(selected.contains(new FLocation("world", 10, 10)));
        assertTrue(selected.contains(new FLocation("world", 12, 8)));
    }

    @Test
    public void recognizesOnlySystemZoneTypes() {
        assertTrue(ZoneUnclaimSelection.isZoneType("WARZONE"));
        assertTrue(ZoneUnclaimSelection.isZoneType("safezone"));
    }
}
