package me.krunsh.kfaction.listeners;

import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.EventExecutor;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.policy.QuestSaleIdentity;
import me.krunsh.kfaction.progression.QuestAction;
import me.krunsh.kfaction.progression.WorldGuardRegionResolver;
import shaded.de.tr7zw.changeme.nbtapi.NBTItem;

/**
 * Optional bridge to KHopeSpigot's post-transaction vanilla crafting event.
 *
 * The event fires once for each recipe operation actually committed by
 * SlotResult. It therefore remains exact for shift-click and full inventories.
 */
public final class VanillaCraftQuestBridge {
    private static final String EVENT_CLASS =
            "com.hpfxd.pandaspigot.event.inventory.PostCraftItemEvent";

    private final Kfaction plugin;

    public VanillaCraftQuestBridge(Kfaction plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("unchecked")
    public boolean register() {
        try {
            Class<?> raw = Class.forName(EVENT_CLASS, true,
                    plugin.getClass().getClassLoader());
            Class<? extends Event> eventClass = (Class<? extends Event>) raw;
            Listener marker = new Listener() {};
            Bukkit.getPluginManager().registerEvent(eventClass, marker,
                    EventPriority.MONITOR, new EventExecutor() {
                        @Override
                        public void execute(Listener ignored, Event event)
                                throws org.bukkit.event.EventException {
                            handle(event);
                        }
                    }, plugin, true);
            return true;
        } catch (ReflectiveOperationException | LinkageError ex) {
            plugin.getLogger().warning("Quêtes CRAFT désactivées: le fork ne "
                    + "fournit pas PostCraftItemEvent (" + ex.getMessage() + ").");
            return false;
        }
    }

    private void handle(Event event) throws org.bukkit.event.EventException {
        try {
            Method getPlayer = event.getClass().getMethod("getPlayer");
            Method getResult = event.getClass().getMethod("getResult");
            Player player = (Player) getPlayer.invoke(event);
            ItemStack result = (ItemStack) getResult.invoke(event);
            if (!isPresent(result) || player == null) return;

            Faction faction = plugin.getFactionManager().getPlayerFaction(player);
            if (faction == null) return;
            Location location = player.getLocation();
            plugin.getQuestManager().applyAction(faction,
                    QuestAction.material("CRAFT", result.getType(),
                            result.getDurability(), readSparrowItemId(result),
                            result.getAmount(), location.getWorld().getName(),
                            WorldGuardRegionResolver.resolve(location),
                            false, true, false));
        } catch (ReflectiveOperationException | ClassCastException ex) {
            throw new org.bukkit.event.EventException(ex);
        }
    }

    private static boolean isPresent(ItemStack item) {
        return item != null && item.getType() != null
                && item.getType() != Material.AIR && item.getAmount() > 0;
    }

    private static String readSparrowItemId(ItemStack item) {
        if (!isPresent(item)) return null;
        try {
            return QuestSaleIdentity.normalizeCit(
                    new NBTItem(item).getString("sparrowmc-item"));
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
