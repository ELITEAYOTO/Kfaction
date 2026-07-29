package me.krunsh.kfaction.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.progression.FactionProgressState;

/**
 * Représente une faction dans le système
 * Thread-safe pour les accès concurrents
 */
public class Faction {
    
    // === Identifiants ===
    private final String id;
    private String name;
    private String tag;
    private String description;
    
    // === Membres ===
    private UUID leader;
    private final Set<UUID> members;
    private final Map<UUID, FactionRole> memberRoles;
    
    // === Territoire ===
    private final Set<FLocation> claims;
    private Location home;
    private final Map<String, Location> warps;  // nom -> location
    
    // === Power ===
    private double powerBoost;
    
    // === Relations ===
    private final Map<String, Relation> relations;      // factionId -> relation
    private final Map<String, Long> relationRequests;   // factionId -> timestamp
    
    // === Économie ===
    private double bank;
    
    // === Permissions ===
    private final Map<FactionRole, Set<PermissionAction>> permissions;
    private final Map<Relation, Set<PermissionAction>> relationPermissions;
    
    // === Métadonnées ===
    private final long createdAt;
    private long lastActivity;
    private boolean permanent;
    private boolean open;
    
    // === TNT / Raiding (optionnel) ===
    private int tntBank;
    private boolean tntEnabled;
    
    // === Système de Niveaux ===
    private int level;
    private int currentXp;
    private QuestCategory activeCategory;
    private final List<FactionQuest> activeQuests;
    private final FactionProgressState progressionState;
    private final Set<String> appliedRewards;  // clés "level_X_reward_Y" déjà appliquées
    
    // === Récompenses de niveau (valeurs cumulées) ===
    private int chestSize;           // 0 = pas débloqué, 36 ou 54
    private String chestContentsB64; // ItemStack[] sérialisé en Base64
    private boolean factionFlyEnabled;
    private boolean antiSethomeEnabled;
    private int extraWarps;          // warps bonus (ajouté au max config)
    private int extraMembers;        // membres bonus
    private double extraPowerBoost;  // power bonus de niveaux
    
    // === Factions système ===
    public static final String WILDERNESS_ID = "wilderness";
    public static final String SAFEZONE_ID = "safezone";
    public static final String WARZONE_ID = "warzone";
    
    /**
     * Crée une nouvelle faction
     * @param id Identifiant unique (UUID format recommandé)
     * @param name Nom de la faction
     * @param leader UUID du chef
     */
    public Faction(String id, String name, UUID leader) {
        this.id = id;
        this.name = name;
        this.tag = name.length() > 4 ? name.substring(0, 4) : name;
        this.description = "";
        this.leader = leader;
        this.members = Collections.newSetFromMap(new ConcurrentHashMap<>());
        this.memberRoles = new ConcurrentHashMap<>();
        this.claims = Collections.newSetFromMap(new ConcurrentHashMap<>());
        this.warps = new ConcurrentHashMap<>();
        this.relations = new ConcurrentHashMap<>();
        this.relationRequests = new ConcurrentHashMap<>();
        this.permissions = new ConcurrentHashMap<>();
        this.relationPermissions = new ConcurrentHashMap<>();
        this.createdAt = System.currentTimeMillis();
        this.lastActivity = System.currentTimeMillis();
        this.permanent = false;
        this.open = false;
        this.bank = 0.0;
        this.tntBank = 0;
        this.tntEnabled = true;
        this.powerBoost = 0.0;
        
        // Système de niveaux
        this.level = 0;
        this.currentXp = 0;
        this.activeCategory = null;
        this.activeQuests = new ArrayList<>();
        this.progressionState = new FactionProgressState();
        this.appliedRewards = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
        this.chestSize = 0;
        this.chestContentsB64 = null;
        this.factionFlyEnabled = false;
        this.antiSethomeEnabled = false;
        this.extraWarps = 0;
        this.extraMembers = 0;
        this.extraPowerBoost = 0.0;
        
        // Ajouter le leader comme membre
        if (leader != null) {
            this.members.add(leader);
            this.memberRoles.put(leader, FactionRole.LEADER);
        }
        
        initDefaultPermissions();
    }
    
