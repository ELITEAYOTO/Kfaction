package me.krunsh.kfaction.managers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.api.event.FactionClaimEvent;
import me.krunsh.kfaction.data.FLocation;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.Relation;

/**
 * Gestionnaire des claims (territoires)
 * Gère le mapping chunk -> faction et les opérations de claim/unclaim
 */
public class ClaimManager {
    
    private final Kfaction plugin;
    
    // Index: FLocation -> factionId pour recherche rapide
    private final Map<FLocation, String> claimIndex;

    // Admin auto-claim actif: UUID → "warzone" ou "safezone"
    private final Map<UUID, String> adminAutoClaimPlayers;
    private final Map<UUID, String> adminAutoUnclaimPlayers;
    
    public ClaimManager(Kfaction plugin) {
        this.plugin = plugin;
        this.claimIndex = new ConcurrentHashMap<>();
        this.adminAutoClaimPlayers = new ConcurrentHashMap<>();
        this.adminAutoUnclaimPlayers = new ConcurrentHashMap<>();
    }
    
    /**
     * Initialise le manager
     */
    public void initialize() {
        plugin.getLogger().info("ClaimManager initialisé");
    }

    /**
     * Reconstruit l'index chunk→faction depuis les objets Faction déjà chargés en mémoire.
     * À appeler APRÈS storageManager.loadAll() pour que getFactionAt() soit opérationnel.
     */
    public void rebuildClaimIndex() {
        claimIndex.clear();
        for (Faction faction : plugin.getFactionManager().getAllFactions()) {
            String factionId = faction.getId();
            for (FLocation loc : faction.getClaims()) {
                claimIndex.put(loc, factionId);
            }
        }
        plugin.getLogger().info("ClaimManager: index reconstruit (" + claimIndex.size() + " chunks claims)");
    }
    
    /**
     * Ferme le manager
     */
    public void shutdown() {
        claimIndex.clear();
        adminAutoClaimPlayers.clear();
        adminAutoUnclaimPlayers.clear();
    }
    
    // === Recherche ===
    
    /**
     * Obtient la faction propriétaire d'un chunk
     * @param location La FLocation du chunk
     * @return La faction ou wilderness si non claim
     */
    public Faction getFactionAt(FLocation location) {
        String factionId = claimIndex.get(location);
        if (factionId == null) {
            return plugin.getFactionManager().getWilderness();
        }
        Faction faction = plugin.getFactionManager().getFaction(factionId);
        return faction != null ? faction : plugin.getFactionManager().getWilderness();
    }
    
    /**
     * Obtient la faction à une location Bukkit
     * @param location La location
     * @return La faction propriétaire
     */
    public Faction getFactionAt(Location location) {
        return getFactionAt(new FLocation(location));
    }
    
    /**
     * Obtient la faction dans un chunk
     * @param chunk Le chunk
     * @return La faction propriétaire
     */
    public Faction getFactionAt(Chunk chunk) {
        return getFactionAt(new FLocation(chunk));
    }
    
    /**
     * Vérifie si un chunk est claim
     * @param location La FLocation
     * @return true si claim (pas wilderness)
     */
    public boolean isClaimed(FLocation location) {
        return claimIndex.containsKey(location);
    }
    
    /**
     * Vérifie si un chunk est en safezone
     * @param location La FLocation
     * @return true si safezone
     */
    public boolean isSafezone(FLocation location) {
        return Faction.SAFEZONE_ID.equals(claimIndex.get(location));
    }
    
    /**
     * Vérifie si un chunk est en warzone
     * @param location La FLocation
     * @return true si warzone
     */
    public boolean isWarzone(FLocation location) {
        return Faction.WARZONE_ID.equals(claimIndex.get(location));
    }
    
    /**
     * Vérifie si une location est en safezone
     * @param location Location Bukkit
     * @return true si safezone
     */
    public boolean isSafezone(Location location) {
        return isSafezone(new FLocation(location));
    }
    
    /**
     * Vérifie si une location est en warzone
     * @param location Location Bukkit
     * @return true si warzone
     */
    public boolean isWarzone(Location location) {
        return isWarzone(new FLocation(location));
    }
    
    // === Opérations de claim ===
    
