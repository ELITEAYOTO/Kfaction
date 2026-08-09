package me.krunsh.kfaction.api.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;

import org.junit.Test;

public class PageContractTest {

    @Test
    public void pageLimitIsHardBounded() {
        assertInvalid(0);
        assertInvalid(PageRequest.MAX_LIMIT + 1);
        assertEquals(45, PageRequest.first().getLimit());
    }

    @Test
    public void pageCopiesItemsAndComputesNavigation() {
        java.util.List<String> source = new java.util.ArrayList<String>(Arrays.asList("a", "b"));
        PageView<String> page = new PageView<String>(source, 0, 2, 3);
        source.clear();
        assertEquals(Arrays.asList("a", "b"), page.getItems());
        assertTrue(page.hasNext());
        assertFalse(page.hasPrevious());
        try {
            page.getItems().add("c");
            fail("immutable page expected");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    private static void assertInvalid(int limit) {
        try {
            new PageRequest(0, limit);
            fail("invalid limit accepted");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
