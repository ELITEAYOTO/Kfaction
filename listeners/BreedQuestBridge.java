package me.krunsh.kfaction.listeners;

import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.progression.WorldGuardRegionResolver;

/** Pont optionnel vers la reproduction réellement validée par KHopeSpigot. */
public final class BreedQuestBridge {
    private static final String EVENT_CLASS =
            "com.hpfxd.pandaspigot.event.entity.PostEntityBreedEvent";

    private final Kfaction plugin;

    public BreedQuestBridge(Kfaction plugin) {
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
            plugin.getLogger().warning("Quêtes BREED désactivées: le fork ne "
                    + "fournit pas PostEntityBreedEvent (" + ex.getMessage() + ").");
            return false;
        }
    }

    private void handle(Event event) throws org.bukkit.event.EventException {
        try {
            Method getBreeder = event.getClass().getMethod("getBreeder");
            Method getChild = event.getClass().getMethod("getChild");
            Player breeder = (Player) getBreeder.invoke(event);
            Entity child = (Entity) getChild.invoke(event);
            if (breeder == null || child == null) return;
            Faction faction = plugin.getFactionManager().getPlayerFaction(breeder);
            if (faction == null) return;
            Location location = child.getLocation();
            plugin.getQuestManager().progressEntityAction(faction, "BREED",
                    child.getType(), 1L, location.getWorld().getName(),
                    WorldGuardRegionResolver.resolve(location));
        } catch (ReflectiveOperationException | ClassCastException ex) {
            throw new org.bukkit.event.EventException(ex);
        }
    }
}
