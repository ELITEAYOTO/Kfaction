package me.krunsh.kfaction.security;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WarpPasswordHasherHardeningTest {

    @Test
    public void verifiesCorrectPasswordOnly() {
        String hash =
                WarpPasswordHasher.hash(
                        "Volkaria-Secret-42",
                        10000
                );

        assertTrue(
                WarpPasswordHasher.verify(
                        "Volkaria-Secret-42",
                        hash
                )
        );

        assertFalse(
                WarpPasswordHasher.verify(
                        "wrong-password",
                        hash
                )
        );
    }

    @Test
    public void rejectsMalformedHashes() {
        assertFalse(
                WarpPasswordHasher.verify(
                        "secret",
                        null
                )
        );

        assertFalse(
                WarpPasswordHasher.verify(
                        "secret",
                        "plain-text"
                )
        );

        assertFalse(
                WarpPasswordHasher.verify(
                        "secret",
                        "pbkdf2-sha256$1$bad$bad"
                )
        );

        assertFalse(
                WarpPasswordHasher.verify(
                        "secret",
                        "pbkdf2-sha256$10000$%%%$%%%"
                )
        );
    }
}
