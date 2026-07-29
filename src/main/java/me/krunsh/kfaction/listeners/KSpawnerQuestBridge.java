package me.krunsh.kfaction.listeners;

import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.progression.WorldGuardRegionResolver;

/** Pont soft-depend vers les transactions post-pose/post-retrait KSpawner. */
public final class KSpawnerQuestBridge {
    private final Kfaction plugin;

    public KSpawnerQuestBridge(Kfaction plugin) {
        this.plugin = plugin;
    }

    public boolean register() {
        Plugin source = Bukkit.getPluginManager().getPlugin("KSpawner");
        if (source == null || !source.isEnabled()) return false;
        boolean place = registerOne(source,
                "me.krunsh.kspawner.api.events.KSpawnerPlaceEvent",
                "SPAWNER_PLACE");
        boolean broken = registerOne(source,
                "me.krunsh.kspawner.api.events.KSpawnerBreakEvent",
                "SPAWNER_BREAK");
        return place && broken;
    }

    @SuppressWarnings("unchecked")
    private boolean registerOne(Plugin source, String className,
            final String questType) {
        try {
            Class<? extends Event> eventClass = (Class<? extends Event>)
                    Class.forName(className, true,
                            source.getClass().getClassLoader());
            Listener marker = new Listener() {};
            Bukkit.getPluginManager().registerEvent(eventClass, marker,
                    EventPriority.MONITOR, new EventExecutor() {
                        @Override public void execute(Listener ignored, Event event)
                                throws org.bukkit.event.EventException {
                            handle(event, questType);
                        }
                    }, plugin, true);
            return true;
        } catch (ReflectiveOperationException | LinkageError ex) {
            plugin.getLogger().warning("Pont " + questType
                    + " indisponible: " + ex.getMessage());
            return false;
        }
    }

    private void handle(Event event, String questType)
            throws org.bukkit.event.EventException {
        try {
            Player player = (Player) event.getClass().getMethod("getPlayer")
                    .invoke(event);
            EntityType entityType = (EntityType) event.getClass()
                    .getMethod("getSpawnerType").invoke(event);
            Location location = (Location) event.getClass()
                    .getMethod("getLocation").invoke(event);
            if (player == null || entityType == null || location == null
                    || location.getWorld() == null) return;
            Faction faction = plugin.getFactionManager().getPlayerFaction(player);
            if (faction == null) return;
            plugin.getQuestManager().progressEntityAction(faction, questType,
                    entityType, 1L, location.getWorld().getName(),
                    WorldGuardRegionResolver.resolve(location));
        } catch (ReflectiveOperationException | ClassCastException ex) {
            throw new org.bukkit.event.EventException(ex);
        }
    }
}
