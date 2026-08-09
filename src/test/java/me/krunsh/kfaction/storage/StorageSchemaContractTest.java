package me.krunsh.kfaction.storage;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import me.krunsh.kfaction.storage.StorageSnapshot.EntityType;

/**
 * Freeze du format logique à la fin de Kfaction V2.
 *
 * Une future migration de payload doit mettre à jour ce test explicitement.
 */
public class StorageSchemaContractTest {

    @Test
    public void payloadSchemaIsFrozenAtNine() {
        assertEquals(
                9,
                StorageSnapshot.CURRENT_SCHEMA_VERSION
        );
    }

    @Test
    public void persistedEntityKindsRemainStable() {
        assertArrayEquals(
                new EntityType[] {
                        EntityType.FACTION,
                        EntityType.FPLAYER,
                        EntityType.GLOBAL_ZONES,
                        EntityType.GRACE_STATE
                },
                EntityType.values()
        );
    }
}
