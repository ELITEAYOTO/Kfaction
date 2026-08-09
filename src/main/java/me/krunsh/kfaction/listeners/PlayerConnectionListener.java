package me.krunsh.kfaction.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FLocation;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;

/**
 * Connexion/déconnexion des joueurs.
 *
 * Le listener ne connaît plus le backend de stockage :
 * FPlayerManager centralise recherche, lazy-load et création.
 */
public class PlayerConnectionListener implements Listener {

    private final Kfaction plugin;

    public PlayerConnectionListener(Kfaction plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        FPlayer fPlayer = plugin
                .getFPlayerManager()
                .onPlayerJoin(player);

        if (fPlayer == null) {
            plugin.getLogger().severe(
                    "Impossible d'initialiser le FPlayer de "
                            + player.getName()
                            + " (" + player.getUniqueId() + ")"
            );
            return;
        }

        plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> {
                    if (!player.isOnline()) {
                        return;
                    }

                    FLocation location =
                            new FLocation(
                                    player.getLocation()
                            );

                    Faction playerFaction = plugin
                            .getFactionManager()
                            .getPlayerFaction(player);

                    String zoneMessage = plugin
                            .getTerritoryManager()
                            .getZoneEnterMessage(
                                    location,
                                    playerFaction
                            );

                    player.sendMessage(zoneMessage);
                },
                20L
        );
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        plugin.getFPlayerManager().onPlayerQuit(player);
        plugin.getClaimManager().stopAdminAutoClaim(player.getUniqueId());
    }
}