    /**
     * Constructeur pour les factions système (Wilderness, Safezone, Warzone)
     * @param id L'identifiant système
     */
    public Faction(String id) {
        this.id = id;
        this.name = formatSystemName(id);
        this.tag = formatSystemTag(id);
        this.description = "";
        this.leader = null;
        this.members = Collections.newSetFromMap(new ConcurrentHashMap<>());
        this.memberRoles = new ConcurrentHashMap<>();
        this.claims = Collections.newSetFromMap(new ConcurrentHashMap<>());
        this.warps = new ConcurrentHashMap<>();
        this.relations = new ConcurrentHashMap<>();
        this.relationRequests = new ConcurrentHashMap<>();
        this.permissions = new ConcurrentHashMap<>();
        this.relationPermissions = new ConcurrentHashMap<>();
        this.createdAt = System.currentTimeMillis();
        this.lastActivity = System.currentTimeMillis();
        this.permanent = true;
        this.open = false;
        this.bank = 0.0;
        this.tntBank = 0;
        this.tntEnabled = false;
        
        // Système de niveaux (système factions)
        this.level = 0;
        this.currentXp = 0;
        this.activeCategory = null;
        this.activeQuests = new ArrayList<>();
        this.progressionState = new FactionProgressState();
        this.appliedRewards = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
        this.chestSize = 0;
        this.chestContentsB64 = null;
        this.factionFlyEnabled = false;
        this.antiSethomeEnabled = false;
        this.extraWarps = 0;
        this.extraMembers = 0;
        this.extraPowerBoost = 0.0;
    }
    
    // === Getters & Setters de base ===
    
