package me.krunsh.kfaction.listeners;

import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import de.tr7zw.changeme.nbtapi.NBTItem;
import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.policy.QuestSaleIdentity;
import me.krunsh.kfaction.policy.QuestSalePolicy;
import me.krunsh.kfaction.utils.KfactionLogger;

/** Progression ITEM_SELL basée exclusivement sur une transaction terminée. */
public final class ShopGuiPlusQuestBridge {

    private static final String EVENT_CLASS =
            "net.brcdev.shopgui.event.ShopPostTransactionEvent";

    private final Kfaction plugin;
    private final Listener owner = new Listener() { };
    private final AtomicBoolean runtimeFailureLogged = new AtomicBoolean();

    public ShopGuiPlusQuestBridge(Kfaction plugin) {
        this.plugin = plugin;
    }

    public boolean register() {
        Plugin shopGuiPlus = Bukkit.getPluginManager().getPlugin("ShopGUIPlus");
        if (shopGuiPlus == null || !shopGuiPlus.isEnabled()) {
            return false;
        }

        try {
            ClassLoader loader = shopGuiPlus.getClass().getClassLoader();
            Class<?> rawEvent = Class.forName(EVENT_CLASS, false, loader);

            if (!Event.class.isAssignableFrom(rawEvent)) {
                throw new IllegalStateException(
                        EVENT_CLASS + " n'est pas un event Bukkit"
                );
            }

            @SuppressWarnings("unchecked")
            Class<? extends Event> eventClass =
                    (Class<? extends Event>) rawEvent;
            final ShopGuiPlusTransactionAccess access =
                    ShopGuiPlusTransactionAccess.resolve(rawEvent);

            Bukkit.getPluginManager().registerEvent(
                    eventClass,
                    owner,
                    EventPriority.MONITOR,
                    new EventExecutor() {
                        @Override
                        public void execute(Listener listener, Event event)
                                throws EventException {
                            handle(access, event);
                        }
                    },
                    plugin,
                    true
            );
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            KfactionLogger.warn(
                    plugin,
                    "Bridge ShopGUIPlus SELL désactivé (contrat incompatible): "
                            + exception.getClass().getSimpleName()
            );
            return false;
        }
    }

    private void handle(
            ShopGuiPlusTransactionAccess access,
            Event event
    ) {
        try {
            ShopGuiPlusTransactionAccess.Sale sale = access.read(event);
            if (sale == null
                    || !QuestSalePolicy.shouldCount(
                            sale.getAction(),
                            sale.getResult(),
                            sale.getAmount()
                    )
                    || sale.getItem().getType() == Material.AIR) {
                return;
            }

            plugin.getQuestManager().onItemSell(
                    sale.getPlayer(),
                    sale.getItem().getType(),
                    readSparrowItemId(sale.getItem()),
                    sale.getAmount()
            );
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (runtimeFailureLogged.compareAndSet(false, true)) {
                KfactionLogger.warn(
                        plugin,
                        "Bridge ShopGUIPlus SELL ignoré après une erreur: "
                                + exception.getClass().getSimpleName()
                );
            }
        }
    }

    private static String readSparrowItemId(org.bukkit.inventory.ItemStack item) {
        try {
            return QuestSaleIdentity.normalizeCit(
                    new NBTItem(item).getString("sparrowmc-item")
            );
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
