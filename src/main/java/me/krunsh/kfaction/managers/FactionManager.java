package me.krunsh.kfaction.managers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionRole;
import me.krunsh.kfaction.policy.FactionNamePolicy;

/**
 * Gestionnaire principal des factions
 * Gère le cache en mémoire et les opérations CRUD
 */
public class FactionManager {
    
    private final Kfaction plugin;
    
    // Cache des factions par ID
    private final Map<String, Faction> factionsById;
    
    // Index par nom (lowercase) pour recherche rapide
    private final Map<String, String> factionNameIndex;
    
    // Index par tag (lowercase) pour recherche rapide O(1)
    private final Map<String, String> factionTagIndex;
    
    // Factions système
    private Faction wilderness;
    private Faction safezone;
    private Faction warzone;
    
    public FactionManager(Kfaction plugin) {
        this.plugin = plugin;
        this.factionsById = new ConcurrentHashMap<>();
        this.factionNameIndex = new ConcurrentHashMap<>();
        this.factionTagIndex = new ConcurrentHashMap<>();
    }
    
    /**
     * Initialise le manager et charge les factions système
     */
    public void initialize() {
        // Créer les factions système
        wilderness = new Faction(Faction.WILDERNESS_ID);
        safezone = new Faction(Faction.SAFEZONE_ID);
        warzone = new Faction(Faction.WARZONE_ID);
        
        factionsById.put(wilderness.getId(), wilderness);
        factionsById.put(safezone.getId(), safezone);
        factionsById.put(warzone.getId(), warzone);
        
        plugin.getLogger().info("FactionManager initialisé avec les factions système");
    }
    
    /**
     * Ferme le manager et sauvegarde les données
     */
    public void shutdown() {
        // La sauvegarde est gérée par StorageManager
        factionsById.clear();
        factionNameIndex.clear();
        factionTagIndex.clear();
    }
    
    // === Opérations CRUD ===
    
    /**
     * Crée une nouvelle faction
     * @param name Nom de la faction
     * @param leader UUID du joueur créateur
     * @return La faction créée ou null en cas d'erreur
     */
    public Faction createFaction(String name, UUID leader) {
        // Vérifier si le nom est déjà pris
        if (getFactionByName(name) != null) {
            return null;
        }
        
        // Vérifier si le joueur est déjà dans une faction
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(leader);
        if (fPlayer != null && fPlayer.hasFaction()) {
            return null;
        }
        
        // Générer un ID unique
        String id = UUID.randomUUID().toString();
        
        // Créer la faction
        Faction faction = new Faction(id, name, leader);
        
        // Ajouter au cache
        factionsById.put(id, faction);
        factionNameIndex.put(name.toLowerCase(), id);
        if (faction.getTag() != null) {
            factionTagIndex.put(faction.getTag().toLowerCase(), id);
        }
        
        // Mettre à jour le FPlayer
        if (fPlayer != null) {
            fPlayer.joinFaction(id, FactionRole.LEADER);
            // Mettre à jour l'index faction→joueur
            plugin.getFPlayerManager().notifyFactionChange(leader, null, id);
        }
        
        // Marquer pour sauvegarde
        plugin.getStorageManager().markDirty(faction);
        
        return faction;
    }
    
    /**
     * Supprime une faction
     * @param faction La faction à supprimer
     * @return true si supprimée avec succès
     */
    public boolean disbandFaction(Faction faction) {
        if (faction == null || faction.isSystemFaction()) {
            return false;
        }
        
        String id = faction.getId();
        
        // Retirer tous les membres
        for (UUID memberUuid : faction.getMembers()) {
            FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(memberUuid);
            if (fPlayer != null) {
                String oldFactionId = fPlayer.getFactionId();
                fPlayer.leaveFaction();
                plugin.getFPlayerManager().notifyFactionChange(memberUuid, oldFactionId, null);
                plugin.getStorageManager().markDirty(fPlayer);
            }
        }
        
        // Libérer tous les claims
        plugin.getClaimManager().unclaimAll(faction);
        
        // Retirer les relations
        for (String otherId : faction.getAllRelations().keySet()) {
            Faction other = getFaction(otherId);
            if (other != null) {
                other.setRelation(id, null);
            }
        }
        
        // Retirer du cache
        factionsById.remove(id);
        factionNameIndex.remove(faction.getName().toLowerCase());
        if (faction.getTag() != null) {
            factionTagIndex.remove(faction.getTag().toLowerCase());
        }
        
        // Supprimer du stockage
        plugin.getStorageManager().deleteFaction(id);
        
        return true;
    }
    
    // === Recherche ===
    
