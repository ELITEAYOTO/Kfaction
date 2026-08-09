package me.krunsh.kfaction.data;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FactionRoleContractTest {

    @Test
    public void keepsExactlySixStableRoles() {
        assertArrayEquals(
                new FactionRole[] {
                        FactionRole.RECRUIT,
                        FactionRole.MEMBER,
                        FactionRole.OFFICER,
                        FactionRole.MODERATOR,
                        FactionRole.COLEADER,
                        FactionRole.LEADER
                },
                FactionRole.values()
        );
    }

    @Test
    public void promotionChainContainsOfficer() {
        assertEquals(
                FactionRole.MEMBER,
                FactionRole.RECRUIT
                        .getNextRole()
        );

        assertEquals(
                FactionRole.OFFICER,
                FactionRole.MEMBER
                        .getNextRole()
        );

        assertEquals(
                FactionRole.MODERATOR,
                FactionRole.OFFICER
                        .getNextRole()
        );

        assertEquals(
                FactionRole.COLEADER,
                FactionRole.MODERATOR
                        .getNextRole()
        );

        assertEquals(
                FactionRole.LEADER,
                FactionRole.COLEADER
                        .getNextRole()
        );

        assertNull(
                FactionRole.LEADER
                        .getNextRole()
        );
    }

    @Test
    public void leaderCannotBeReachedByNormalPromotion() {
        assertFalse(
                FactionRole.COLEADER
                        .canBePromotedNormally()
        );

        assertFalse(
                FactionRole.LEADER
                        .canBeDemotedNormally()
        );

        assertTrue(
                FactionRole.OFFICER
                        .canBePromotedNormally()
        );
    }
}
