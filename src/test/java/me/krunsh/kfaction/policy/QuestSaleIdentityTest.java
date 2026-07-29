package me.krunsh.kfaction.policy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class QuestSaleIdentityTest {
    @Test
    public void legacyMaterialTargetAcceptsVanillaAndCitVariants() {
        assertTrue(QuestSaleIdentity.matches("DIAMOND", null, "DIAMOND", null));
        assertTrue(QuestSaleIdentity.matches("DIAMOND", "", "DIAMOND", "azurite"));
    }

    @Test
    public void exactCitTargetRequiresMaterialAndSparrowIdentity() {
        assertTrue(QuestSaleIdentity.matches(
                "DIAMOND_CHESTPLATE", "azurite_chestplate",
                "DIAMOND_CHESTPLATE", "azurite_chestplate"));
        assertFalse(QuestSaleIdentity.matches(
                "DIAMOND_CHESTPLATE", "azurite_chestplate",
                "DIAMOND_CHESTPLATE", "jaspe_chestplate"));
        assertFalse(QuestSaleIdentity.matches(
                "DIAMOND_CHESTPLATE", "azurite_chestplate",
                "IRON_CHESTPLATE", "azurite_chestplate"));
        assertFalse(QuestSaleIdentity.matches(
                "DIAMOND_CHESTPLATE", "azurite_chestplate",
                "DIAMOND_CHESTPLATE", null));
    }

    @Test
    public void citValueRemainsCaseSensitiveAndLevelIsOutsideIdentity() {
        assertFalse(QuestSaleIdentity.matches(
                "DIAMOND_CHESTPLATE", "Azurite_Chestplate",
                "DIAMOND_CHESTPLATE", "azurite_chestplate"));

        // Il n'existe volontairement aucun parametre level dans l'identite.
        assertTrue(QuestSaleIdentity.matches(
                "DIAMOND_CHESTPLATE", "volkarite_chestplate",
                "DIAMOND_CHESTPLATE", "volkarite_chestplate"));
    }
}