    /**
     * Obtient une faction par son ID
     * @param id L'identifiant unique
     * @return La faction ou null
     */
    public Faction getFaction(String id) {
        if (id == null) return null;
        return factionsById.get(id);
    }
    
    /**
     * Obtient une faction par son nom (insensible à la casse)
     * @param name Le nom de la faction
     * @return La faction ou null
     */
    public Faction getFactionByName(String name) {
        if (name == null) return null;
        String id = factionNameIndex.get(name.toLowerCase());
        if (id == null) return null;
        return factionsById.get(id);
    }
    
    /**
     * Obtient une faction par son tag (insensible à la casse) - O(1) via index
     * @param tag Le tag de la faction
     * @return La faction ou null
     */
    public Faction getFactionByTag(String tag) {
        if (tag == null) return null;
        String id = factionTagIndex.get(tag.toLowerCase());
        if (id == null) return null;
        return factionsById.get(id);
    }
    
    /**
     * Met à jour le tag d'une faction et maintient l'index
     * @param faction La faction
     * @param newTag Le nouveau tag
     */
    public void updateFactionTag(Faction faction, String newTag) {
        if (faction == null) return;
        // Retirer l'ancien index
        if (faction.getTag() != null) {
            factionTagIndex.remove(faction.getTag().toLowerCase());
        }
        // Mettre à jour le tag
        faction.setTag(newTag);
        // Ajouter le nouvel index
        if (newTag != null) {
            factionTagIndex.put(newTag.toLowerCase(), faction.getId());
        }
        plugin.getStorageManager().markDirty(faction);
    }
    
    /**
     * Obtient la faction d'un joueur
     * @param playerUuid UUID du joueur
     * @return La faction ou null
     */
    public Faction getPlayerFaction(UUID playerUuid) {
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(playerUuid);
        if (fPlayer == null || !fPlayer.hasFaction()) {
            return null;
        }
        return getFaction(fPlayer.getFactionId());
    }
    
    /**
     * Obtient la faction d'un joueur
     * @param player Le joueur
     * @return La faction ou null
     */
    public Faction getPlayerFaction(Player player) {
        return getPlayerFaction(player.getUniqueId());
    }
    
    /**
     * @return Collection de toutes les factions (incluant système)
     */
    public Collection<Faction> getAllFactions() {
        return Collections.unmodifiableCollection(factionsById.values());
    }
    
    /**
     * @return Collection des factions joueurs uniquement
     */
    public Collection<Faction> getPlayerFactions() {
        return factionsById.values().stream()
                .filter(f -> !f.isSystemFaction())
                .collect(Collectors.toList());
    }
    
    /**
     * @return Le nombre total de factions joueurs
     */
    public int getFactionCount() {
        return (int) factionsById.values().stream()
                .filter(f -> !f.isSystemFaction())
                .count();
    }
    
    // === Factions système ===
    
    public Faction getWilderness() {
        return wilderness;
    }
    
    public Faction getSafezone() {
        return safezone;
    }
    
    public Faction getWarzone() {
        return warzone;
    }
    
    // === Gestion des membres ===
    
    /**
     * Ajoute un joueur à une faction
     * @param faction La faction cible
     * @param playerUuid UUID du joueur
     * @param role Rôle initial
     * @return true si ajouté avec succès
     */
    public boolean addMember(Faction faction, UUID playerUuid, FactionRole role) {
        if (faction == null || faction.isSystemFaction()) return false;
        
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(playerUuid);
        if (fPlayer == null) return false;
        
        // Vérifier s'il n'est pas déjà dans une faction
        if (fPlayer.hasFaction()) return false;
        
        // Ajouter à la faction
        if (!faction.addMember(playerUuid, role)) return false;
        
        // Mettre à jour le FPlayer
        fPlayer.joinFaction(faction.getId(), role);
        plugin.getFPlayerManager().notifyFactionChange(playerUuid, null, faction.getId());
        
        // Marquer pour sauvegarde
        plugin.getStorageManager().markDirty(faction);
        plugin.getStorageManager().markDirty(fPlayer);
        
        return true;
    }
    
    /**
     * Retire un joueur d'une faction
     * @param faction La faction
     * @param playerUuid UUID du joueur
     * @return true si retiré avec succès
     */
    public boolean removeMember(Faction faction, UUID playerUuid) {
        if (faction == null || faction.isSystemFaction()) return false;
        
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(playerUuid);
        
        // Retirer de la faction
        if (!faction.removeMember(playerUuid)) return false;
        
        // Mettre à jour le FPlayer
        if (fPlayer != null) {
            String oldFactionId = fPlayer.getFactionId();
            fPlayer.leaveFaction();
            plugin.getFPlayerManager().notifyFactionChange(playerUuid, oldFactionId, null);
            plugin.getStorageManager().markDirty(fPlayer);
        }
        
        // Dissoudre si plus de membres
        if (faction.getMemberCount() == 0) {
            disbandFaction(faction);
        } else {
            plugin.getStorageManager().markDirty(faction);
        }
        
        return true;
    }
    
