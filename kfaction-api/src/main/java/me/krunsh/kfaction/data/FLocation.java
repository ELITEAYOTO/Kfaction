package me.krunsh.kfaction.data;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Représente une position de chunk dans le monde
 * Utilisé pour le système de claims
 */
public class FLocation {
    
    private final String worldName;
    private final int x;
    private final int z;
    
    /**
     * Crée une FLocation à partir de coordonnées de chunk
     * @param worldName Nom du monde
     * @param x Coordonnée X du chunk
     * @param z Coordonnée Z du chunk
     */
    public FLocation(String worldName, int x, int z) {
        this.worldName = worldName;
        this.x = x;
        this.z = z;
    }
    
    /**
     * Crée une FLocation à partir d'un chunk Bukkit
     * @param chunk Le chunk
     */
    public FLocation(Chunk chunk) {
        this(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }
    
    /**
     * Crée une FLocation à partir d'une location Bukkit
     * @param location La location
     */
    public FLocation(Location location) {
        this(location.getWorld().getName(), 
             location.getBlockX() >> 4, 
             location.getBlockZ() >> 4);
    }
    
    /**
     * Crée une FLocation à partir d'un bloc
     * @param block Le bloc
     */
    public FLocation(Block block) {
        this(block.getLocation());
    }
    
    /**
     * Crée une FLocation à partir de la position d'un joueur
     * @param player Le joueur
     * @return La FLocation du joueur
     */
    public static FLocation fromPlayer(Player player) {
        return new FLocation(player.getLocation());
    }
    
    /**
     * @return Nom du monde
     */
    public String getWorldName() {
        return worldName;
    }
    
    /**
     * @return Coordonnée X du chunk
     */
    public int getX() {
        return x;
    }
    
    /**
     * @return Coordonnée Z du chunk
     */
    public int getZ() {
        return z;
    }
    
    /**
     * @return Le monde Bukkit ou null si non chargé
     */
    public World getWorld() {
        return Bukkit.getWorld(worldName);
    }
    
    /**
     * @return Le chunk Bukkit ou null si le monde n'est pas chargé
     */
    public Chunk getChunk() {
        World world = getWorld();
        if (world == null) return null;
        return world.getChunkAt(x, z);
    }
    
    /**
     * Vérifie si ce chunk est adjacent à un autre
     * @param other L'autre FLocation
     * @return true si les chunks sont adjacents (pas en diagonale)
     */
    public boolean isAdjacentTo(FLocation other) {
        if (!worldName.equals(other.worldName)) return false;
        int dx = Math.abs(x - other.x);
        int dz = Math.abs(z - other.z);
        return (dx == 1 && dz == 0) || (dx == 0 && dz == 1);
    }
    
    /**
     * Obtient la FLocation relative à celle-ci
     * @param dx Décalage X
     * @param dz Décalage Z
     * @return La FLocation décalée
     */
    public FLocation getRelative(int dx, int dz) {
        return new FLocation(worldName, x + dx, z + dz);
    }
    
    /**
     * @return Les 4 FLocations adjacentes (nord, sud, est, ouest)
     */
    public FLocation[] getAdjacent() {
        return new FLocation[] {
            getRelative(1, 0),   // Est
            getRelative(-1, 0),  // Ouest
            getRelative(0, 1),   // Sud
            getRelative(0, -1)   // Nord
        };
    }
    
    /**
     * Vérifie si une location Bukkit est dans ce chunk
     * @param location La location à vérifier
     * @return true si la location est dans ce chunk
     */
    public boolean contains(Location location) {
        if (!location.getWorld().getName().equals(worldName)) return false;
        int locX = location.getBlockX() >> 4;
        int locZ = location.getBlockZ() >> 4;
        return locX == x && locZ == z;
    }
    
    /**
     * Calcule la distance en chunks vers une autre FLocation
     * @param other L'autre FLocation
     * @return La distance (Manhattan distance) ou -1 si mondes différents
     */
    public int distanceTo(FLocation other) {
        if (!worldName.equals(other.worldName)) return -1;
        return Math.abs(x - other.x) + Math.abs(z - other.z);
    }
    
    /**
     * @return Clé unique pour stockage/cache
     */
    public String getKey() {
        return worldName + ":" + x + ":" + z;
    }
    
    /**
     * Parse une clé de stockage
     * @param key La clé (format: "world:x:z")
     * @return La FLocation ou null si format invalide
     */
    public static FLocation fromKey(String key) {
        if (key == null) return null;
        String[] parts = key.split(":");
        if (parts.length != 3) return null;
        try {
            return new FLocation(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * @return Représentation pour les logs/debug
     */
    @Override
    public String toString() {
        return "FLocation{world=" + worldName + ", x=" + x + ", z=" + z + "}";
    }
    
    /**
     * @return Format lisible pour les joueurs
     */
    public String toReadableString() {
        return worldName + " (" + x + ", " + z + ")";
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FLocation fLocation = (FLocation) o;
        return x == fLocation.x && z == fLocation.z && worldName.equals(fLocation.worldName);
    }
    
    @Override
    public int hashCode() {
        // Pas de Objects.hash() — évite l'autoboxing int→Integer (hot path: chaque block event)
        int h = worldName.hashCode();
        h = 31 * h + x;
        h = 31 * h + z;
        return h;
    }
}
