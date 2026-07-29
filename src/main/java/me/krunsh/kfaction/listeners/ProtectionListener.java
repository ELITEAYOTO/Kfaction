package me.krunsh.kfaction.listeners;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import me.krunsh.kfaction.Kfaction;

/**
 * Listener pour la protection territoriale
 * Bloque les interactions non autorisées (cancel silencieux)
 */
public class ProtectionListener implements Listener {
    
    private final Kfaction plugin;
    
    public ProtectionListener(Kfaction plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        
        if (!plugin.getTerritoryManager().canBuild(player, block.getLocation())) {
            event.setCancelled(true);
            // Cancel silencieux - pas de message ni de son
        }
    }
    
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        
        if (!plugin.getTerritoryManager().canBreak(player, block.getLocation())) {
            event.setCancelled(true);
            // Cancel silencieux - pas de message ni de son
        }
    }
    
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        
        // Vérifier l'interaction avec le bloc
        if (!plugin.getTerritoryManager().canInteract(player, block)) {
            event.setCancelled(true);
            // Cancel silencieux - pas de message ni de son
        }
    }
    
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        Location location = event.getBlockClicked().getRelative(event.getBlockFace()).getLocation();
        
        if (!plugin.getTerritoryManager().canBuild(player, location)) {
            event.setCancelled(true);
            // Cancel silencieux - pas de message ni de son
        }
    }
    
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlockClicked();
        
        if (!plugin.getTerritoryManager().canBreak(player, block.getLocation())) {
            event.setCancelled(true);
            // Cancel silencieux - pas de message ni de son
        }
    }
}