    /**
     * Change le rôle d'un membre
     * @param faction La faction
     * @param playerUuid UUID du joueur
     * @param newRole Nouveau rôle
     * @return true si changé avec succès
     */
    public boolean setMemberRole(Faction faction, UUID playerUuid, FactionRole newRole) {
        if (faction == null || faction.isSystemFaction()) return false;
        if (!faction.isMember(playerUuid)) return false;
        
        faction.setRole(playerUuid, newRole);
        
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(playerUuid);
        if (fPlayer != null) {
            fPlayer.setRole(newRole);
            plugin.getStorageManager().markDirty(fPlayer);
        }
        
        plugin.getStorageManager().markDirty(faction);
        return true;
    }
    
    /**
     * Transfère le leadership à un autre membre
     * @param faction La faction
     * @param newLeader UUID du nouveau leader
     * @return true si transféré avec succès
     */
    public boolean transferLeadership(Faction faction, UUID newLeader) {
        if (faction == null || faction.isSystemFaction()) return false;
        if (!faction.isMember(newLeader)) return false;
        
        UUID oldLeader = faction.getLeader();
        
        // Le nouveau devient LEADER
        faction.setLeader(newLeader);
        
        FPlayer newLeaderFP = plugin.getFPlayerManager().getFPlayer(newLeader);
        if (newLeaderFP != null) {
            newLeaderFP.setRole(FactionRole.LEADER);
            plugin.getStorageManager().markDirty(newLeaderFP);
        }
        
        // L'ancien devient COLEADER
        if (oldLeader != null) {
            faction.setRole(oldLeader, FactionRole.COLEADER);
            FPlayer oldLeaderFP = plugin.getFPlayerManager().getFPlayer(oldLeader);
            if (oldLeaderFP != null) {
                oldLeaderFP.setRole(FactionRole.COLEADER);
                plugin.getStorageManager().markDirty(oldLeaderFP);
            }
        }
        
        plugin.getStorageManager().markDirty(faction);
        return true;
    }
    
    // === Validation ===
    
    /**
     * Vérifie si un nom de faction est valide
     * @param name Le nom à vérifier
     * @return true si valide
     */
    public boolean isValidName(String name) {
        if (name == null || name.isEmpty()) return false;
        
        int minLength = plugin.getConfigManager().getInt("factions.name.min-length", 3);
        int maxLength = plugin.getConfigManager().getInt("factions.name.max-length", 16);
        String pattern = plugin.getConfigManager().getString("factions.name.regex",
            plugin.getConfigManager().getString("factions.name.pattern", "^[a-zA-Z0-9_]+$"));
        
        List<String> reserved = new ArrayList<>(
            plugin.getConfigManager().getStringList("factions.name.blocked-words"));
        reserved.addAll(Arrays.asList("wilderness", "safezone", "warzone", "admin", "server"));
        return FactionNamePolicy.isValid(name, minLength, maxLength, pattern, reserved);
        
        // Vérifier les noms réservés
    }
    
    /**
     * Vérifie si un nom de faction est disponible
     * @param name Le nom à vérifier
     * @return true si disponible
     */
    public boolean isNameAvailable(String name) {
        return getFactionByName(name) == null;
    }
    
    // === Chargement depuis stockage ===
    
    /**
     * Charge une faction dans le cache
     * Appelé par StorageManager lors du chargement
     * @param faction La faction à charger
     */
    public void loadFaction(Faction faction) {
        if (faction == null) return;
        factionsById.put(faction.getId(), faction);
        factionNameIndex.put(faction.getName().toLowerCase(), faction.getId());
        if (faction.getTag() != null) {
            factionTagIndex.put(faction.getTag().toLowerCase(), faction.getId());
        }
    }
    
    /**
     * Renomme une faction
     * @param faction La faction
     * @param newName Nouveau nom
     * @return true si renommée avec succès
     */
    public boolean renameFaction(Faction faction, String newName) {
        if (faction == null || faction.isSystemFaction()) return false;
        if (!isValidName(newName) || !isNameAvailable(newName)) return false;
        
        // Mettre à jour l'index nom
        factionNameIndex.remove(faction.getName().toLowerCase());
        faction.setName(newName);
        factionNameIndex.put(newName.toLowerCase(), faction.getId());
        
        plugin.getStorageManager().markDirty(faction);
        return true;
    }
}
