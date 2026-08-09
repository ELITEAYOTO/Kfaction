package me.krunsh.kfaction.listeners;

import java.util.Iterator;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.Faction;

/**
 * Listener pour la protection territoriale.
 *
 * Lot 14:
 * - les protections joueur continuent à passer par TerritoryManager;
 * - les explosions sont filtrées pendant la Grace Period afin qu'une TNT
 *   tirée depuis l'extérieur ne contourne pas les checks Player.
 */
public class ProtectionListener implements Listener {

    private final Kfaction plugin;

    public ProtectionListener(Kfaction plugin) {
        this.plugin = plugin;
    }

    @EventHandler(
            priority = EventPriority.NORMAL,
            ignoreCancelled = true
    )
    public void onBlockPlace(
            BlockPlaceEvent event
    ) {
        Player player =
                event.getPlayer();

        Block block =
                event.getBlock();

        if (!plugin.getTerritoryManager()
                .canBuild(
                        player,
                        block.getLocation()
                )) {
            event.setCancelled(true);
        }
    }

    @EventHandler(
            priority = EventPriority.NORMAL,
            ignoreCancelled = true
    )
    public void onBlockBreak(
            BlockBreakEvent event
    ) {
        Player player =
                event.getPlayer();

        Block block =
                event.getBlock();

        if (!plugin.getTerritoryManager()
                .canBreak(
                        player,
                        block.getLocation()
                )) {
            event.setCancelled(true);
        }
    }

    @EventHandler(
            priority = EventPriority.NORMAL,
            ignoreCancelled = true
    )
    public void onPlayerInteract(
            PlayerInteractEvent event
    ) {
        if (event.getClickedBlock() == null) {
            return;
        }

        Player player =
                event.getPlayer();

        Block block =
                event.getClickedBlock();

        if (!plugin.getTerritoryManager()
                .canInteract(
                        player,
                        block
                )) {
            event.setCancelled(true);
        }
    }

    @EventHandler(
            priority = EventPriority.NORMAL,
            ignoreCancelled = true
    )
    public void onBucketEmpty(
            PlayerBucketEmptyEvent event
    ) {
        Player player =
                event.getPlayer();

        Location location =
                event.getBlockClicked()
                        .getRelative(
                                event.getBlockFace()
                        )
                        .getLocation();

        if (!plugin.getTerritoryManager()
                .canBuild(
                        player,
                        location
                )) {
            event.setCancelled(true);
        }
    }

    @EventHandler(
            priority = EventPriority.NORMAL,
            ignoreCancelled = true
    )
    public void onBucketFill(
            PlayerBucketFillEvent event
    ) {
        Player player =
                event.getPlayer();

        Block block =
                event.getBlockClicked();

        if (!plugin.getTerritoryManager()
                .canBreak(
                        player,
                        block.getLocation()
                )) {
            event.setCancelled(true);
        }
    }

    /**
     * Grace Period:
     *
     * On ne cancel PAS l'explosion complète. On retire uniquement les blocs
     * appartenant à une faction joueur de la blockList Bukkit.
     *
     * Résultat:
     * - l'explosion continue en Wilderness;
     * - SafeZone/WarZone gardent leurs propres règles;
     * - les claims faction sont protégés des dégâts blocs pendant la grâce.
     */
    @EventHandler(
            priority = EventPriority.HIGH,
            ignoreCancelled = true
    )
    public void onEntityExplode(
            EntityExplodeEvent event
    ) {
        if (plugin.getPermissionManager() == null
                || !plugin.getPermissionManager()
                        .getGraceService()
                        .blocksExplosionBlockDamage()) {
            return;
        }

        Iterator<Block> iterator =
                event.blockList()
                        .iterator();

        while (iterator.hasNext()) {
            Block block =
                    iterator.next();

            Faction faction =
                    plugin.getClaimManager()
                            .getFactionAt(
                                    block.getLocation()
                            );

            if (faction != null
                    && !faction.isSystemFaction()) {
                iterator.remove();
            }
        }
    }
}
