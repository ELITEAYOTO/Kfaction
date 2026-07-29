package me.krunsh.kfaction.managers;

import java.util.EnumSet;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FLocation;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.PermissionAction;
import me.krunsh.kfaction.data.Relation;

/**
 * Gestionnaire de la protection territoriale — optimisé pour 1000+ joueurs
 * 
 * Optimisations:
 * - EnumSet au lieu de HashSet pour les types de blocs (O(1) bit-test vs hash)
 * - Config cachée (warzone/raid/pvp) au lieu de relire la config à chaque événement
 * - Getters retournent des vues non-modifiables (pas de copie)
 */
public class TerritoryManager {
    
    private final Kfaction plugin;
    
    // EnumSet = stockage en long (bit-set) — O(1) contains, zéro allocation
    private Set<Material> switchBlocks = EnumSet.noneOf(Material.class);
    private Set<Material> containerBlocks = EnumSet.noneOf(Material.class);
    private Set<Material> protectedBlocks = EnumSet.noneOf(Material.class);
    
    // Config cachée — évite des appels à getBoolean() sur chaque block event
    private boolean warzoneAllowBuild;
    private boolean warzoneAllowContainers;
    private boolean raidAllowBuild;
    private boolean raidAllowBreak;
    private boolean raidAllowContainers;
    private boolean raidAllowTnt;
    private boolean raidAllowSpawners;
    private boolean allowFriendlyFire;
    
    public TerritoryManager(Kfaction plugin) {
        this.plugin = plugin;
        loadBlockLists();
        loadCachedConfig();
    }
    
    /**
     * Initialise le manager
     */
    public void initialize() {
        loadBlockLists();
        loadCachedConfig();
        plugin.getLogger().info("TerritoryManager initialisé");
    }
    
    /**
     * Charge les valeurs de config en cache
     */
    private void loadCachedConfig() {
        warzoneAllowBuild = plugin.getConfigManager().getBoolean("territory.warzone.allow-build", false);
        warzoneAllowContainers = plugin.getConfigManager().getBoolean("territory.warzone.allow-containers", true);
        raidAllowBuild = plugin.getConfigManager().getBoolean("territory.raid.allow-build", true);
        raidAllowBreak = plugin.getConfigManager().getBoolean("territory.raid.allow-break", true);
        raidAllowContainers = plugin.getConfigManager().getBoolean("territory.raid.allow-containers", true);
        raidAllowTnt = plugin.getConfigManager().getBoolean("territory.raid.allow-tnt", true);
        raidAllowSpawners = plugin.getConfigManager().getBoolean("territory.raid.allow-spawners", true);
        allowFriendlyFire = plugin.getConfigManager().getBoolean("pvp.allow-friendly-fire", false);
    }
    
    /**
     * Charge les listes de blocs (EnumSet)
     */
    private void loadBlockLists() {
        switchBlocks = EnumSet.of(
            Material.WOODEN_DOOR, Material.IRON_DOOR, Material.FENCE_GATE,
            Material.TRAP_DOOR, Material.LEVER, Material.STONE_BUTTON,
            Material.WOOD_BUTTON, Material.STONE_PLATE, Material.WOOD_PLATE
        );
        
        containerBlocks = EnumSet.of(
            Material.CHEST, Material.TRAPPED_CHEST, Material.ENDER_CHEST,
            Material.FURNACE, Material.BURNING_FURNACE, Material.DISPENSER,
            Material.DROPPER, Material.HOPPER, Material.BREWING_STAND,
            Material.ANVIL, Material.ENCHANTMENT_TABLE, Material.BEACON
        );
        
        protectedBlocks = EnumSet.of(
            Material.MOB_SPAWNER, Material.BEACON, Material.DRAGON_EGG
        );
    }
    
    // === Vérification des permissions ===
    
    /**
     * Vérifie si un joueur peut construire à une location
     */
    public boolean canBuild(Player player, Location location) {
        return canPerformAction(player, location, PermissionAction.BUILD);
    }
    
    /**
     * Vérifie si un joueur peut détruire à une location
     */
    public boolean canBreak(Player player, Location location) {
        return canPerformAction(player, location, PermissionAction.DESTROY);
    }
    
    /**
     * Vérifie si un joueur peut interagir avec un bloc
     */
    public boolean canInteract(Player player, Block block) {
        PermissionAction action = getActionForBlock(block.getType());
        return canPerformAction(player, block.getLocation(), action);
    }
    
    /**
     * Vérifie si un joueur peut faire une action à une location.
     * Chemin critique — appelé sur chaque block event pour chaque joueur.
     */
    public boolean canPerformAction(Player player, Location location, PermissionAction action) {
        // Bypass admin (vérification la plus rapide = permission cache Bukkit)
        if (player.hasPermission("kfaction.admin.bypass")) {
            return true;
        }
        
        // Lookup chunk → faction (O(1) via ConcurrentHashMap)
        FLocation fLoc = new FLocation(location);
        Faction factionAt = plugin.getClaimManager().getFactionAt(fLoc);
        
        // Wilderness = autorisé (cas le plus fréquent, court-circuiter tôt)
        if (factionAt.isWilderness()) {
            return true;
        }
        
        // Safezone = pas de destruction
        if (factionAt.isSafezone()) {
            return action != PermissionAction.BUILD && 
                   action != PermissionAction.DESTROY &&
                   action != PermissionAction.TNT;
        }
        
        // Warzone = dépend de config cachée
        if (factionAt.isWarzone()) {
            return canActionInWarzone(action);
        }
        
        // Faction normale
        Faction playerFaction = plugin.getFactionManager().getPlayerFaction(player);
        
        // Pas de faction = pas d'accès
        if (playerFaction == null) {
            return false;
        }
        
        // Même faction
        if (playerFaction.getId().equals(factionAt.getId())) {
            FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
            return factionAt.hasPermission(fPlayer.getUuid(), action);
        }
        
        // Faction différente - vérifier la relation
        Relation relation = playerFaction.getRelationTo(factionAt);
        
        // Ennemi sur territoire raidable = dépend de l'action
        if (relation == Relation.ENEMY && plugin.getClaimManager().isRaidable(factionAt)) {
            return canActionWhenRaiding(action);
        }
        
        // Vérifier les permissions de relation
        return factionAt.hasPermission(relation, action);
    }
    
