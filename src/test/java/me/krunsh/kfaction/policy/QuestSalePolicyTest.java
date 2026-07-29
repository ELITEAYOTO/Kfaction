package me.krunsh.kfaction.policy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class QuestSalePolicyTest {
    @Test
    public void countsSuccessfulGuiAndSellAllTransactions() {
        assertTrue(QuestSalePolicy.shouldCount("SELL", "SUCCESS", 3));
        assertTrue(QuestSalePolicy.shouldCount("SELL_ALL", "SUCCESS", 64));
    }

    @Test
    public void ignoresBuyFailuresAndNonPositiveAmounts() {
        assertFalse(QuestSalePolicy.shouldCount("BUY", "SUCCESS", 1));
        assertFalse(QuestSalePolicy.shouldCount("SELL", "FAILURE_NO_ITEMS", 1));
        assertFalse(QuestSalePolicy.shouldCount("SELL", "SUCCESS", 0));
    }
}