    /**
     * Tente de claim un chunk pour une faction
     * @param faction La faction
     * @param location La FLocation
     * @return Résultat du claim
     */
    public ClaimResult claim(Faction faction, FLocation location) {
        // Vérifier si déjà claim par cette faction
        String currentOwner = claimIndex.get(location);
        if (faction.getId().equals(currentOwner)) {
            return ClaimResult.ALREADY_OWNED;
        }
        
        // Vérifier si claim par une autre faction
        if (currentOwner != null) {
            Faction owner = plugin.getFactionManager().getFaction(currentOwner);
            if (owner != null && !owner.isWilderness()) {
                // Vérifier si la faction est raidable (overclaim)
                if (!isRaidable(owner)) {
                    return ClaimResult.OWNED_BY_OTHER;
                }
            }
        }
        
        // Vérifier la limite de claims
        int maxClaims = getMaxClaims(faction);
        if (faction.getClaimCount() >= maxClaims) {
            return ClaimResult.LIMIT_REACHED;
        }
        
        // Vérifier la connexité si configuré
        if (plugin.getConfigManager().getBoolean("claims.require-connected", false)) {
            if (faction.getClaimCount() > 0 && !isAdjacentToFaction(location, faction)) {
                return ClaimResult.NOT_CONNECTED;
            }
        }
        
        // Vérifier la distance minimum du spawn si configuré
        int minDistanceSpawn = plugin.getConfigManager().getInt("claims.min-distance-spawn", 0);
        if (minDistanceSpawn > 0) {
            Location spawn = location.getWorld().getSpawnLocation();
            FLocation spawnChunk = new FLocation(spawn);
            if (location.distanceTo(spawnChunk) < minDistanceSpawn) {
                return ClaimResult.TOO_CLOSE_TO_SPAWN;
            }
        }
        
        // Effectuer le claim
        performClaim(faction, location);
        return ClaimResult.SUCCESS;
    }
    
    /**
     * Tente de claim un chunk pour une faction avec déclenchement de l'event API
     * @param player Le joueur qui claim
     * @param faction La faction
     * @param location La FLocation
     * @return Résultat du claim
     */
    public ClaimResult claim(Player player, Faction faction, FLocation location) {
        // Vérifications préliminaires
        String currentOwner = claimIndex.get(location);
        if (faction.getId().equals(currentOwner)) {
            return ClaimResult.ALREADY_OWNED;
        }
        
        // Déterminer le type de claim et l'ancien propriétaire
        Faction previousOwner = null;
        FactionClaimEvent.ClaimType claimType = FactionClaimEvent.ClaimType.CLAIM;
        
        if (currentOwner != null) {
            previousOwner = plugin.getFactionManager().getFaction(currentOwner);
            if (previousOwner != null && !previousOwner.isWilderness()) {
                if (!isRaidable(previousOwner)) {
                    return ClaimResult.OWNED_BY_OTHER;
                }
                claimType = FactionClaimEvent.ClaimType.OVERCLAIM;
            }
        }
        
        // Vérifier la limite de claims
        int maxClaims = getMaxClaims(faction);
        if (faction.getClaimCount() >= maxClaims) {
            return ClaimResult.LIMIT_REACHED;
        }
        
        // Vérifier la connexité
        if (plugin.getConfigManager().getBoolean("claims.require-connected", false)) {
            if (faction.getClaimCount() > 0 && !isAdjacentToFaction(location, faction)) {
                return ClaimResult.NOT_CONNECTED;
            }
        }
        
        // Vérifier distance spawn
        int minDistanceSpawn = plugin.getConfigManager().getInt("claims.min-distance-spawn", 0);
        if (minDistanceSpawn > 0) {
            Location spawn = location.getWorld().getSpawnLocation();
            FLocation spawnChunk = new FLocation(spawn);
            if (location.distanceTo(spawnChunk) < minDistanceSpawn) {
                return ClaimResult.TOO_CLOSE_TO_SPAWN;
            }
        }
        
        // Déclencher l'event API
        Chunk bukkitChunk = location.getWorld().getChunkAt(location.getX(), location.getZ());
        FactionClaimEvent event = new FactionClaimEvent(player, faction, bukkitChunk, previousOwner, claimType);
        Bukkit.getPluginManager().callEvent(event);
        
        if (event.isCancelled()) {
            String reason = event.getCancelReason();
            return reason != null ? new ClaimResult(false, reason) : ClaimResult.CANCELLED;
        }
        
        // Effectuer le claim
        performClaim(faction, location);
        return ClaimResult.SUCCESS;
    }
    
