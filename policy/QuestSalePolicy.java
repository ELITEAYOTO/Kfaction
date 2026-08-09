package me.krunsh.kfaction.policy;

/** Filtre pur applique aux transactions ShopGUIPlus avant progression. */
public final class QuestSalePolicy {
    private QuestSalePolicy() {}

    public static boolean shouldCount(String action, String result, int amount) {
        if (amount <= 0 || action == null || result == null) return false;
        boolean sale = "SELL".equals(action) || "SELL_ALL".equals(action);
        return sale && "SUCCESS".equals(result);
    }
}
