package me.krunsh.kfaction.listeners;

import java.lang.reflect.Proxy;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.Assert;
import org.junit.Test;

public final class ShopGuiPlusTransactionAccessTest {

    @Test
    public void readsTheSupportedRuntimeContract() throws Exception {
        Player player = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[] { Player.class },
                (proxy, method, args) -> null
        );
        FakeEvent event = new FakeEvent(new FakeResult(player));

        ShopGuiPlusTransactionAccess.Sale sale =
                ShopGuiPlusTransactionAccess.resolve(FakeEvent.class)
                        .read(event);

        Assert.assertNotNull(sale);
        Assert.assertEquals("SELL", sale.getAction());
        Assert.assertEquals("SUCCESS", sale.getResult());
        Assert.assertSame(player, sale.getPlayer());
        Assert.assertEquals(Material.STONE, sale.getItem().getType());
        Assert.assertEquals(12, sale.getAmount());
    }

    @Test(expected = NoSuchMethodException.class)
    public void rejectsAnIncompleteContractAtRegistration() throws Exception {
        ShopGuiPlusTransactionAccess.resolve(IncompleteEvent.class);
    }

    public static final class FakeEvent {
        private final FakeResult result;

        public FakeEvent(FakeResult result) {
            this.result = result;
        }

        public FakeResult getResult() {
            return result;
        }
    }

    public static final class FakeResult {
        private final Player player;

        public FakeResult(Player player) {
            this.player = player;
        }

        public Action getShopAction() { return Action.SELL; }
        public Result getResult() { return Result.SUCCESS; }
        public FakeShopItem getShopItem() { return new FakeShopItem(); }
        public Player getPlayer() { return player; }
        public int getAmount() { return 12; }
    }

    public static final class FakeShopItem {
        public ItemStack getItem() {
            return new ItemStack(Material.STONE);
        }
    }

    public static final class IncompleteEvent {
        public IncompleteResult getResult() { return new IncompleteResult(); }
    }

    public static final class IncompleteResult {
        public Action getShopAction() { return Action.SELL; }
        public Result getResult() { return Result.SUCCESS; }
        public FakeShopItem getShopItem() { return new FakeShopItem(); }
        public Player getPlayer() { return null; }
    }

    public enum Action { SELL }
    public enum Result { SUCCESS }
}