    /**
     * Effectue réellement le claim (sans vérifications)
     */
    private void performClaim(Faction faction, FLocation location) {
        // Retirer de l'ancien propriétaire si besoin
        String oldOwnerId = claimIndex.get(location);
        if (oldOwnerId != null) {
            Faction oldOwner = plugin.getFactionManager().getFaction(oldOwnerId);
            if (oldOwner != null) {
                oldOwner.removeClaim(location);
                plugin.getStorageManager().markDirty(oldOwner);
            }
        }
        
        // Ajouter au nouveau propriétaire
        claimIndex.put(location, faction.getId());
        faction.addClaim(location);
        plugin.getStorageManager().markDirty(faction);
    }
    
    /**
     * Retire le claim d'un chunk
     * @param faction La faction propriétaire
     * @param location La FLocation
     * @return true si unclaim réussi
     */
    public boolean unclaim(Faction faction, FLocation location) {
        String ownerId = claimIndex.get(location);
        if (ownerId == null || !ownerId.equals(faction.getId())) {
            return false;
        }
        
        claimIndex.remove(location);
        faction.removeClaim(location);
        
        // Check and remove home if in this chunk
        checkAndRemoveHomeInChunk(faction, location);
        
        // Check and remove warps in this chunk
        checkAndRemoveWarpsInChunk(faction, location);
        
        plugin.getStorageManager().markDirty(faction);
        return true;
    }
    
    /**
     * Checks if faction home is in the given chunk and removes it
     */
    private void checkAndRemoveHomeInChunk(Faction faction, FLocation chunkLoc) {
        if (faction.hasHome()) {
            Location home = faction.getHome();
            FLocation homeFLoc = new FLocation(home);
            if (homeFLoc.equals(chunkLoc)) {
                faction.setHome(null);
                // Notify online members
                for (org.bukkit.entity.Player p : faction.getOnlinePlayers()) {
                    plugin.getMessageManager().send(p, "home.removed-unclaim");
                }
            }
        }
    }
    
    /**
     * Checks and removes any warps in the given chunk
     */
    private void checkAndRemoveWarpsInChunk(Faction faction, FLocation chunkLoc) {
        java.util.List<String> warpsToRemove = new java.util.ArrayList<>();
        
        for (java.util.Map.Entry<String, Location> entry : faction.getWarps().entrySet()) {
            FLocation warpFLoc = new FLocation(entry.getValue());
            if (warpFLoc.equals(chunkLoc)) {
                warpsToRemove.add(entry.getKey());
            }
        }
        
        for (String warpName : warpsToRemove) {
            faction.removeWarp(warpName);
            // Notify online members
            for (org.bukkit.entity.Player p : faction.getOnlinePlayers()) {
                plugin.getMessageManager().send(p, "warp.removed-unclaim", "{name}", warpName);
            }
        }
    }
    
    /**
     * Retire tous les claims d'une faction
     * @param faction La faction
     */
    public void unclaimAll(Faction faction) {
        // Copier pour éviter ConcurrentModification
        Set<FLocation> claims = new HashSet<>(faction.getClaims());
        for (FLocation loc : claims) {
            claimIndex.remove(loc);
        }
        faction.clearClaims();
        plugin.getStorageManager().markDirty(faction);
    }
    
    /**
     * Retire un claim à une location (peu importe le propriétaire)
     * @param location La FLocation
     * @return true si unclaim réussi
     */
    public boolean unclaim(FLocation location) {
        String ownerId = claimIndex.get(location);
        if (ownerId == null) {
            return false;
        }
        
        Faction owner = plugin.getFactionManager().getFaction(ownerId);
        if (owner != null) {
            owner.removeClaim(location);
            plugin.getStorageManager().markDirty(owner);
        }
        claimIndex.remove(location);
        return true;
    }
    
    /**
     * Claim un chunk en warzone
     * @param location La FLocation
     */
    public void claimWarzone(FLocation location) {
        unclaim(location); // Retire tout claim existant
        Faction warzone = plugin.getFactionManager().getWarzone();
        claimIndex.put(location, warzone.getId());
        warzone.addClaim(location);
    }
    
