package me.krunsh.kfaction.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.UUID;

import org.junit.Test;

public class FactionRelationRequestContractTest {

    @Test
    public void allyAndTruceRequestsAreDifferentKeys() {
        Faction faction =
                new Faction(
                        "alpha",
                        "Alpha",
                        UUID.randomUUID()
                );

        faction.addRelationRequest(
                "beta",
                Relation.ALLY
        );

        assertTrue(
                faction.hasRelationRequest(
                        "beta",
                        Relation.ALLY
                )
        );

        assertFalse(
                faction.hasRelationRequest(
                        "beta",
                        Relation.TRUCE
                )
        );

        assertTrue(
                faction.getRelationRequestsSnapshot()
                        .containsKey(
                                "ALLY|beta"
                        )
        );
    }

    @Test
    public void removingFactionRequestsCleansAllTypedVariants() {
        Faction faction =
                new Faction(
                        "alpha",
                        "Alpha",
                        UUID.randomUUID()
                );

        faction.addRelationRequest(
                "beta",
                Relation.ALLY
        );

        faction.addRelationRequest(
                "beta",
                Relation.TRUCE
        );

        assertTrue(
                faction.removeRelationRequestsForFaction(
                        "beta"
                )
        );

        assertFalse(
                faction.hasAnyRelationRequestForFaction(
                        "beta"
                )
        );
    }

    @Test
    public void expirationPrunesPersistedLegacyAndTypedRequests() {
        Faction faction =
                new Faction(
                        "alpha",
                        "Alpha",
                        UUID.randomUUID()
                );

        long old =
                System.currentTimeMillis()
                        - 60000L;

        faction.restoreRelationRequest(
                "ALLY|beta",
                old
        );

        faction.restoreRelationRequest(
                "legacyGamma",
                old
        );

        assertTrue(
                faction.pruneExpiredRelationRequests(
                        1000L
                )
        );

        assertTrue(
                faction.getRelationRequestsSnapshot()
                        .isEmpty()
        );
    }
}