    public String getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
        updateActivity();
    }
    
    public String getTag() {
        return tag;
    }
    
    public void setTag(String tag) {
        this.tag = tag;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
        updateActivity();
    }
    
    // === Membres ===
    
    public UUID getLeader() {
        return leader;
    }
    
    public void setLeader(UUID leader) {
        // Mettre à jour les rôles
        if (this.leader != null) {
            memberRoles.put(this.leader, FactionRole.COLEADER);
        }
        this.leader = leader;
        if (leader != null) {
            memberRoles.put(leader, FactionRole.LEADER);
        }
        updateActivity();
    }
    
    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(members);
    }
    
    public int getMemberCount() {
        return members.size();
    }
    
    public boolean isMember(UUID uuid) {
        return members.contains(uuid);
    }
    
    public boolean isLeader(UUID uuid) {
        return leader != null && leader.equals(uuid);
    }
    
    /**
     * Ajoute un membre à la faction
     * @param uuid UUID du joueur
     * @param role Rôle initial
     * @return true si ajouté avec succès
     */
    public boolean addMember(UUID uuid, FactionRole role) {
        if (members.add(uuid)) {
            memberRoles.put(uuid, role);
            updateActivity();
            return true;
        }
        return false;
    }
    
    /**
     * Ajoute un membre avec le rôle RECRUIT par défaut
     */
    public boolean addMember(UUID uuid) {
        return addMember(uuid, FactionRole.RECRUIT);
    }
    
    /**
     * Retire un membre de la faction
     * @param uuid UUID du joueur
     * @return true si retiré avec succès
     */
    public boolean removeMember(UUID uuid) {
        if (members.remove(uuid)) {
            memberRoles.remove(uuid);
            if (uuid.equals(leader)) {
                // Promouvoir automatiquement un remplaçant si possible
                promoteNewLeader();
            }
            updateActivity();
            return true;
        }
        return false;
    }
    
    public FactionRole getRole(UUID uuid) {
        return memberRoles.getOrDefault(uuid, null);
    }
    
    public void setRole(UUID uuid, FactionRole role) {
        if (members.contains(uuid)) {
            memberRoles.put(uuid, role);
            if (role == FactionRole.LEADER) {
                leader = uuid;
            }
            updateActivity();
        }
    }
    
    /**
     * Obtient tous les membres d'un rang spécifique ou supérieur
     * @param minRole Rang minimum
     * @return Set de UUIDs
     */
    public Set<UUID> getMembersWithMinRole(FactionRole minRole) {
        Set<UUID> result = new HashSet<>();
        for (Map.Entry<UUID, FactionRole> entry : memberRoles.entrySet()) {
            if (entry.getValue().isAtLeast(minRole)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }
    
    /**
     * @return Liste des joueurs en ligne de cette faction
     */
    public List<Player> getOnlinePlayers() {
        List<Player> online = new ArrayList<>();
        for (UUID uuid : members) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                online.add(player);
            }
        }
        return online;
    }
    
    // === Territoire ===
    
    public Set<FLocation> getClaims() {
        return Collections.unmodifiableSet(claims);
    }
    
    public int getClaimCount() {
        return claims.size();
    }
    
    public boolean hasClaim(FLocation location) {
        return claims.contains(location);
    }
    
    public boolean addClaim(FLocation location) {
        if (claims.add(location)) {
            updateActivity();
            return true;
        }
        return false;
    }
    
    public boolean removeClaim(FLocation location) {
        if (claims.remove(location)) {
            updateActivity();
            return true;
        }
        return false;
    }
    
    public void clearClaims() {
        claims.clear();
        updateActivity();
    }
    
    public Location getHome() {
        return home;
    }
    
    public void setHome(Location home) {
        this.home = home;
        updateActivity();
    }
    
    public boolean hasHome() {
        return home != null;
    }
    
    // === Warps ===
    
    public Map<String, Location> getWarps() {
        return Collections.unmodifiableMap(warps);
    }
    
    public Location getWarp(String name) {
        return warps.get(name.toLowerCase());
    }
    
    public boolean hasWarp(String name) {
        return warps.containsKey(name.toLowerCase());
    }
    
    public void setWarp(String name, Location location) {
        warps.put(name.toLowerCase(), location);
        updateActivity();
    }
    
    public boolean removeWarp(String name) {
        Location removed = warps.remove(name.toLowerCase());
        if (removed != null) {
            updateActivity();
            return true;
        }
        return false;
    }
    
    public int getWarpCount() {
        return warps.size();
    }
    
    public Set<String> getWarpNames() {
        return Collections.unmodifiableSet(warps.keySet());
    }
    
    // === Power ===
    
    public double getPowerBoost() {
        return powerBoost;
    }
    
    public void setPowerBoost(double powerBoost) {
        this.powerBoost = powerBoost;
    }
    
    /**
     * Calcule le power total de la faction
     * NOTE: Nécessite FPlayerManager pour obtenir le power des membres
     * Cette méthode est un placeholder - le calcul réel est fait par PowerManager
     * @return Le power boost de faction uniquement
     */
    public double getFactionPowerBoost() {
        return powerBoost;
    }
    
    /**
     * Retourne le power total de la faction
     * Calcule la somme du power des membres + le powerBoost
     * @return Le power total de la faction
     */
    public double getPower() {
        me.krunsh.kfaction.Kfaction plugin = me.krunsh.kfaction.Kfaction.getInstance();
        if (plugin != null && plugin.getPowerManager() != null) {
            return plugin.getPowerManager().getFactionPower(this);
        }
        // Fallback si plugin non initialisé
        return powerBoost;
    }
    
    /**
     * Retourne le power maximum de la faction
     * Calcule la somme du max power des membres + le powerBoost
     * @return Le power max total de la faction
     */
    public double getMaxPower() {
        me.krunsh.kfaction.Kfaction plugin = me.krunsh.kfaction.Kfaction.getInstance();
        if (plugin != null && plugin.getPowerManager() != null) {
            return plugin.getPowerManager().getFactionMaxPower(this);
        }
        // Fallback si plugin non initialisé
        return powerBoost;
    }
    
    /**
     * Alias pour getBank() - retourne la balance de la faction
     * @return La balance de la faction
     */
    public double getBalance() {
        return bank;
    }
    
    // === Relations ===
    
    public Relation getRelationTo(Faction other) {
        if (other == null) return Relation.NEUTRAL;
        if (this.id.equals(other.id)) return Relation.MEMBER;
        return relations.getOrDefault(other.getId(), Relation.NEUTRAL);
    }
    
    public Relation getRelationTo(String factionId) {
        if (this.id.equals(factionId)) return Relation.MEMBER;
        return relations.getOrDefault(factionId, Relation.NEUTRAL);
    }
    
    public void setRelation(String factionId, Relation relation) {
        if (relation == Relation.NEUTRAL) {
            relations.remove(factionId);
        } else {
            relations.put(factionId, relation);
        }
        updateActivity();
    }
    
    public Map<String, Relation> getAllRelations() {
        return Collections.unmodifiableMap(relations);
    }
    
    public Set<String> getAllies() {
        Set<String> allies = new HashSet<>();
        for (Map.Entry<String, Relation> entry : relations.entrySet()) {
            if (entry.getValue() == Relation.ALLY) {
                allies.add(entry.getKey());
            }
        }
        return allies;
    }
    
    public Set<String> getEnemies() {
        Set<String> enemies = new HashSet<>();
        for (Map.Entry<String, Relation> entry : relations.entrySet()) {
            if (entry.getValue() == Relation.ENEMY) {
                enemies.add(entry.getKey());
            }
        }
        return enemies;
    }
    
    public Set<String> getTruces() {
        Set<String> truces = new HashSet<>();
        for (Map.Entry<String, Relation> entry : relations.entrySet()) {
            if (entry.getValue() == Relation.TRUCE) {
                truces.add(entry.getKey());
            }
        }
        return truces;
    }
    
    // Demandes de relation
    
    public void addRelationRequest(String factionId) {
        relationRequests.put(factionId, System.currentTimeMillis());
    }
    
    public boolean hasRelationRequest(String factionId) {
        Long timestamp = relationRequests.get(factionId);
        if (timestamp == null) return false;
        // Expire après 5 minutes
        if (System.currentTimeMillis() - timestamp > 300000) {
            relationRequests.remove(factionId);
            return false;
        }
        return true;
    }
    
    public void removeRelationRequest(String factionId) {
        relationRequests.remove(factionId);
    }
    
    // === Invitations ===
    
    // Map d'invitations: joueur UUID -> timestamp
    private final Map<UUID, Long> invites = new ConcurrentHashMap<>();
    
    /**
     * Ajoute une invitation pour un joueur
     * @param playerId UUID du joueur invité
     */
    public void addInvite(UUID playerId) {
        invites.put(playerId, System.currentTimeMillis());
    }
    
    /**
     * Vérifie si un joueur a une invitation valide
     * @param playerId UUID du joueur
     * @param expirationMs Temps d'expiration en ms
     * @return true si invitation valide
     */
    public boolean hasInvite(UUID playerId, long expirationMs) {
        Long timestamp = invites.get(playerId);
        if (timestamp == null) return false;
        if (System.currentTimeMillis() - timestamp > expirationMs) {
            invites.remove(playerId);
            return false;
        }
        return true;
    }
    
    /**
     * Retire une invitation
     * @param playerId UUID du joueur
     */
    public void removeInvite(UUID playerId) {
        invites.remove(playerId);
    }
    
    // === Promotions / Rétrogradations ===
    
    /**
     * Promeut un membre au rang supérieur
     * @param playerId UUID du joueur
     * @return true si promotion réussie
     */
    public boolean promote(UUID playerId) {
        if (!members.contains(playerId)) return false;
        FactionRole currentRole = memberRoles.get(playerId);
        if (currentRole == null || currentRole == FactionRole.LEADER || currentRole == FactionRole.COLEADER) {
            return false;
        }
        
        FactionRole newRole;
        switch (currentRole) {
            case RECRUIT:
                newRole = FactionRole.MEMBER;
                break;
            case MEMBER:
                newRole = FactionRole.MODERATOR;
                break;
            case MODERATOR:
                newRole = FactionRole.COLEADER;
                break;
            default:
                return false;
        }
        
        memberRoles.put(playerId, newRole);
        updateActivity();
        return true;
    }
    
    /**
     * Rétrograde un membre au rang inférieur
     * @param playerId UUID du joueur
     * @return true si rétrogradation réussie
     */
    public boolean demote(UUID playerId) {
        if (!members.contains(playerId)) return false;
        FactionRole currentRole = memberRoles.get(playerId);
        if (currentRole == null || currentRole == FactionRole.LEADER || currentRole == FactionRole.RECRUIT) {
            return false;
        }
        
        FactionRole newRole;
        switch (currentRole) {
            case COLEADER:
                newRole = FactionRole.MODERATOR;
                break;
            case MODERATOR:
                newRole = FactionRole.MEMBER;
                break;
            case MEMBER:
                newRole = FactionRole.RECRUIT;
                break;
            default:
                return false;
        }
        
        memberRoles.put(playerId, newRole);
        updateActivity();
        return true;
    }
    
    // === Économie ===
    
    public double getBank() {
        return bank;
    }
    
    public void setBank(double bank) {
        this.bank = Math.max(0, bank);
    }
    
    public void deposit(double amount) {
        this.bank += Math.max(0, amount);
        updateActivity();
    }
    
    public boolean withdraw(double amount) {
        if (amount > bank) return false;
        this.bank -= amount;
        updateActivity();
        return true;
    }
    
    // === TNT Bank ===
    
    public int getTntBank() {
        return tntBank;
    }
    
    public void setTntBank(int amount) {
        this.tntBank = Math.max(0, amount);
    }
    
    public void addTnt(int amount) {
        this.tntBank += Math.max(0, amount);
    }
    
    public boolean removeTnt(int amount) {
        if (amount > tntBank) return false;
        this.tntBank -= amount;
        return true;
    }
    
    public boolean isTntEnabled() {
        return tntEnabled;
    }
    
    public void setTntEnabled(boolean enabled) {
        this.tntEnabled = enabled;
    }
    
    // === Système de Niveaux ===
    
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    
    public int getCurrentXp() { return currentXp; }
    public void setCurrentXp(int xp) { this.currentXp = xp; }
    
    public void addXp(int amount) {
        this.currentXp += Math.max(0, amount);
        updateActivity();
    }
    
    public QuestCategory getActiveCategory() { return activeCategory; }
    public void setActiveCategory(QuestCategory category) { this.activeCategory = category; }
    
    public List<FactionQuest> getActiveQuests() { return activeQuests; }
    
    public void setActiveQuests(List<FactionQuest> quests) {
        this.activeQuests.clear();
        if (quests != null) {
            this.activeQuests.addAll(quests);
        }
    }
    
    public void clearActiveQuests() { this.activeQuests.clear(); }

    public FactionProgressState getProgressionState() { return progressionState; }
    
    public Set<String> getAppliedRewards() { 
        return Collections.unmodifiableSet(appliedRewards); 
    }
    
    public boolean hasAppliedReward(String key) { return appliedRewards.contains(key); }
    public void addAppliedReward(String key) { appliedRewards.add(key); }
    
    /**
     * Vérifie si toutes les quêtes actives sont complétées
     */
    public boolean areAllQuestsCompleted() {
        if (activeQuests.isEmpty()) return false;
        for (FactionQuest q : activeQuests) {
            if (!q.isCompleted()) return false;
        }
        return true;
    }
    
    // === Récompenses de niveau ===
    
    public int getChestSize() { return chestSize; }
    public void setChestSize(int size) { this.chestSize = size; }
    public boolean hasChest() { return chestSize > 0; }
    
    public String getChestContentsB64() { return chestContentsB64; }
    public void setChestContentsB64(String b64) { this.chestContentsB64 = b64; }
    
    public boolean isFactionFlyEnabled() { return factionFlyEnabled; }
    public void setFactionFlyEnabled(boolean enabled) { this.factionFlyEnabled = enabled; }
    
    public boolean isAntiSethomeEnabled() { return antiSethomeEnabled; }
    public void setAntiSethomeEnabled(boolean enabled) { this.antiSethomeEnabled = enabled; }
    
    public int getExtraWarps() { return extraWarps; }
    public void setExtraWarps(int extra) { this.extraWarps = extra; }
    public void addExtraWarps(int amount) { this.extraWarps += amount; }
    
    public int getExtraMembers() { return extraMembers; }
    public void setExtraMembers(int extra) { this.extraMembers = extra; }
    public void addExtraMembers(int amount) { this.extraMembers += amount; }
    
    public double getExtraPowerBoost() { return extraPowerBoost; }
    public void setExtraPowerBoost(double extra) { this.extraPowerBoost = extra; }
    public void addExtraPowerBoost(double amount) { this.extraPowerBoost += amount; }
    
    // === Permissions ===
    
    public boolean hasPermission(UUID uuid, PermissionAction action) {
        FactionRole role = memberRoles.get(uuid);
        if (role == null) return false;
        
        // Leader a toutes les permissions
        if (role == FactionRole.LEADER) return true;
        
        Set<PermissionAction> perms = permissions.get(role);
        return perms != null && perms.contains(action);
    }
    
    public boolean hasPermission(FactionRole role, PermissionAction action) {
        if (role == FactionRole.LEADER) return true;
        Set<PermissionAction> perms = permissions.get(role);
        return perms != null && perms.contains(action);
    }
    
    public boolean hasPermission(Relation relation, PermissionAction action) {
        Set<PermissionAction> perms = relationPermissions.get(relation);
        return perms != null && perms.contains(action);
    }
    
    public void setPermission(FactionRole role, PermissionAction action, boolean value) {
        Set<PermissionAction> perms = permissions.computeIfAbsent(role, 
            k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
        if (value) {
            perms.add(action);
        } else {
            perms.remove(action);
        }
    }
    
    public void setPermission(Relation relation, PermissionAction action, boolean value) {
        Set<PermissionAction> perms = relationPermissions.computeIfAbsent(relation,
            k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
        if (value) {
            perms.add(action);
        } else {
            perms.remove(action);
        }
    }
    
    public Map<FactionRole, Set<PermissionAction>> getAllPermissions() {
        return Collections.unmodifiableMap(permissions);
    }
    
    public Map<Relation, Set<PermissionAction>> getRelationPermissions() {
        return Collections.unmodifiableMap(relationPermissions);
    }
    
    /**
     * Initialise les permissions par défaut
     */
    private void initDefaultPermissions() {
        // RECRUIT
        Set<PermissionAction> recruitPerms = Collections.newSetFromMap(new ConcurrentHashMap<>());
        recruitPerms.add(PermissionAction.HOME);
        recruitPerms.add(PermissionAction.CONTAINER);
        permissions.put(FactionRole.RECRUIT, recruitPerms);
        
        // MEMBER
        Set<PermissionAction> memberPerms = Collections.newSetFromMap(new ConcurrentHashMap<>());
        memberPerms.addAll(recruitPerms);
        memberPerms.add(PermissionAction.BUILD);
        memberPerms.add(PermissionAction.DESTROY);
        memberPerms.add(PermissionAction.SWITCH);
        memberPerms.add(PermissionAction.DEPOSIT);
        permissions.put(FactionRole.MEMBER, memberPerms);
        
        // MODERATOR
        Set<PermissionAction> modPerms = Collections.newSetFromMap(new ConcurrentHashMap<>());
        modPerms.addAll(memberPerms);
        modPerms.add(PermissionAction.INVITE);
        modPerms.add(PermissionAction.KICK);
        modPerms.add(PermissionAction.CLAIM);
        modPerms.add(PermissionAction.SETHOME);
        modPerms.add(PermissionAction.TNT);
        permissions.put(FactionRole.MODERATOR, modPerms);
        
        // COLEADER
        Set<PermissionAction> coleaderPerms = Collections.newSetFromMap(new ConcurrentHashMap<>());
        coleaderPerms.addAll(modPerms);
        coleaderPerms.add(PermissionAction.UNCLAIM);
        coleaderPerms.add(PermissionAction.PROMOTE);
        coleaderPerms.add(PermissionAction.DEMOTE);
        coleaderPerms.add(PermissionAction.WITHDRAW);
        coleaderPerms.add(PermissionAction.RELATION_ALLY);
        coleaderPerms.add(PermissionAction.RELATION_ENEMY);
        coleaderPerms.add(PermissionAction.RELATION_TRUCE);
        coleaderPerms.add(PermissionAction.RELATION_NEUTRAL);
        permissions.put(FactionRole.COLEADER, coleaderPerms);
        
        // Relation permissions (par défaut très restrictives - aucune permission)
        // Les joueurs choisiront quelles permissions activer
        Set<PermissionAction> allyPerms = Collections.newSetFromMap(new ConcurrentHashMap<>());
        allyPerms.add(PermissionAction.SWITCH);
        relationPermissions.put(Relation.ALLY, allyPerms);
        
        // Initialiser les autres relations avec des sets vides (aucune permission par défaut)
        Set<PermissionAction> trucePerms = Collections.newSetFromMap(new ConcurrentHashMap<>());
        relationPermissions.put(Relation.TRUCE, trucePerms);
        
        Set<PermissionAction> neutralPerms = Collections.newSetFromMap(new ConcurrentHashMap<>());
        relationPermissions.put(Relation.NEUTRAL, neutralPerms);
        
        Set<PermissionAction> enemyPerms = Collections.newSetFromMap(new ConcurrentHashMap<>());
        relationPermissions.put(Relation.ENEMY, enemyPerms);
    }
    
    // === Métadonnées ===
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public long getLastActivity() {
        return lastActivity;
    }
    
    public void updateActivity() {
        this.lastActivity = System.currentTimeMillis();
    }
    
    /**
     * Calcule le temps d'inactivité en millisecondes
     */
    public long getInactivityTime() {
        return System.currentTimeMillis() - lastActivity;
    }
    
    /**
     * Calcule le temps d'inactivité en jours
     */
    public int getInactivityDays() {
        return (int) (getInactivityTime() / (1000L * 60 * 60 * 24));
    }
    
    public boolean isPermanent() {
        return permanent;
    }
    
    public void setPermanent(boolean permanent) {
        this.permanent = permanent;
    }
    
    public boolean isOpen() {
        return open;
    }
    
    public void setOpen(boolean open) {
        this.open = open;
    }
    
    // === Helpers système ===
    
    public boolean isSystemFaction() {
        return WILDERNESS_ID.equals(id) || SAFEZONE_ID.equals(id) || WARZONE_ID.equals(id);
    }
    
    public boolean isWilderness() {
        return WILDERNESS_ID.equals(id);
    }
    
    public boolean isSafezone() {
        return SAFEZONE_ID.equals(id);
    }
    
    /**
     * Alias pour isSafezone() - pour compatibilité
     */
    public boolean isSafeZone() {
        return isSafezone();
    }
    
    public boolean isWarzone() {
        return WARZONE_ID.equals(id);
    }
    
    /**
     * Alias pour isWarzone() - pour compatibilité
     */
    public boolean isWarZone() {
        return isWarzone();
    }
    
    private static String formatSystemName(String id) {
        switch (id) {
            case WILDERNESS_ID: return "Zone Sauvage";
            case SAFEZONE_ID: return "Zone Protégée";
            case WARZONE_ID: return "Zone de Guerre";
            default: return id;
        }
    }
    
    private static String formatSystemTag(String id) {
        switch (id) {
            case WILDERNESS_ID: return "~";
            case SAFEZONE_ID: return "✦";
            case WARZONE_ID: return "⚔";
            default: return "?";
        }
    }
    
    /**
     * Trouve et promeut un nouveau leader parmi les membres
     */
    private void promoteNewLeader() {
        if (members.isEmpty()) {
            leader = null;
            return;
        }
        
        // Chercher le membre avec le rang le plus élevé
        UUID newLeader = null;
        FactionRole highestRole = null;
        
        for (UUID uuid : members) {
            FactionRole role = memberRoles.get(uuid);
            if (role != null && (highestRole == null || role.isHigherThan(highestRole))) {
                newLeader = uuid;
                highestRole = role;
            }
        }
        
        if (newLeader != null) {
            memberRoles.put(newLeader, FactionRole.LEADER);
            leader = newLeader;
        }
    }
    
    /**
     * Envoie un message à tous les membres en ligne
     * @param message Le message à envoyer
     */
    public void broadcast(String message) {
        for (Player player : getOnlinePlayers()) {
            player.sendMessage(message);
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Faction faction = (Faction) o;
        return id.equals(faction.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return "Faction{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", members=" + members.size() +
                ", claims=" + claims.size() +
                '}';
    }
}
