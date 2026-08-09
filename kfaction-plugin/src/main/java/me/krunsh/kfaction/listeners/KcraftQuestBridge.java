package me.krunsh.kfaction.listeners;

import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.progression.QuestAction;
import me.krunsh.kfaction.progression.WorldGuardRegionResolver;

/** Pont soft-depend vers la transaction post-craft réussie de KCraft. */
public final class KcraftQuestBridge {
    private final Kfaction plugin;

    public KcraftQuestBridge(Kfaction plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("unchecked")
    public boolean register() {
        Plugin kcraft = Bukkit.getPluginManager().getPlugin("Kcraft");
        if (kcraft == null || !kcraft.isEnabled()) return false;
        try {
            Class<?> raw = Class.forName(
                    "me.krunsh.kcraft.api.events.KcraftPostCraftEvent",
                    true, kcraft.getClass().getClassLoader());
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
            plugin.getLogger().warning("Pont KCraft quêtes indisponible: "
                    + ex.getMessage());
            return false;
        }
    }

    private void handle(Event event) throws org.bukkit.event.EventException {
        try {
            Method isSuccess = event.getClass().getMethod("isSuccess");
            if (!Boolean.TRUE.equals(isSuccess.invoke(event))) return;
            Player player = (Player) event.getClass().getMethod("getPlayer")
                    .invoke(event);
            Object id = event.getClass().getMethod("getCraftId").invoke(event);
            String recipeId = id == null ? null : String.valueOf(id);
            if (player == null || recipeId == null
                    || recipeId.trim().isEmpty()) return;
            Faction faction = plugin.getFactionManager().getPlayerFaction(player);
            if (faction == null) return;
            Location location = player.getLocation();
            plugin.getQuestManager().applyAction(faction,
                    QuestAction.string("CUSTOM_CRAFT", recipeId, 1L,
                            location.getWorld().getName(),
                            WorldGuardRegionResolver.resolve(location)));
        } catch (ReflectiveOperationException | ClassCastException ex) {
            throw new org.bukkit.event.EventException(ex);
        }
    }
}
