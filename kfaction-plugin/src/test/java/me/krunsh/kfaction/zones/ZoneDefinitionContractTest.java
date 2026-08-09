package me.krunsh.kfaction.zones;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;

import org.junit.Test;

import me.krunsh.kfaction.permissions.TerritoryAction;
import me.krunsh.kfaction.zones.ZoneDefinition.DefaultPolicy;

public class ZoneDefinitionContractTest {

    @Test
    public void zoneIdsAreStrictAndNormalized() {
        assertEquals(
                "avant_post",
                ZoneDefinition.normalizeId(
                        "avant_post"
                )
        );

        assertEquals(
                "event-pvp",
                ZoneDefinition.normalizeId(
                        "EVENT-PVP"
                )
        );

        assertNull(
                ZoneDefinition.normalizeId(
                        "Avant Post"
                )
        );

        assertNull(
                ZoneDefinition.normalizeId(
                        "event!"
                )
        );
    }

    @Test
    public void explicitDenyWinsOverAllow() {
        ZoneDefinition definition =
                new ZoneDefinition(
                        "test",
                        "Test",
                        "&e",
                        "T",
                        "&eTest",
                        "",
                        "&e~ Test",
                        true,
                        DefaultPolicy.ALLOW,
                        EnumSet.of(
                                TerritoryAction.BLOCK_BREAK
                        ),
                        EnumSet.of(
                                TerritoryAction.BLOCK_BREAK
                        ),
                        true
                );

        assertFalse(
                definition.isActionAllowed(
                        TerritoryAction.BLOCK_BREAK
                )
        );

        assertTrue(
                definition.isActionAllowed(
                        TerritoryAction.ENTER
                )
        );
    }

    @Test
    public void denyPolicyOnlyAllowsExplicitAllow() {
        ZoneDefinition definition =
                new ZoneDefinition(
                        "avant_post",
                        "Avant-Post",
                        "&6",
                        "A",
                        "&6Avant-Post",
                        "",
                        "&6~ Avant-Post",
                        true,
                        DefaultPolicy.DENY,
                        EnumSet.of(
                                TerritoryAction.ENTER,
                                TerritoryAction.SWITCH
                        ),
                        EnumSet.noneOf(
                                TerritoryAction.class
                        ),
                        true
                );

        assertTrue(
                definition.isActionAllowed(
                        TerritoryAction.ENTER
                )
        );

        assertFalse(
                definition.isActionAllowed(
                        TerritoryAction.BLOCK_BREAK
                )
        );
    }

    @Test
    public void orphanZonesFailClosedButAllowMovement() {
        ZoneDefinition orphan =
                ZoneDefinition.orphan(
                        "removed_event"
                );

        assertFalse(
                orphan.isConfigured()
        );

        assertFalse(
                orphan.isPvpAllowed()
        );

        assertTrue(
                orphan.isActionAllowed(
                        TerritoryAction.ENTER
                )
        );

        assertFalse(
                orphan.isActionAllowed(
                        TerritoryAction.CONTAINER_OPEN
                )
        );
    }
}
