package me.krunsh.kfaction.listeners;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Contrat minimal résolu à l'exécution contre ShopGUIPlus. */
final class ShopGuiPlusTransactionAccess {

    private final Method eventResult;
    private final Method action;
    private final Method transactionResult;
    private final Method shopItem;
    private final Method player;
    private final Method amount;
    private final Method item;

    private ShopGuiPlusTransactionAccess(
            Method eventResult,
            Method action,
            Method transactionResult,
            Method shopItem,
            Method player,
            Method amount,
            Method item
    ) {
        this.eventResult = eventResult;
        this.action = action;
        this.transactionResult = transactionResult;
        this.shopItem = shopItem;
        this.player = player;
        this.amount = amount;
        this.item = item;
    }

    static ShopGuiPlusTransactionAccess resolve(Class<?> eventClass)
            throws NoSuchMethodException {
        Method eventResult = eventClass.getMethod("getResult");
        Class<?> resultClass = eventResult.getReturnType();
        Method action = resultClass.getMethod("getShopAction");
        Method transactionResult = resultClass.getMethod("getResult");
        Method shopItem = resultClass.getMethod("getShopItem");

        return new ShopGuiPlusTransactionAccess(
                eventResult,
                action,
                transactionResult,
                shopItem,
                resultClass.getMethod("getPlayer"),
                resultClass.getMethod("getAmount"),
                shopItem.getReturnType().getMethod("getItem")
        );
    }

    Sale read(Object event) throws ReflectiveOperationException {
        Object result = invoke(eventResult, event);
        if (result == null) {
            return null;
        }

        Object rawAction = invoke(action, result);
        Object rawResult = invoke(transactionResult, result);
        Object rawShopItem = invoke(shopItem, result);
        Object rawPlayer = invoke(player, result);
        Object rawAmount = invoke(amount, result);

        if (rawAction == null
                || rawResult == null
                || rawShopItem == null
                || !(rawPlayer instanceof Player)
                || !(rawAmount instanceof Number)) {
            return null;
        }

        Object rawItem = invoke(item, rawShopItem);
        if (!(rawItem instanceof ItemStack)) {
            return null;
        }

        return new Sale(
                enumName(rawAction),
                enumName(rawResult),
                (Player) rawPlayer,
                (ItemStack) rawItem,
                ((Number) rawAmount).intValue()
        );
    }

    private static Object invoke(Method method, Object target)
            throws ReflectiveOperationException {
        try {
            return method.invoke(target);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof ReflectiveOperationException) {
                throw (ReflectiveOperationException) cause;
            }
            throw exception;
        }
    }

    private static String enumName(Object value) {
        return value instanceof Enum<?>
                ? ((Enum<?>) value).name()
                : String.valueOf(value);
    }

    static final class Sale {
        private final String action;
        private final String result;
        private final Player player;
        private final ItemStack item;
        private final int amount;

        private Sale(
                String action,
                String result,
                Player player,
                ItemStack item,
                int amount
        ) {
            this.action = action;
            this.result = result;
            this.player = player;
            this.item = item;
            this.amount = amount;
        }

        String getAction() { return action; }
        String getResult() { return result; }
        Player getPlayer() { return player; }
        ItemStack getItem() { return item; }
        int getAmount() { return amount; }
    }
}
