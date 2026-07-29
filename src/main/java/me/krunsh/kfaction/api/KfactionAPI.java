package me.krunsh.kfaction.api;

import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FLocation;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.Relation;

/**
 * API publique de Kfaction pour les plugins externes
 * 
 * Utilisation:
 * KfactionAPI api = Kfaction.getInstance().getAPI();
 * 
 * Ou via le ServiceProvider Bukkit:
 * RegisteredServiceProvider<KfactionAPI> rsp = Bukkit.getServicesManager().getRegistration(KfactionAPI.class);
 * KfactionAPI api = rsp.getProvider();
 */
public class KfactionAPI {

    private final Kfaction plugin;

    public KfactionAPI(Kfaction plugin) {
        this.plugin = plugin;
    }

    // ==================== FACTIONS ====================

    /**
     * Obtient une faction par son ID
     * @param factionId L'ID de la faction
     * @return La faction ou null
     */
    public Faction getFaction(String factionId) {
        return plugin.getFactionManager().getFaction(factionId);
    }

    /**
     * Obtient une faction par son tag/nom
     * @param tag Le tag de la faction
     * @return La faction ou null
     */
    public Faction getFactionByTag(String tag) {
        return plugin.getFactionManager().getFactionByName(tag);
    }

    /**
     * Obtient la faction d'un joueur
     * @param player Le joueur
     * @return La faction ou null si sans faction
     */
    public Faction getPlayerFaction(Player player) {
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        if (fPlayer == null || !fPlayer.hasFaction()) return null;
        return plugin.getFactionManager().getFaction(fPlayer.getFactionId());
    }

    /**
     * Obtient la faction d'un joueur par UUID
     * @param uuid L'UUID du joueur
     * @return La faction ou null
     */
    public Faction getPlayerFaction(UUID uuid) {
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(uuid);
        if (fPlayer == null || !fPlayer.hasFaction()) return null;
        return plugin.getFactionManager().getFaction(fPlayer.getFactionId());
    }

    /**
     * Vérifie si un joueur est dans une faction
     * @param player Le joueur
     * @return true si dans une faction
     */
    public boolean hasFaction(Player player) {
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        return fPlayer != null && fPlayer.hasFaction();
    }

    /**
     * Obtient toutes les factions normales (pas wilderness/warzone/safezone)
     * @return Liste des factions
     */
    public List<Faction> getAllFactions() {
        return new java.util.ArrayList<>(plugin.getFactionManager().getPlayerFactions());
    }

    // ==================== FPLAYER ====================

    /**
     * Obtient les données faction d'un joueur
     * @param player Le joueur
     * @return Le FPlayer
     */
    public FPlayer getFPlayer(Player player) {
        return plugin.getFPlayerManager().getFPlayer(player);
    }

    /**
     * Obtient les données faction d'un joueur par UUID
     * @param uuid L'UUID du joueur
     * @return Le FPlayer ou null
     */
    public FPlayer getFPlayer(UUID uuid) {
        return plugin.getFPlayerManager().getFPlayer(uuid);
    }

    // ==================== POWER ====================

    /**
     * Obtient le power d'un joueur
     * @param player Le joueur
     * @return Le power actuel
     */
    public double getPlayerPower(Player player) {
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        return fPlayer != null ? fPlayer.getPower() : 0;
    }

    /**
     * Obtient le power max d'un joueur
     * @param player Le joueur
     * @return Le power maximum
     */
    public double getPlayerMaxPower(Player player) {
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        return fPlayer != null ? plugin.getPowerManager().getPlayerMaxPower(player.getUniqueId()) : 0;
    }

    /**
     * Obtient le power d'une faction
     * @param faction La faction
     * @return Le power total
     */
    public double getFactionPower(Faction faction) {
        return faction != null ? faction.getPower() : 0;
    }

    /**
     * Modifie le power d'un joueur (admin)
     * @param uuid L'UUID du joueur
     * @param power Le nouveau power
     */
    public void setPlayerPower(UUID uuid, double power) {
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(uuid);
        if (fPlayer != null) {
            fPlayer.setPower(power);
        }
    }

    // ==================== CLAIMS ====================

    /**
     * Obtient la faction propriétaire d'un chunk
     * @param location La location
     * @return La faction ou null (wilderness)
     */
    public Faction getFactionAt(Location location) {
        FLocation fLoc = new FLocation(location);
        return plugin.getClaimManager().getFactionAt(fLoc);
    }

    /**
     * Vérifie si un chunk est claim
     * @param location La location
     * @return true si claimé
     */
    public boolean isClaimed(Location location) {
        Faction faction = getFactionAt(location);
        return faction != null && !faction.isWilderness();
    }