    /**
     * Claim un chunk en safezone
     * @param location La FLocation
     */
    public void claimSafezone(FLocation location) {
        unclaim(location); // Retire tout claim existant
        Faction safezone = plugin.getFactionManager().getSafezone();
        claimIndex.put(location, safezone.getId());
        safezone.addClaim(location);
    }
    
    /**
     * @return Nombre total de claims dans l'index
     */
    public int getClaimCount() {
        return claimIndex.size();
    }
    
    // === Overclaiming ===
    
    /**
     * Vérifie si une faction est raidable (peut être overclaim)
     * @param faction La faction
     * @return true si le power total < nombre de claims
     */
    public boolean isRaidable(Faction faction) {
        if (faction == null || faction.isSystemFaction()) return false;
        double power = plugin.getPowerManager().getFactionPower(faction);
        return faction.getClaimCount() > power;
    }
    
    /**
     * Calcule le nombre de lands pouvant être overclaim
     * @param faction La faction
     * @return Nombre de lands au-delà du power
     */
    public int getOverclaimableCount(Faction faction) {
        if (faction == null || faction.isSystemFaction()) return 0;
        double power = plugin.getPowerManager().getFactionPower(faction);
        int deficit = faction.getClaimCount() - (int) power;
        return Math.max(0, deficit);
    }
    
    // === Helpers ===
    
    // === Admin auto-claim ===

    /**
     * Active/désactive l'auto-claim admin pour un joueur.
     * @param uuid UUID du joueur
     * @param type "warzone" ou "safezone"
     * @return true si activé, false si désactivé
     */
    public boolean toggleAdminAutoClaim(UUID uuid, String type) {
        adminAutoUnclaimPlayers.remove(uuid);
        if (type.equals(adminAutoClaimPlayers.get(uuid))) {
            adminAutoClaimPlayers.remove(uuid);
            return false;
        }
        adminAutoClaimPlayers.put(uuid, type);
        return true;
    }

    public boolean isAdminAutoClaiming(UUID uuid) {
        return adminAutoClaimPlayers.containsKey(uuid);
    }

    public String getAdminAutoClaimType(UUID uuid) {
        return adminAutoClaimPlayers.get(uuid);
    }

    public void stopAdminAutoClaim(UUID uuid) {
        adminAutoClaimPlayers.remove(uuid);
    }

    /** Active/désactive l'unclaim automatique d'un type de zone système. */
    public boolean toggleAdminAutoUnclaim(UUID uuid, String type) {
        adminAutoClaimPlayers.remove(uuid);
        if (type.equals(adminAutoUnclaimPlayers.get(uuid))) {
            adminAutoUnclaimPlayers.remove(uuid);
            return false;
        }
        adminAutoUnclaimPlayers.put(uuid, type);
        return true;
    }

    public boolean isAdminAutoUnclaiming(UUID uuid) {
        return adminAutoUnclaimPlayers.containsKey(uuid);
    }

    public String getAdminAutoUnclaimType(UUID uuid) {
        return adminAutoUnclaimPlayers.get(uuid);
    }

    public void stopAdminAutoUnclaim(UUID uuid) {
        adminAutoUnclaimPlayers.remove(uuid);
    }

    /** Snapshot des claims appartenant exactement à la zone demandée. */
    public List<FLocation> getZoneClaims(String type) {
        Faction zone = "warzone".equalsIgnoreCase(type)
            ? plugin.getFactionManager().getWarzone()
            : "safezone".equalsIgnoreCase(type)
                ? plugin.getFactionManager().getSafezone() : null;
        return zone == null ? new ArrayList<>() : new ArrayList<>(zone.getClaims());
    }

    /**
     * Unclaim sécurisé : ne retire le chunk que si son propriétaire actuel est
     * toujours la safezone/warzone demandée.
     */
    public boolean unclaimZone(String type, FLocation location) {
        String expectedId = "warzone".equalsIgnoreCase(type) ? Faction.WARZONE_ID
            : "safezone".equalsIgnoreCase(type) ? Faction.SAFEZONE_ID : null;
        if (expectedId == null || !expectedId.equals(claimIndex.get(location))) {
            return false;
        }
        return unclaim(location);
    }

