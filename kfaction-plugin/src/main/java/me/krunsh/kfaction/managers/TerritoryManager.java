package me.krunsh.kfaction.managers;

import java.util.EnumSet;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FLocation;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.PermissionAction;
import me.krunsh.kfaction.data.Relation;
import me.krunsh.kfaction.permissions.TerritoryAction;
import me.krunsh.kfaction.zones.ZoneDefinition;

/**
 * Adaptateur Bukkit du Permission Engine V2.
 *
 * Cette classe ne décide plus des ACL : elle classe l'action Bukkit puis
 * délègue à PermissionService.
 */
public final class TerritoryManager {

    private final Kfaction plugin;

    private Set<Material> switchBlocks;
    private Set<Material> containerBlocks;
    private Set<Material> protectedBlocks;

    public TerritoryManager(Kfaction plugin) {
        this.plugin = plugin;
        loadBlockLists();
    }

    public void initialize() {
        loadBlockLists();

        if (plugin.getPermissionManager() != null) {
            plugin.getPermissionManager().reload();
        }

        plugin.getLogger().info(
                "TerritoryManager V2 initialisé"
        );
    }

    public void reload() {
        loadBlockLists();

        if (plugin.getPermissionManager() != null) {
            plugin.getPermissionManager().reload();
        }
    }

    private void loadBlockLists() {
        switchBlocks = EnumSet.of(
                Material.WOODEN_DOOR,
                Material.IRON_DOOR,
                Material.FENCE_GATE,
                Material.TRAP_DOOR,
                Material.LEVER,
                Material.STONE_BUTTON,
                Material.WOOD_BUTTON,
                Material.STONE_PLATE,
                Material.WOOD_PLATE
        );

        containerBlocks = EnumSet.of(
                Material.CHEST,
                Material.TRAPPED_CHEST,
                Material.ENDER_CHEST,
                Material.FURNACE,
                Material.BURNING_FURNACE,
                Material.DISPENSER,
                Material.DROPPER,
                Material.HOPPER,
                Material.BREWING_STAND,
                Material.ANVIL,
                Material.ENCHANTMENT_TABLE,
                Material.BEACON
        );

        protectedBlocks = EnumSet.of(
                Material.MOB_SPAWNER,
                Material.BEACON,
                Material.DRAGON_EGG
        );
    }

    // ============================================================
    // Protection
    // ============================================================

    public boolean canBuild(
            Player player,
            Location location
    ) {
        return plugin.getPermissionManager()
                .canTerritory(
                        player,
                        location,
                        TerritoryAction.BLOCK_PLACE
                );
    }

    public boolean canBreak(
            Player player,
            Location location
    ) {
        return plugin.getPermissionManager()
                .canTerritory(
                        player,
                        location,
                        TerritoryAction.BLOCK_BREAK
                );
    }

    public boolean canInteract(
            Player player,
            Block block
    ) {
        if (block == null) {
            return true;
        }

        TerritoryAction action =
                getActionForBlock(
                        block.getType()
                );

        return plugin.getPermissionManager()
                .canTerritory(
                        player,
                        block.getLocation(),
                        action
                );
    }

    /**
     * Compatibilité V1 pour les callers qui utilisent encore PermissionAction.
     */
    public boolean canPerformAction(
            Player player,
            Location location,
            PermissionAction action
    ) {
        TerritoryAction mapped =
                TerritoryAction.fromLegacy(
                        action
                );

        return mapped != null
                && plugin.getPermissionManager()
                        .canTerritory(
                                player,
                                location,
                                mapped
                        );
    }

    private TerritoryAction getActionForBlock(
            Material material
    ) {
        if (material == null) {
            return TerritoryAction.SWITCH;
        }

        if (switchBlocks.contains(material)) {
            return TerritoryAction.SWITCH;
        }

        if (containerBlocks.contains(material)) {
            return TerritoryAction.CONTAINER_OPEN;
        }

        if (material == Material.MOB_SPAWNER) {
            return TerritoryAction.SPAWNER_INTERACT;
        }

        if (material == Material.TNT) {
            return TerritoryAction.TNT_IGNITE;
        }

        return TerritoryAction.SWITCH;
    }

    // ============================================================
    // PvP
    // ============================================================

    public boolean canPvP(
            Player attacker,
            Player defender
    ) {
        return plugin.getPermissionManager()
                .getService()
                .canPvP(
                        attacker,
                        defender
                );
    }

    public double getDamageMultiplier(
            Player attacker,
            Player defender
    ) {
        return plugin.getPermissionManager()
                .getService()
                .getDamageMultiplier(
                        attacker,
                        defender
                );
    }

    // ============================================================
    // Messages de territoire
    // ============================================================

    public String getZoneEnterMessage(
            FLocation location,
            Faction playerFaction
    ) {
        ZoneDefinition zone =
                plugin.getClaimManager()
                        .getZoneService()
                        .getDefinitionAt(
                                location
                        );

        if (zone != null) {
            return ChatColor.translateAlternateColorCodes(
                    '&',
                    zone.getEnterMessage()
            );
        }

        Faction faction =
                plugin.getClaimManager()
                        .getFactionAt(
                                location
                        );

        return getZoneEnterMessage(
                faction,
                playerFaction
        );
    }

    public String getZoneEnterMessage(
            Faction faction,
            Faction playerFaction
    ) {
        if (faction.isWilderness()) {
            return plugin.getMessageManager()
                    .get(
                            "territory.enter.wilderness"
                    );
        }

        if (faction.isSafezone()) {
            return plugin.getMessageManager()
                    .get(
                            "territory.enter.safezone"
                    );
        }

        if (faction.isWarzone()) {
            return plugin.getMessageManager()
                    .get(
                            "territory.enter.warzone"
                    );
        }

        String relationColor = "&f";
        String relationName = "Neutre";

        if (playerFaction != null) {
            Relation relation =
                    playerFaction.getRelationTo(
                            faction
                    );

            relationColor =
                    relation.getColorPrefix();

            relationName =
                    relation.getDisplayName();
        }

        return plugin.getMessageManager()
                .get(
                        "territory.enter.faction",
                        "{faction}",
                        faction.getName(),
                        "{relation}",
                        relationName,
                        "{color}",
                        relationColor,
                        "{description}",
                        faction.getDescription()
                );
    }

    public Set<Material> getSwitchBlocks() {
        return java.util.Collections
                .unmodifiableSet(
                        switchBlocks
                );
    }

    public Set<Material> getContainerBlocks() {
        return java.util.Collections
                .unmodifiableSet(
                        containerBlocks
                );
    }

    public Set<Material> getProtectedBlocks() {
        return java.util.Collections
                .unmodifiableSet(
                        protectedBlocks
                );
    }
}
