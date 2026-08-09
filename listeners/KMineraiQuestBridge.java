package me.krunsh.kfaction.listeners;

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

/** Pont soft-depend vers le post-minage transactionnel de Kminerai. */
public final class KMineraiQuestBridge {
    private final Kfaction plugin;

    public KMineraiQuestBridge(Kfaction plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("unchecked")
    public boolean register() {
        Plugin source = org.bukkit.Bukkit.getPluginManager().getPlugin("Kminerai");
        if (source == null || !source.isEnabled()) return false;
        try {
            Class<? extends Event> eventClass = (Class<? extends Event>)
                    Class.forName("me.krunsh.kminerai.api.events.CustomOreMinedEvent",
                            true, source.getClass().getClassLoader());
            Listener marker = new Listener() {};
            org.bukkit.Bukkit.getPluginManager().registerEvent(eventClass, marker,
                    EventPriority.MONITOR, new EventExecutor() {
                        @Override public void execute(Listener ignored, Event event)
                                throws org.bukkit.event.EventException {
                            handle(event);
                        }
                    }, plugin, true);
            return true;
        } catch (ReflectiveOperationException | LinkageError ex) {
            plugin.getLogger().warning("Pont CUSTOM_ORE_MINE indisponible: "
                    + ex.getMessage());
            return false;
        }
    }

    private void handle(Event event) throws org.bukkit.event.EventException {
        try {
            Player player = (Player) event.getClass().getMethod("getPlayer")
                    .invoke(event);
            Location location = (Location) event.getClass().getMethod("getLocation")
                    .invoke(event);
            Object rawId = event.getClass().getMethod("getOreId").invoke(event);
            String oreId = rawId == null ? null : String.valueOf(rawId);
            if (player == null || location == null || location.getWorld() == null
                    || oreId == null || oreId.trim().isEmpty()) return;
            Faction faction = plugin.getFactionManager().getPlayerFaction(player);
            if (faction == null) return;
            plugin.getQuestManager().applyAction(faction,
                    QuestAction.string("CUSTOM_ORE_MINE", oreId, 1L,
                            location.getWorld().getName(),
                            WorldGuardRegionResolver.resolve(location)));
        } catch (ReflectiveOperationException | ClassCastException ex) {
            throw new org.bukkit.event.EventException(ex);
        }
    }
}