    public int unclaimZone(String type, Collection<FLocation> locations) {
        int count = 0;
        for (FLocation location : locations) {
            if (unclaimZone(type, location)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Vérifie si un chunk est adjacent à un claim de faction
     * @param location La FLocation à vérifier
     * @param faction La faction
     * @return true si adjacent
     */
    public boolean isAdjacentToFaction(FLocation location, Faction faction) {
        for (FLocation adjacent : location.getAdjacent()) {
            if (faction.hasClaim(adjacent)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Calcule le nombre maximum de claims pour une faction
     * @param faction La faction
     * @return Le maximum de claims
     */
    public int getMaxClaims(Faction faction) {
        // Base: power total de la faction
        return (int) plugin.getPowerManager().getFactionPower(faction);
    }
    
    /**
     * Compte tous les claims dans un monde
     * @param worldName Nom du monde
     * @return Nombre de claims
     */
    public int getClaimsInWorld(String worldName) {
        int count = 0;
        for (FLocation loc : claimIndex.keySet()) {
            if (loc.getWorldName().equals(worldName)) {
                count++;
            }
        }
        return count;
    }
    
    // === Chargement index ===
    
    /**
     * Enregistre un claim dans l'index (utilisé lors du chargement)
     * @param location La FLocation
     * @param factionId ID de la faction
     */
    public void registerClaim(FLocation location, String factionId) {
        if (location != null && factionId != null) {
            claimIndex.put(location, factionId);
        }
    }
    
    /**
     * Reconstruit l'index à partir des factions
     */
    public void rebuildIndex() {
        claimIndex.clear();
        for (Faction faction : plugin.getFactionManager().getAllFactions()) {
            for (FLocation loc : faction.getClaims()) {
                claimIndex.put(loc, faction.getId());
            }
        }
        plugin.getLogger().info("Index de claims reconstruit: " + claimIndex.size() + " claims");
    }
    
    /**
     * @return Nombre total de claims
     */
    public int getTotalClaims() {
        return claimIndex.size();
    }
    
    // === Relations territoriales ===
    
    /**
     * Obtient la relation entre un joueur et le territoire où il se trouve
     * @param player Le joueur
     * @return La relation
     */
    public Relation getPlayerRelationToLocation(Player player) {
        Faction factionAt = getFactionAt(player.getLocation());
        Faction playerFaction = plugin.getFactionManager().getPlayerFaction(player);
        
        if (playerFaction == null) {
            return Relation.NEUTRAL;
        }
        
        return playerFaction.getRelationTo(factionAt);
    }
    
    /**
     * Résultats possibles d'une opération de claim
     */
    public static class ClaimResult {
        public static final ClaimResult SUCCESS = new ClaimResult(true, "Claim réussi");
        public static final ClaimResult ALREADY_OWNED = new ClaimResult(false, "Vous possédez déjà ce chunk");
        public static final ClaimResult OWNED_BY_OTHER = new ClaimResult(false, "Ce chunk appartient à une autre faction");
        public static final ClaimResult LIMIT_REACHED = new ClaimResult(false, "Limite de claims atteinte (power insuffisant)");
        public static final ClaimResult NOT_CONNECTED = new ClaimResult(false, "Le claim doit être connecté à votre territoire");
        public static final ClaimResult TOO_CLOSE_TO_SPAWN = new ClaimResult(false, "Trop proche du spawn");
        public static final ClaimResult NO_PERMISSION = new ClaimResult(false, "Vous n'avez pas la permission");
        public static final ClaimResult WORLD_DISABLED = new ClaimResult(false, "Les claims sont désactivés dans ce monde");
        public static final ClaimResult CANCELLED = new ClaimResult(false, "Claim annulé");
        
        private final boolean success;
        private final String message;
        
        public ClaimResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
        
        public String getMessage() {
            return message;
        }
        
        public boolean isSuccess() {
            return success;
        }
    }
    
    /**
     * Affiche la carte de la zone autour d'une position
     * @param player Le joueur à qui envoyer la carte
     * @param location La position centrale
     */
    public void showMap(Player player, FLocation location) {
        // TODO: Implémenter l'affichage complet de la carte
        player.sendMessage("§6[§eMap§6] §7Position: " + location.getX() + ", " + location.getZ());
        Faction factionAt = getFactionAt(location);
        player.sendMessage("§6[§eMap§6] §7Zone: " + factionAt.getName());
    }
}