    /**
     * Détermine l'action à vérifier pour un type de bloc.
     * EnumSet.contains() = O(1) bit-test, pas de hash.
     */
    private PermissionAction getActionForBlock(Material material) {
        if (switchBlocks.contains(material)) {
            return PermissionAction.SWITCH;
        }
        if (containerBlocks.contains(material)) {
            return PermissionAction.CONTAINER;
        }
        if (material == Material.MOB_SPAWNER) {
            return PermissionAction.SPAWNER;
        }
        if (material == Material.TNT) {
            return PermissionAction.TNT;
        }
        return PermissionAction.SWITCH;
    }
    
    /**
     * Vérifie si une action est autorisée en warzone (config cachée)
     */
    private boolean canActionInWarzone(PermissionAction action) {
        switch (action) {
            case BUILD:
            case DESTROY:
                return warzoneAllowBuild;
            case CONTAINER:
                return warzoneAllowContainers;
            case SWITCH:
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Vérifie si une action est autorisée lors d'un raid (config cachée)
     */
    private boolean canActionWhenRaiding(PermissionAction action) {
        switch (action) {
            case BUILD:
                return raidAllowBuild;
            case DESTROY:
                return raidAllowBreak;
            case CONTAINER:
                return raidAllowContainers;
            case TNT:
                return raidAllowTnt;
            case SPAWNER:
                return raidAllowSpawners;
            default:
                return false;
        }
    }
    
    // === PvP ===
    
    /**
     * Vérifie si le PvP est autorisé entre deux joueurs
     */
    public boolean canPvP(Player attacker, Player defender) {
        // Vérifier la zone du défenseur
        FLocation defenderLoc = new FLocation(defender.getLocation());
        Faction factionAt = plugin.getClaimManager().getFactionAt(defenderLoc);
        
        // Safezone = pas de PvP
        if (factionAt.isSafezone()) {
            return false;
        }
        
        // Vérifier la zone de l'attaquant aussi
        FLocation attackerLoc = new FLocation(attacker.getLocation());
        Faction attackerZone = plugin.getClaimManager().getFactionAt(attackerLoc);
        if (attackerZone.isSafezone()) {
            return false;
        }
        
        // Warzone = PvP autorisé
        if (factionAt.isWarzone() || attackerZone.isWarzone()) {
            return true;
        }
        
        // Vérifier les factions
        Faction attackerFaction = plugin.getFactionManager().getPlayerFaction(attacker);
        Faction defenderFaction = plugin.getFactionManager().getPlayerFaction(defender);
        
        // Pas de faction = PvP autorisé
        if (attackerFaction == null || defenderFaction == null) {
            return true;
        }
        
        // Même faction (config cachée)
        if (attackerFaction.getId().equals(defenderFaction.getId())) {
            return allowFriendlyFire;
        }
        
        // Vérifier la relation
        Relation relation = attackerFaction.getRelationTo(defenderFaction);
        return relation.isPvPAllowed();
    }
    
    /**
     * Obtient le multiplicateur de dégâts basé sur la relation
     */
    public double getDamageMultiplier(Player attacker, Player defender) {
        Faction attackerFaction = plugin.getFactionManager().getPlayerFaction(attacker);
        Faction defenderFaction = plugin.getFactionManager().getPlayerFaction(defender);
        
        if (attackerFaction == null || defenderFaction == null) {
            return 1.0;
        }
        
        Relation relation = attackerFaction.getRelationTo(defenderFaction);
        return relation.getDamageMultiplier();
    }
    
    // === Messages de zone ===
    
    /**
     * Obtient le message d'entrée dans une zone
     */
    public String getZoneEnterMessage(Faction faction, Faction playerFaction) {
        if (faction.isWilderness()) {
            return plugin.getMessageManager().get("territory.enter.wilderness");
        }
        if (faction.isSafezone()) {
            return plugin.getMessageManager().get("territory.enter.safezone");
        }
        if (faction.isWarzone()) {
            return plugin.getMessageManager().get("territory.enter.warzone");
        }
        
        // Faction normale
        String relationColor = "&f";
        String relationName = "Neutre";
        
        if (playerFaction != null) {
            Relation relation = playerFaction.getRelationTo(faction);
            relationColor = relation.getColorPrefix();
            relationName = relation.getDisplayName();
        }
        
        return plugin.getMessageManager().get("territory.enter.faction",
            "{faction}", faction.getName(),
            "{relation}", relationName,
            "{color}", relationColor,
            "{description}", faction.getDescription());
    }
    
    // === Getters (vues non-modifiables, pas de copie) ===
    
    public Set<Material> getSwitchBlocks() {
        return java.util.Collections.unmodifiableSet(switchBlocks);
    }
    
    public Set<Material> getContainerBlocks() {
        return java.util.Collections.unmodifiableSet(containerBlocks);
    }
    
    public Set<Material> getProtectedBlocks() {
        return java.util.Collections.unmodifiableSet(protectedBlocks);
    }
}
