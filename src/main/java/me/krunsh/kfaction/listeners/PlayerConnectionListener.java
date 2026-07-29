package me.krunsh.kfaction.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;

/**
 * Listener pour les connexions/déconnexions des joueurs
 */
public class PlayerConnectionListener implements Listener {
    
    private final Kfaction plugin;
    
    public PlayerConnectionListener(Kfaction plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Tenter de charger depuis le stockage si pas en cache
        if (!plugin.getFPlayerManager().isLoaded(player.getUniqueId())) {
            FPlayer loaded = plugin.getStorageManager().getStorage().loadFPlayer(player.getUniqueId().toString());
            if (loaded != null) {
                plugin.getFPlayerManager().loadFPlayer(loaded);
            }
        }
        
        // Charger/créer le FPlayer
        FPlayer fPlayer = plugin.getFPlayerManager().onPlayerJoin(player);
        
        // Afficher le message de territoire initial
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                // Envoyer le message de zone actuelle
                Faction faction = plugin.getClaimManager().getFactionAt(player.getLocation());
                Faction playerFaction = plugin.getFactionManager().getPlayerFaction(player);
                String zoneMsg = plugin.getTerritoryManager().getZoneEnterMessage(faction, playerFaction);
                player.sendMessage(zoneMsg);
            }
        }, 20L); // 1 seconde après connexion
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Sauvegarder et mettre à jour le FPlayer
        plugin.getFPlayerManager().onPlayerQuit(player);
        
        // Arrêter le mode admin auto-claim si actif
        plugin.getClaimManager().stopAdminAutoClaim(player.getUniqueId());
    }
}
