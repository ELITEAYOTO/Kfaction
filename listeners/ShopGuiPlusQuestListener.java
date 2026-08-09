package me.krunsh.kfaction.listeners;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.policy.QuestSalePolicy;
import net.brcdev.shopgui.event.ShopPostTransactionEvent;
import net.brcdev.shopgui.shop.ShopTransactionResult;
import shaded.de.tr7zw.changeme.nbtapi.NBTItem;

/** Progression ITEM_SELL basee exclusivement sur une transaction terminee. */
public final class ShopGuiPlusQuestListener implements Listener {
    private final Kfaction plugin;

    public ShopGuiPlusQuestListener(Kfaction plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTransaction(ShopPostTransactionEvent event) {
        ShopTransactionResult result = event.getResult();
        if (result == null || result.getShopAction() == null || result.getResult() == null) return;
        if (!QuestSalePolicy.shouldCount(result.getShopAction().name(), result.getResult().name(),
                result.getAmount())) return;
        if (result.getPlayer() == null || result.getShopItem() == null) return;

        ItemStack soldDefinition = result.getShopItem().getItem();
        if (soldDefinition == null || soldDefinition.getType() == Material.AIR) return;
        String sparrowItemId = readSparrowItemId(soldDefinition);
        plugin.getQuestManager().onItemSell(result.getPlayer(), soldDefinition.getType(),
                sparrowItemId, result.getAmount());
    }

    private static String readSparrowItemId(ItemStack item) {
        try {
            return me.krunsh.kfaction.policy.QuestSaleIdentity.normalizeCit(
                    new NBTItem(item).getString("sparrowmc-item"));
        } catch (RuntimeException ignored) {
            // Une quete CIT exacte ne progresse pas si l'identite est illisible.
            // Les quetes historiques par materiau restent compatibles.
            return null;
        }
    }
}
