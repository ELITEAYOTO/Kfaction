package me.krunsh.kfaction.policy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public class FactionNamePolicyTest {

    @Test
    public void validatesLengthCharactersAndReservedWords() {
        assertTrue(FactionNamePolicy.isValid("Volkaria", 3, 16,
            "^[a-zA-Z0-9_]+$", Arrays.asList("admin", "safezone")));
        assertFalse(FactionNamePolicy.isValid("ab", 3, 16,
            "^[a-zA-Z0-9_]+$", Arrays.asList("admin")));
        assertFalse(FactionNamePolicy.isValid("Volk aria", 3, 16,
            "^[a-zA-Z0-9_]+$", Arrays.asList("admin")));
        assertFalse(FactionNamePolicy.isValid("AdMiN", 3, 16,
            "^[a-zA-Z0-9_]+$", Arrays.asList("admin")));
    }
}