    /**
     * Vérifie si une location est en safezone
     * @param location La location
     * @return true si safezone
     */
    public boolean isSafezone(Location location) {
        Faction faction = getFactionAt(location);
        return faction != null && faction.isSafezone();
    }

    /**
     * Vérifie si une location est en warzone
     * @param location La location
     * @return true si warzone
     */
    public boolean isWarzone(Location location) {
        Faction faction = getFactionAt(location);
        return faction != null && faction.isWarzone();
    }

    /**
     * Obtient le nombre de claims d'une faction
     * @param faction La faction
     * @return Nombre de claims
     */
    public int getFactionClaimCount(Faction faction) {
        return faction != null ? faction.getClaimCount() : 0;
    }

    // ==================== RELATIONS ====================

    /**
     * Obtient la relation entre deux joueurs
     * @param player1 Premier joueur
     * @param player2 Deuxième joueur
     * @return La relation
     */
    public Relation getRelation(Player player1, Player player2) {
        FPlayer fp1 = plugin.getFPlayerManager().getFPlayer(player1);
        FPlayer fp2 = plugin.getFPlayerManager().getFPlayer(player2);
        
        if (fp1 == null || fp2 == null) return Relation.NEUTRAL;
        if (!fp1.hasFaction() || !fp2.hasFaction()) return Relation.NEUTRAL;
        
        Faction f1 = plugin.getFactionManager().getFaction(fp1.getFactionId());
        Faction f2 = plugin.getFactionManager().getFaction(fp2.getFactionId());
        if (f1 == null || f2 == null) return Relation.NEUTRAL;
        
        return f1.getRelationTo(f2);
    }

    /**
     * Obtient la relation entre deux factions
     * @param faction1 Première faction
     * @param faction2 Deuxième faction
     * @return La relation
     */
    public Relation getRelation(Faction faction1, Faction faction2) {
        if (faction1 == null || faction2 == null) return Relation.NEUTRAL;
        return faction1.getRelationTo(faction2);
    }

    /**
     * Vérifie si deux joueurs sont alliés
     * @param player1 Premier joueur
     * @param player2 Deuxième joueur
     * @return true si alliés
     */
    public boolean areAllies(Player player1, Player player2) {
        return getRelation(player1, player2) == Relation.ALLY;
    }

    /**
     * Vérifie si deux joueurs sont ennemis
     * @param player1 Premier joueur
     * @param player2 Deuxième joueur
     * @return true si ennemis
     */
    public boolean areEnemies(Player player1, Player player2) {
        return getRelation(player1, player2) == Relation.ENEMY;
    }

    /**
     * Vérifie si deux joueurs sont dans la même faction
     * @param player1 Premier joueur
     * @param player2 Deuxième joueur
     * @return true si même faction
     */
    public boolean areSameFaction(Player player1, Player player2) {
        return getRelation(player1, player2) == Relation.MEMBER;
    }

    // ==================== TERRITORY ====================

    /**
     * Vérifie si un joueur peut construire à une location
     * @param player Le joueur
     * @param location La location
     * @return true si autorisé
     */
    public boolean canBuild(Player player, Location location) {
        return plugin.getTerritoryManager().canPerformAction(
            player, location, me.krunsh.kfaction.data.PermissionAction.BUILD);
    }

    /**
     * Vérifie si un joueur peut casser un bloc à une location
     * @param player Le joueur
     * @param location La location
     * @return true si autorisé
     */
    public boolean canBreak(Player player, Location location) {
        return plugin.getTerritoryManager().canPerformAction(
            player, location, me.krunsh.kfaction.data.PermissionAction.DESTROY);
    }

    /**
     * Vérifie si un joueur peut interagir à une location
     * @param player Le joueur
     * @param location La location
     * @return true si autorisé
     */
    public boolean canInteract(Player player, Location location) {
        return plugin.getTerritoryManager().canPerformAction(
            player, location, me.krunsh.kfaction.data.PermissionAction.SWITCH);
    }

    // ==================== RAIDING ====================

    /**
     * Vérifie si une faction est raidable (surclaim possible)
     * @param faction La faction
     * @return true si raidable
     */
    public boolean isRaidable(Faction faction) {
        if (faction == null || faction.isSystemFaction()) return false;
        return plugin.getClaimManager().isRaidable(faction);
    }

    /**
     * Obtient le nombre de chunks surclaimables
     * @param faction La faction
     * @return Nombre de chunks surclaimables
     */
    public int getSurclaimableChunks(Faction faction) {
        if (faction == null || faction.isSystemFaction()) return 0;
        return plugin.getClaimManager().getOverclaimableCount(faction);
    }

    // ==================== UTILS ====================

    /**
     * Vérifie si un joueur est en bypass admin
     * @param player Le joueur
     * @return true si en bypass
     */
    public boolean isBypassing(Player player) {
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        return fPlayer != null && fPlayer.isBypassing();
    }
}
