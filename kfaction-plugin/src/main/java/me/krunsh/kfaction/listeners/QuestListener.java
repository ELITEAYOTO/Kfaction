package me.krunsh.kfaction.listeners;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.managers.FactionChestManager;
import me.krunsh.kfaction.managers.QuestManager;
import me.krunsh.kfaction.progression.PlacedBlockTracker;
import me.krunsh.kfaction.progression.QuestAction;
import me.krunsh.kfaction.progression.WorldGuardRegionResolver;

/** Traduit les événements réussis vers les actions atomiques de progression v2. */
public final class QuestListener implements Listener {
    private final Kfaction plugin;

    public QuestListener(Kfaction plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    @SuppressWarnings("deprecation")
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Faction faction = plugin.getFactionManager().getPlayerFaction(player);
        QuestManager manager = plugin.getQuestManager();
        if (faction == null || manager == null || !manager.isEnabled()) return;

        Block block = event.getBlock();
        Material material = block.getType();
        int data = block.getData() & 0xFF;
        if (material == Material.GLOWING_REDSTONE_ORE) {
            material = Material.REDSTONE_ORE;
        }
        ItemStack hand = player.getItemInHand();
        boolean silk = hand != null
                && hand.containsEnchantment(Enchantment.SILK_TOUCH);
        boolean mature = !isCrop(material) || isMatureCrop(block);
        PlacedBlockTracker tracker = plugin.getPlacedBlockTracker();
        boolean playerPlaced = tracker != null && tracker.consume(block);
        String world = block.getWorld().getName();
        Set<String> regions = WorldGuardRegionResolver.resolve(block.getLocation());

        List<QuestAction> actions = new ArrayList<QuestAction>();
        actions.add(QuestAction.material(isCrop(material) ? "HARVEST" : "MINE",
                material, data, null, 1L, world, regions, silk, mature,
                playerPlaced));
        actions.add(QuestAction.material("BREAK", material, data, null, 1L,
                world, regions, silk, mature, playerPlaced));
        manager.applyActions(faction, actions);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    @SuppressWarnings("deprecation")
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        QuestManager manager = plugin.getQuestManager();
        PlacedBlockTracker tracker = plugin.getPlacedBlockTracker();
        int data = block.getData() & 0xFF;
        if (manager != null && tracker != null
                && manager.shouldTrackPlayerPlaced(block.getType(), data)) {
            tracker.record(block);
        }
        Faction faction = plugin.getFactionManager()
                .getPlayerFaction(event.getPlayer());
        if (faction == null || manager == null || !manager.isEnabled()) return;
        Location location = block.getLocation();
        manager.progressBlockAction(faction, "PLACE", block.getType(), data,
                null, 1L, block.getWorld().getName(),
                WorldGuardRegionResolver.resolve(location),
                false, true, true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player) {
            Player victim = (Player) entity;
            Player killer = victim.getKiller();
            if (killer == null) return;
            Location location = victim.getLocation();
            plugin.getQuestManager().progressPlayerKill(killer, victim,
                    location.getWorld().getName(),
                    WorldGuardRegionResolver.resolve(location),
                    System.currentTimeMillis());
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        Faction faction = plugin.getFactionManager().getPlayerFaction(killer);
        if (faction == null) return;
        Location location = entity.getLocation();
        plugin.getQuestManager().progressEntityAction(faction, "MOB_KILL",
                entity.getType(), 1L, location.getWorld().getName(),
                WorldGuardRegionResolver.resolve(location));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        Faction faction = plugin.getFactionManager()
                .getPlayerFaction(event.getPlayer());
        if (faction == null) return;
        Block block = event.getBlock();
        plugin.getQuestManager().progressBlockAction(faction, "SMELT",
                event.getItemType(), 0, null, event.getItemAmount(),
                block.getWorld().getName(),
                WorldGuardRegionResolver.resolve(block.getLocation()),
                false, true, false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    @SuppressWarnings("deprecation")
    public void onEnchant(EnchantItemEvent event) {
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;
        Faction faction = plugin.getFactionManager()
                .getPlayerFaction(event.getEnchanter());
        if (faction == null) return;
        Block table = event.getEnchantBlock();
        plugin.getQuestManager().progressBlockAction(faction, "ENCHANT",
                item.getType(), item.getDurability(), null, 1L,
                table.getWorld().getName(),
                WorldGuardRegionResolver.resolve(table.getLocation()),
                false, true, false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    @SuppressWarnings("deprecation")
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH
                || !(event.getCaught() instanceof Item)) return;
        ItemStack caught = ((Item) event.getCaught()).getItemStack();
        if (caught == null || caught.getType() == Material.AIR) return;
        Faction faction = plugin.getFactionManager()
                .getPlayerFaction(event.getPlayer());
        if (faction == null) return;
        Location location = event.getCaught().getLocation();
        plugin.getQuestManager().progressBlockAction(faction, "FISH",
                caught.getType(), caught.getDurability(), null,
                Math.max(1, caught.getAmount()), location.getWorld().getName(),
                WorldGuardRegionResolver.resolve(location),
                false, true, false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTame(EntityTameEvent event) {
        if (!(event.getOwner() instanceof Player)) return;
        Player player = (Player) event.getOwner();
        Faction faction = plugin.getFactionManager().getPlayerFaction(player);
        if (faction == null) return;
        Location location = event.getEntity().getLocation();
        plugin.getQuestManager().progressEntityAction(faction, "TAME",
                event.getEntity().getType(), 1L, location.getWorld().getName(),
                WorldGuardRegionResolver.resolve(location));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        FactionChestManager chestManager = plugin.getFactionChestManager();
        if (chestManager != null && chestManager.isFactionChest(event.getInventory())) {
            chestManager.handleClose(player, event.getInventory());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        FactionChestManager chestManager = plugin.getFactionChestManager();
        if (chestManager == null || event.getInventory() == null
                || !chestManager.isFactionChest(event.getInventory())) return;
        Player player = (Player) event.getWhoClicked();
        String factionId = chestManager.getFactionIdFromChest(event.getInventory());
        if (factionId == null) return;
        Faction faction = plugin.getFactionManager().getFaction(factionId);
        if (faction == null || !faction.hasPermission(player.getUniqueId(),
                me.krunsh.kfaction.data.PermissionAction.CHEST)) {
            event.setCancelled(true);
            player.sendMessage("§cVous n'avez pas la permission de modifier le coffre.");
        }
    }

    private boolean isCrop(Material material) {
        return material == Material.CROPS || material == Material.CARROT
                || material == Material.POTATO || material == Material.NETHER_WARTS
                || material == Material.COCOA;
    }

    @SuppressWarnings("deprecation")
    private boolean isMatureCrop(Block block) {
        switch (block.getType()) {
            case CROPS:
            case CARROT:
            case POTATO:
                return block.getData() >= 7;
            case NETHER_WARTS:
                return block.getData() >= 3;
            case COCOA:
                return (block.getData() & 0xC) >= 8;
            default:
                return true;
        }
    }
}
