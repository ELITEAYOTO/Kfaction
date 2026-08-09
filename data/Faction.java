package me.krunsh.kfaction.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.economy.MoneyAmount;
import me.krunsh.kfaction.progression.FactionProgressState;

/**
 * Représente une faction dans le système.
 *
 * Cette classe reste compatible avec le core V1 pendant la migration V2.
 * La future étape Domain Model déplacera progressivement les mutations
 * importantes dans des services dédiés.
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

    // Claim Groups V2:
    // groupId -> groupe, puis chunk -> groupId.
    private final Map<String, ClaimGroup> claimGroups;
    private final Map<FLocation, String> claimGroupAssignments;

    private StoredLocation home;
    private final Map<String, FactionWarp> warps;

    // === Power ===
    private double powerBoost;

    // === Relations ===
    private final Map<String, Relation> relations;
    private final Map<String, Long> relationRequests;

    // === Invitations ===
    private final Map<UUID, Long> invites;

    // === Économie ===
    private long bankMinor;

    // === Permissions ===
    private final Map<FactionRole, Set<PermissionAction>> permissions;
    private final Map<Relation, Set<PermissionAction>> relationPermissions;

    // === Métadonnées ===
    private final long createdAt;
    private long lastActivity;
    private boolean permanent;
    private boolean open;

    // === TNT / Raiding ===
    private int tntBank;
    private boolean tntEnabled;

    // === Système de niveaux ===
    private int level;
    private int currentXp;
    private QuestCategory activeCategory;
    private final List<FactionQuest> activeQuests;
    private final FactionProgressState progressionState;
    private final Set<String> appliedRewards;

    // === Récompenses de niveau ===
    private int chestSize;
    private String chestContentsB64;
    private boolean factionFlyEnabled;
    private boolean antiSethomeEnabled;
    private int extraWarps;
    private int extraMembers;
    private double extraPowerBoost;

    // === Factions système ===
    public static final String WILDERNESS_ID = "wilderness";
    public static final String SAFEZONE_ID = "safezone";
    public static final String WARZONE_ID = "warzone";

    /**
     * Crée une faction normale.
     */
    public Faction(String id, String name, UUID leader) {
        this(
                id,
                name,
                leader,
                System.currentTimeMillis(),
                System.currentTimeMillis()
        );
    }

    /**
     * Constructeur de restauration V2.
     *
     * Permet au stockage de restaurer fidèlement les timestamps persistants
     * au lieu de recréer artificiellement la faction à chaque redémarrage.
     */
    public Faction(
            String id,
            String name,
            UUID leader,
            long createdAt,
            long lastActivity
    ) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("id cannot be empty");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("name cannot be empty");
        }

        this.id = id;
        this.name = name;
        this.tag = name.length() > 4 ? name.substring(0, 4) : name;
        this.description = "";

        this.leader = leader;
        this.members = newConcurrentSet();
        this.memberRoles = new ConcurrentHashMap<>();

        this.claims = newConcurrentSet();
        this.claimGroups = new ConcurrentHashMap<>();
        this.claimGroupAssignments = new ConcurrentHashMap<>();
        this.warps = new ConcurrentHashMap<>();

        this.relations = new ConcurrentHashMap<>();
        this.relationRequests = new ConcurrentHashMap<>();
        this.invites = new ConcurrentHashMap<>();

        this.permissions = new ConcurrentHashMap<>();
        this.relationPermissions = new ConcurrentHashMap<>();

        long now = System.currentTimeMillis();
        this.createdAt = createdAt > 0L ? createdAt : now;
        this.lastActivity = lastActivity > 0L
                ? lastActivity
                : this.createdAt;
        this.permanent = false;
        this.open = false;

        this.bankMinor = 0L;
        this.tntBank = 0;
        this.tntEnabled = true;
        this.powerBoost = 0.0D;

        this.level = 0;
        this.currentXp = 0;
        this.activeCategory = null;
        this.activeQuests = new ArrayList<>();
        this.progressionState = new FactionProgressState();
        this.appliedRewards = newConcurrentSet();

        this.chestSize = 0;
        this.chestContentsB64 = null;
        this.factionFlyEnabled = false;
        this.antiSethomeEnabled = false;
        this.extraWarps = 0;
        this.extraMembers = 0;
        this.extraPowerBoost = 0.0D;

        if (leader != null) {
            members.add(leader);
            memberRoles.put(leader, FactionRole.LEADER);
        }

        initDefaultPermissions();
    }

    /**
     * Crée une faction système (Wilderness, SafeZone, WarZone).
     */
    public Faction(String id) {
        this.id = id;
        this.name = formatSystemName(id);
        this.tag = formatSystemTag(id);
        this.description = "";

        this.leader = null;
        this.members = newConcurrentSet();
        this.memberRoles = new ConcurrentHashMap<>();

        this.claims = newConcurrentSet();
        this.claimGroups = new ConcurrentHashMap<>();
        this.claimGroupAssignments = new ConcurrentHashMap<>();
        this.warps = new ConcurrentHashMap<>();

        this.relations = new ConcurrentHashMap<>();
        this.relationRequests = new ConcurrentHashMap<>();
        this.invites = new ConcurrentHashMap<>();

        this.permissions = new ConcurrentHashMap<>();
        this.relationPermissions = new ConcurrentHashMap<>();

        this.createdAt = System.currentTimeMillis();
        this.lastActivity = this.createdAt;
        this.permanent = true;
        this.open = false;

        this.bankMinor = 0L;
        this.tntBank = 0;
        this.tntEnabled = false;
        this.powerBoost = 0.0D;

        this.level = 0;
        this.currentXp = 0;
        this.activeCategory = null;
        this.activeQuests = new ArrayList<>();
        this.progressionState = new FactionProgressState();
        this.appliedRewards = newConcurrentSet();

        this.chestSize = 0;
        this.chestContentsB64 = null;
        this.factionFlyEnabled = false;
        this.antiSethomeEnabled = false;
        this.extraWarps = 0;
        this.extraMembers = 0;
        this.extraPowerBoost = 0.0D;
    }

    private static <T> Set<T> newConcurrentSet() {
        return Collections.newSetFromMap(new ConcurrentHashMap<T, Boolean>());
    }

    // ============================================================
    // Identité
    // ============================================================

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
        updateActivity();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        updateActivity();
    }

    // ============================================================
    // Membres / rôles
    // ============================================================

    public UUID getLeader() {
        return leader;
    }

    /**
     * Transfère le leadership.
     *
     * L'ancien leader devient COLEADER.
     * Le nouveau leader doit déjà être membre de la faction.
     */
    public void setLeader(UUID newLeader) {
        if (newLeader != null && !members.contains(newLeader)) {
            return;
        }

        if (Objects.equals(this.leader, newLeader)) {
            if (newLeader != null) {
                memberRoles.put(newLeader, FactionRole.LEADER);
            }
            return;
        }

        UUID oldLeader = this.leader;

        if (oldLeader != null && members.contains(oldLeader)) {
            memberRoles.put(oldLeader, FactionRole.COLEADER);
        }

        this.leader = newLeader;

        if (newLeader != null) {
            memberRoles.put(newLeader, FactionRole.LEADER);
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
        return uuid != null && members.contains(uuid);
    }

    public boolean isLeader(UUID uuid) {
        return uuid != null && leader != null && leader.equals(uuid);
    }

    public boolean addMember(UUID uuid, FactionRole role) {
        if (uuid == null || role == null) {
            return false;
        }

        if (!members.add(uuid)) {
            return false;
        }

        // Un LEADER supplémentaire ne doit jamais être créé via addMember.
        FactionRole safeRole = role == FactionRole.LEADER && leader != null
                ? FactionRole.COLEADER
                : role;

        memberRoles.put(uuid, safeRole);

        if (safeRole == FactionRole.LEADER) {
            leader = uuid;
        }

        updateActivity();
        return true;
    }

    public boolean addMember(UUID uuid) {
        return addMember(uuid, FactionRole.RECRUIT);
    }

    public boolean removeMember(UUID uuid) {
        if (uuid == null || !members.remove(uuid)) {
            return false;
        }

        memberRoles.remove(uuid);

        if (uuid.equals(leader)) {
            leader = null;
            promoteNewLeader();
        }

        updateActivity();
        return true;
    }

    public FactionRole getRole(UUID uuid) {
        return uuid == null ? null : memberRoles.get(uuid);
    }

    /**
     * Compatibilité V1.
     *
     * Le passage vers LEADER utilise setLeader() afin d'éviter deux leaders.
     * Une tentative de rétrograder directement le leader courant est ignorée :
     * le transfert de leadership doit d'abord être réalisé proprement.
     */
    public void setRole(UUID uuid, FactionRole role) {
        if (uuid == null || role == null || !members.contains(uuid)) {
            return;
        }

        if (role == FactionRole.LEADER) {
            setLeader(uuid);
            return;
        }

        if (uuid.equals(leader)) {
            return;
        }

        memberRoles.put(uuid, role);
        updateActivity();
    }

    public Set<UUID> getMembersWithMinRole(FactionRole minRole) {
        Set<UUID> result = new HashSet<>();
        if (minRole == null) {
            return result;
        }

        for (Map.Entry<UUID, FactionRole> entry : memberRoles.entrySet()) {
            FactionRole role = entry.getValue();
            if (role != null && role.isAtLeast(minRole)) {
                result.add(entry.getKey());
            }
        }

        return result;
    }

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

    /**
     * Promotion standard :
     * RECRUIT -> MEMBER -> OFFICER -> MODERATOR -> COLEADER.
     *
     * COLEADER -> LEADER est volontairement interdit ici et doit passer par
     * un vrai transfert de leadership.
     */
    public boolean promote(UUID playerId) {
        if (!members.contains(playerId)) {
            return false;
        }

        FactionRole currentRole = memberRoles.get(playerId);
        if (currentRole == null || !currentRole.canBePromotedNormally()) {
            return false;
        }

        FactionRole newRole = currentRole.getNextRole();
        if (newRole == null || newRole == FactionRole.LEADER) {
            return false;
        }

        memberRoles.put(playerId, newRole);
        updateActivity();
        return true;
    }

    /**
     * Rétrogradation standard :
     * COLEADER -> MODERATOR -> OFFICER -> MEMBER -> RECRUIT.
     */
    public boolean demote(UUID playerId) {
        if (!members.contains(playerId)) {
            return false;
        }

        FactionRole currentRole = memberRoles.get(playerId);
        if (currentRole == null || !currentRole.canBeDemotedNormally()) {
            return false;
        }

        FactionRole newRole = currentRole.getPreviousRole();
        if (newRole == null) {
            return false;
        }

        memberRoles.put(playerId, newRole);
        updateActivity();
        return true;
    }

    // ============================================================
    // Territoire
    // ============================================================

    public Set<FLocation> getClaims() {
        return Collections.unmodifiableSet(claims);
    }

    public int getClaimCount() {
        return claims.size();
    }

    public boolean hasClaim(FLocation location) {
        return location != null && claims.contains(location);
    }

    public boolean addClaim(FLocation location) {
        if (location != null && claims.add(location)) {
            updateActivity();
            return true;
        }
        return false;
    }

    public boolean removeClaim(FLocation location) {
        if (location != null && claims.remove(location)) {
            // Invariant V2: un chunk non claimé ne peut jamais rester affecté
            // à un Claim Group.
            claimGroupAssignments.remove(location);
            updateActivity();
            return true;
        }
        return false;
    }

    public void clearClaims() {
        claims.clear();
        claimGroupAssignments.clear();
        updateActivity();
    }

    // ============================================================
    // Claim Groups V2
    // ============================================================

    public Map<String, ClaimGroup> getClaimGroups() {
        return Collections.unmodifiableMap(claimGroups);
    }

    public int getClaimGroupCount() {
        return claimGroups.size();
    }

    public ClaimGroup getClaimGroup(String id) {
        if (id == null) {
            return null;
        }

        return claimGroups.get(
                id.trim().toLowerCase()
        );
    }

    public boolean addClaimGroup(ClaimGroup group) {
        if (group == null) {
            return false;
        }

        ClaimGroup previous =
                claimGroups.putIfAbsent(
                        group.getId(),
                        group
                );

        if (previous == null) {
            updateActivity();
            return true;
        }

        return false;
    }

    /**
     * Restauration stockage: même invariant d'unicité, sans dépendre d'un
     * service applicatif.
     */
    public boolean restoreClaimGroup(ClaimGroup group) {
        return addClaimGroup(group);
    }

    /**
     * Supprime le groupe mais jamais les claims.
     * Toutes ses affectations deviennent simplement INHERIT via "aucun groupe".
     */
    public boolean removeClaimGroup(String id) {
        ClaimGroup group =
                getClaimGroup(id);

        if (group == null) {
            return false;
        }

        String groupId =
                group.getId();

        if (!claimGroups.remove(
                groupId,
                group
        )) {
            return false;
        }

        for (Map.Entry<FLocation, String> entry
                : new HashMap<FLocation, String>(
                        claimGroupAssignments
                ).entrySet()) {
            if (groupId.equals(
                    entry.getValue()
            )) {
                claimGroupAssignments.remove(
                        entry.getKey(),
                        groupId
                );
            }
        }

        updateActivity();
        return true;
    }

    public String getClaimGroupId(FLocation location) {
        if (location == null) {
            return null;
        }

        return claimGroupAssignments.get(location);
    }

    public ClaimGroup getClaimGroupAt(FLocation location) {
        String groupId =
                getClaimGroupId(location);

        return groupId != null
                ? claimGroups.get(groupId)
                : null;
    }

    public boolean assignClaimToGroup(
            FLocation location,
            String groupId
    ) {
        if (location == null
                || groupId == null
                || !claims.contains(location)) {
            return false;
        }

        ClaimGroup group =
                getClaimGroup(groupId);

        if (group == null) {
            return false;
        }

        claimGroupAssignments.put(
                location,
                group.getId()
        );

        updateActivity();
        return true;
    }

    /**
     * Utilisé par le codec après restauration des groups et des claims.
     */
    public boolean restoreClaimGroupAssignment(
            FLocation location,
            String groupId
    ) {
        return assignClaimToGroup(
                location,
                groupId
        );
    }

    public boolean unassignClaimGroup(
            FLocation location
    ) {
        if (location == null) {
            return false;
        }

        String removed =
                claimGroupAssignments.remove(
                        location
                );

        if (removed != null) {
            updateActivity();
            return true;
        }

        return false;
    }

    public int countClaimsInGroup(String groupId) {
        ClaimGroup group =
                getClaimGroup(groupId);

        if (group == null) {
            return 0;
        }

        int count = 0;

        for (String assigned
                : claimGroupAssignments.values()) {
            if (group.getId().equals(
                    assigned
            )) {
                count++;
            }
        }

        return count;
    }

    public Map<FLocation, String>
            getClaimGroupAssignmentsSnapshot() {
        return Collections.unmodifiableMap(
                new HashMap<FLocation, String>(
                        claimGroupAssignments
                )
        );
    }

    /**
     * Façade Bukkit V1.
     *
     * null peut signifier "home absent" OU "monde non chargé".
     * Utiliser getStoredHome() pour distinguer les deux cas.
     */
    public Location getHome() {
        return home != null
                ? home.toBukkitLocation()
                : null;
    }

    public StoredLocation getStoredHome() {
        return home;
    }

    public void setHome(Location home) {
        this.home =
                home != null
                        ? StoredLocation.fromBukkit(home)
                        : null;
        updateActivity();
    }

    public void restoreHome(
            StoredLocation home
    ) {
        this.home = home;
        updateActivity();
    }

    public boolean hasHome() {
        return home != null;
    }

    // ============================================================
    // Warps V2
    // ============================================================

    /**
     * Façade V1: retourne uniquement les warps dont le monde est actuellement
     * chargé. Le stockage/API V2 doit préférer getWarpDataSnapshot().
     */
    public Map<String, Location> getWarps() {
        Map<String, Location> available =
                new HashMap<String, Location>();

        for (Map.Entry<String, FactionWarp> entry
                : warps.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }

            Location location =
                    entry.getValue()
                            .getLocation();

            if (location != null) {
                available.put(
                        entry.getKey(),
                        location
                );
            }
        }

        return Collections.unmodifiableMap(
                available
        );
    }

    public Map<String, FactionWarp>
            getWarpDataSnapshot() {
        return Collections.unmodifiableMap(
                new HashMap<String, FactionWarp>(
                        warps
                )
        );
    }

    public FactionWarp getWarpData(
            String name
    ) {
        if (name == null) {
            return null;
        }

        return warps.get(
                name.toLowerCase(
                        java.util.Locale.ROOT
                )
        );
    }

    public Location getWarp(String name) {
        FactionWarp warp =
                getWarpData(name);

        return warp != null
                ? warp.getLocation()
                : null;
    }

    public boolean hasWarp(String name) {
        return getWarpData(name) != null;
    }

    /**
     * Compatibilité V1.
     *
     * Une mise à jour legacy de location conserve le hash existant.
     */
    public void setWarp(
            String name,
            Location location
    ) {
        if (name == null || location == null) {
            return;
        }

        StoredLocation stored =
                StoredLocation.fromBukkit(
                        location
                );

        if (stored == null) {
            return;
        }

        FactionWarp existing =
                getWarpData(name);

        long now =
                System.currentTimeMillis();

        putWarp(
                new FactionWarp(
                        name,
                        stored,
                        existing != null
                                ? existing.getPasswordHash()
                                : null,
                        existing != null
                                ? existing.getCreatedAt()
                                : now,
                        existing != null
                                ? existing.getCreatedBy()
                                : null,
                        now
                )
        );
    }

    public void putWarp(
            FactionWarp warp
    ) {
        if (warp == null) {
            return;
        }

        warps.put(
                warp.getName(),
                warp
        );

        updateActivity();
    }

    public void restoreWarp(
            FactionWarp warp
    ) {
        putWarp(warp);
    }

    public boolean removeWarp(String name) {
        if (name == null) {
            return false;
        }

        FactionWarp removed =
                warps.remove(
                        name.toLowerCase(
                                java.util.Locale.ROOT
                        )
                );

        if (removed == null) {
            return false;
        }

        updateActivity();
        return true;
    }

    public int getWarpCount() {
        return warps.size();
    }

    public Set<String> getWarpNames() {
        return Collections.unmodifiableSet(
                new HashSet<String>(
                        warps.keySet()
                )
        );
    }

    // ============================================================
    // Power
    // ============================================================

    public double getPowerBoost() {
        return powerBoost;
    }

    public void setPowerBoost(double powerBoost) {
        this.powerBoost = powerBoost;
    }

    public double getFactionPowerBoost() {
        return powerBoost;
    }

    public double getPower() {
        me.krunsh.kfaction.Kfaction plugin = me.krunsh.kfaction.Kfaction.getInstance();
        if (plugin != null && plugin.getPowerManager() != null) {
            return plugin.getPowerManager().getFactionPower(this);
        }
        return powerBoost;
    }

    public double getMaxPower() {
        me.krunsh.kfaction.Kfaction plugin = me.krunsh.kfaction.Kfaction.getInstance();
        if (plugin != null && plugin.getPowerManager() != null) {
            return plugin.getPowerManager().getFactionMaxPower(this);
        }
        return powerBoost;
    }

    // ============================================================
    // Relations
    // ============================================================

    public Relation getRelationTo(Faction other) {
        if (other == null) {
            return Relation.NEUTRAL;
        }

        if (id.equals(other.id)) {
            return Relation.MEMBER;
        }

        return relations.getOrDefault(other.getId(), Relation.NEUTRAL);
    }

    public Relation getRelationTo(String factionId) {
        if (factionId == null) {
            return Relation.NEUTRAL;
        }

        if (id.equals(factionId)) {
            return Relation.MEMBER;
        }

        return relations.getOrDefault(factionId, Relation.NEUTRAL);
    }

    /**
     * null et NEUTRAL signifient tous les deux "aucune relation stockée".
     *
     * Ce comportement corrige aussi la suppression des relations pendant un
     * disband V1, qui appelait historiquement setRelation(id, null).
     */
    public void setRelation(String factionId, Relation relation) {
        if (factionId == null || factionId.equals(id)) {
            return;
        }

        if (relation == null || relation == Relation.NEUTRAL) {
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
        return getFactionIdsByRelation(Relation.ALLY);
    }

    public Set<String> getEnemies() {
        return getFactionIdsByRelation(Relation.ENEMY);
    }

    public Set<String> getTruces() {
        return getFactionIdsByRelation(Relation.TRUCE);
    }

    private Set<String> getFactionIdsByRelation(Relation relation) {
        Set<String> result = new HashSet<>();

        for (Map.Entry<String, Relation> entry : relations.entrySet()) {
            if (entry.getValue() == relation) {
                result.add(entry.getKey());
            }
        }

        return result;
    }

    /**
     * Ajoute une demande typée V2.
     *
     * Le type fait partie de la clé persistée afin qu'une demande ALLY ne
     * puisse jamais être acceptée accidentellement par une commande TRUCE.
     */
    public void addRelationRequest(
            String factionId,
            Relation relation
    ) {
        String key =
                relationRequestKey(
                        factionId,
                        relation
                );

        if (key != null) {
            relationRequests.put(
                    key,
                    System.currentTimeMillis()
            );
        }
    }

    public boolean hasRelationRequest(
            String factionId,
            Relation relation
    ) {
        String key =
                relationRequestKey(
                        factionId,
                        relation
                );

        return key != null
                && relationRequests.containsKey(
                        key
                );
    }

    public Long getRelationRequestTimestamp(
            String factionId,
            Relation relation
    ) {
        String key =
                relationRequestKey(
                        factionId,
                        relation
                );

        return key != null
                ? relationRequests.get(
                        key
                )
                : null;
    }

    public boolean removeRelationRequestIfPresent(
            String factionId,
            Relation relation
    ) {
        String key =
                relationRequestKey(
                        factionId,
                        relation
                );

        return key != null
                && relationRequests.remove(
                        key
                ) != null;
    }

    /**
     * Supprime toutes les propositions liées à une faction, quel que soit le
     * type, ainsi que l'ancienne clé V1 brute si elle existe encore.
     */
    public boolean removeRelationRequestsForFaction(
            String factionId
    ) {
        if (factionId == null
                || factionId.equals(id)) {
            return false;
        }

        boolean removed =
                relationRequests.remove(
                        factionId
                ) != null;

        String suffix =
                "|"
                        + factionId;

        for (String key
                : new HashSet<String>(
                        relationRequests.keySet()
                )) {
            if (key != null
                    && key.endsWith(
                            suffix
                    )
                    && relationRequests.remove(
                            key
                    ) != null) {
                removed = true;
            }
        }

        return removed;
    }

    public boolean hasAnyRelationRequestForFaction(
            String factionId
    ) {
        if (factionId == null
                || factionId.equals(id)) {
            return false;
        }

        if (relationRequests.containsKey(
                factionId
        )) {
            return true;
        }

        String suffix =
                "|"
                        + factionId;

        for (String key
                : relationRequests.keySet()) {
            if (key != null
                    && key.endsWith(
                            suffix
                    )) {
                return true;
            }
        }

        return false;
    }

    /**
     * Nettoie les demandes expirées.
     *
     * @return true si au moins une entrée a été supprimée et doit donc être
     *         repersistée.
     */
    public boolean pruneExpiredRelationRequests(
            long expirationMs
    ) {
        if (expirationMs <= 0L) {
            return false;
        }

        long now =
                System.currentTimeMillis();

        boolean removed =
                false;

        for (Map.Entry<String, Long> entry
                : new HashMap<String, Long>(
                        relationRequests
                ).entrySet()) {
            Long timestamp =
                    entry.getValue();

            if (timestamp == null) {
                if (relationRequests.remove(
                        entry.getKey()
                ) != null) {
                    removed = true;
                }
                continue;
            }

            if (now - timestamp.longValue()
                    > expirationMs
                    && relationRequests.remove(
                            entry.getKey(),
                            timestamp
                    )) {
                removed = true;
            }
        }

        return removed;
    }

    /**
     * Compatibilité binaire V1.
     *
     * Les nouvelles mutations doivent utiliser la surcharge typée.
     */
    @Deprecated
    public void addRelationRequest(
            String factionId
    ) {
        if (factionId != null
                && !factionId.equals(id)) {
            relationRequests.put(
                    factionId,
                    System.currentTimeMillis()
            );
        }
    }

    /**
     * Compatibilité V1 uniquement.
     *
     * Les demandes legacy brutes restent volontairement isolées des demandes
     * typées V2 et ne peuvent donc plus établir une relation d'un autre type.
     */
    @Deprecated
    public boolean hasRelationRequest(
            String factionId
    ) {
        if (factionId == null) {
            return false;
        }

        Long timestamp =
                relationRequests.get(
                        factionId
                );

        return timestamp != null
                && System.currentTimeMillis()
                        - timestamp.longValue()
                        <= 300000L;
    }

    @Deprecated
    public void removeRelationRequest(
            String factionId
    ) {
        if (factionId != null) {
            relationRequests.remove(
                    factionId
            );
        }
    }

    /**
     * Compatibilité lifecycle historique.
     *
     * Supprime désormais aussi les clés typées V2.
     */
    public boolean removeRelationRequestIfPresent(
            String factionId
    ) {
        return removeRelationRequestsForFaction(
                factionId
        );
    }

    /**
     * Snapshot défensif pour la persistance.
     */
    public Map<String, Long> getRelationRequestsSnapshot() {
        return Collections.unmodifiableMap(
                new HashMap<String, Long>(
                        relationRequests
                )
        );
    }

    /**
     * Restauration contrôlée depuis le stockage.
     *
     * Le champ factionIdOrTypedKey porte ce nom volontairement: le codec doit
     * accepter les anciennes clés brutes et les nouvelles clés RELATION|id.
     */
    public void restoreRelationRequest(
            String factionIdOrTypedKey,
            long timestamp
    ) {
        if (factionIdOrTypedKey == null
                || factionIdOrTypedKey.equals(id)) {
            return;
        }

        relationRequests.put(
                factionIdOrTypedKey,
                timestamp > 0L
                        ? timestamp
                        : System.currentTimeMillis()
        );
    }

    private String relationRequestKey(
            String factionId,
            Relation relation
    ) {
        if (factionId == null
                || factionId.equals(id)
                || relation == null
                || relation == Relation.MEMBER
                || relation == Relation.NEUTRAL
                || relation == Relation.ENEMY) {
            return null;
        }

        return relation.name()
                + "|"
                + factionId;
    }

    // ============================================================
    // Invitations
    // ============================================================

    public void addInvite(UUID playerId) {
        if (playerId != null) {
            invites.put(playerId, System.currentTimeMillis());
        }
    }

    public boolean hasInvite(UUID playerId, long expirationMs) {
        if (playerId == null) {
            return false;
        }

        Long timestamp = invites.get(playerId);
        if (timestamp == null) {
            return false;
        }

        if (expirationMs >= 0L && System.currentTimeMillis() - timestamp > expirationMs) {
            invites.remove(playerId);
            return false;
        }

        return true;
    }

    public void removeInvite(UUID playerId) {
        if (playerId != null) {
            invites.remove(playerId);
        }
    }

    /**
     * Snapshot défensif des invitations persistantes.
     */
    public Map<UUID, Long> getInvitesSnapshot() {
        return Collections.unmodifiableMap(
                new HashMap<UUID, Long>(invites)
        );
    }

    /**
     * Restauration contrôlée depuis le stockage.
     */
    public void restoreInvite(
            UUID playerId,
            long timestamp
    ) {
        if (playerId == null) {
            return;
        }

        invites.put(
                playerId,
                timestamp > 0L
                        ? timestamp
                        : System.currentTimeMillis()
        );
    }

    // ============================================================
    // Économie
    // ============================================================

    /**
     * Façade V1 en double.
     *
     * Le domaine V2 stocke exclusivement des minor units exactes.
     */
    public double getBank() {
        return MoneyAmount
                .ofMinor(bankMinor)
                .toDouble();
    }

    public double getBalance() {
        return getBank();
    }

    public long getBankMinor() {
        return bankMinor;
    }

    public void restoreBankMinor(
            long bankMinor
    ) {
        this.bankMinor =
                Math.max(
                        0L,
                        bankMinor
                );
    }

    /**
     * Compatibilité V1 / anciens payloads.
     */
    public void setBank(double bank) {
        this.bankMinor =
                MoneyAmount
                        .fromLegacyDouble(bank)
                        .getMinorUnits();
    }

    public boolean tryDepositMinor(
            long amountMinor
    ) {
        if (amountMinor <= 0L) {
            return false;
        }

        final long next;

        try {
            next =
                    Math.addExact(
                            bankMinor,
                            amountMinor
                    );
        } catch (ArithmeticException exception) {
            return false;
        }

        bankMinor = next;
        updateActivity();

        return true;
    }

    public boolean tryWithdrawMinor(
            long amountMinor
    ) {
        if (amountMinor <= 0L
                || amountMinor > bankMinor) {
            return false;
        }

        bankMinor -= amountMinor;
        updateActivity();

        return true;
    }

    /**
     * Façades V1.
     */
    public void deposit(double amount) {
        MoneyAmount money =
                MoneyAmount.fromLegacyDouble(
                        amount
                );

        if (money.isPositive()) {
            tryDepositMinor(
                    money.getMinorUnits()
            );
        }
    }

    public boolean withdraw(double amount) {
        MoneyAmount money =
                MoneyAmount.fromLegacyDouble(
                        amount
                );

        return money.isPositive()
                && tryWithdrawMinor(
                        money.getMinorUnits()
                );
    }

    // ============================================================
    // TNT
    // ============================================================

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
        if (amount > tntBank) {
            return false;
        }

        this.tntBank -= amount;
        return true;
    }

    public boolean isTntEnabled() {
        return tntEnabled;
    }

    public void setTntEnabled(boolean enabled) {
        this.tntEnabled = enabled;
    }

    // ============================================================
    // Progression
    // ============================================================

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getCurrentXp() {
        return currentXp;
    }

    public void setCurrentXp(int xp) {
        this.currentXp = xp;
    }

    public void addXp(int amount) {
        this.currentXp += Math.max(0, amount);
        updateActivity();
    }

    public QuestCategory getActiveCategory() {
        return activeCategory;
    }

    public void setActiveCategory(QuestCategory category) {
        this.activeCategory = category;
    }

    public List<FactionQuest> getActiveQuests() {
        return activeQuests;
    }

    public void setActiveQuests(List<FactionQuest> quests) {
        activeQuests.clear();
        if (quests != null) {
            activeQuests.addAll(quests);
        }
    }

    public void clearActiveQuests() {
        activeQuests.clear();
    }

    public FactionProgressState getProgressionState() {
        return progressionState;
    }

    public Set<String> getAppliedRewards() {
        return Collections.unmodifiableSet(appliedRewards);
    }

    public boolean hasAppliedReward(String key) {
        return appliedRewards.contains(key);
    }

    public void addAppliedReward(String key) {
        if (key != null) {
            appliedRewards.add(key);
        }
    }

    public boolean areAllQuestsCompleted() {
        if (activeQuests.isEmpty()) {
            return false;
        }

        for (FactionQuest quest : activeQuests) {
            if (!quest.isCompleted()) {
                return false;
            }
        }

        return true;
    }

    // ============================================================
    // Récompenses
    // ============================================================

    public int getChestSize() {
        return chestSize;
    }

    public void setChestSize(int size) {
        this.chestSize = size;
    }

    public boolean hasChest() {
        return chestSize > 0;
    }

    public String getChestContentsB64() {
        return chestContentsB64;
    }

    public void setChestContentsB64(String b64) {
        this.chestContentsB64 = b64;
    }

    public boolean isFactionFlyEnabled() {
        return factionFlyEnabled;
    }

    public void setFactionFlyEnabled(boolean enabled) {
        this.factionFlyEnabled = enabled;
    }

    public boolean isAntiSethomeEnabled() {
        return antiSethomeEnabled;
    }

    public void setAntiSethomeEnabled(boolean enabled) {
        this.antiSethomeEnabled = enabled;
    }

    public int getExtraWarps() {
        return extraWarps;
    }

    public void setExtraWarps(int extra) {
        this.extraWarps = extra;
    }

    public void addExtraWarps(int amount) {
        this.extraWarps += amount;
    }

    public int getExtraMembers() {
        return extraMembers;
    }

    public void setExtraMembers(int extra) {
        this.extraMembers = extra;
    }

    public void addExtraMembers(int amount) {
        this.extraMembers += amount;
    }

    public double getExtraPowerBoost() {
        return extraPowerBoost;
    }

    public void setExtraPowerBoost(double extra) {
        this.extraPowerBoost = extra;
    }

    public void addExtraPowerBoost(double amount) {
        this.extraPowerBoost += amount;
    }

    // ============================================================
    // Permissions V1 de transition
    // ============================================================

    public boolean hasPermission(UUID uuid, PermissionAction action) {
        FactionRole role = memberRoles.get(uuid);
        return role != null && hasPermission(role, action);
    }

    public boolean hasPermission(FactionRole role, PermissionAction action) {
        if (role == null || action == null) {
            return false;
        }

        if (role == FactionRole.LEADER) {
            return true;
        }

        Set<PermissionAction> perms = permissions.get(role);
        return perms != null && perms.contains(action);
    }

    public boolean hasPermission(Relation relation, PermissionAction action) {
        if (relation == null || action == null) {
            return false;
        }

        Set<PermissionAction> perms = relationPermissions.get(relation);
        return perms != null && perms.contains(action);
    }

    public void setPermission(FactionRole role, PermissionAction action, boolean value) {
        if (role == null || action == null || role == FactionRole.LEADER) {
            return;
        }

        Set<PermissionAction> perms = permissions.computeIfAbsent(
                role,
                key -> newConcurrentSet()
        );

        if (value) {
            perms.add(action);
        } else {
            perms.remove(action);
        }
    }

    public void setPermission(Relation relation, PermissionAction action, boolean value) {
        if (relation == null || action == null) {
            return;
        }

        Set<PermissionAction> perms = relationPermissions.computeIfAbsent(
                relation,
                key -> newConcurrentSet()
        );

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
     * Permissions par défaut TEMPORAIRES pendant la migration V2.
     *
     * Elles seront remplacées par Permission Engine V2/configuration.
     *
     * RECRUIT
     *   -> HOME, CONTAINER
     *
     * MEMBER
     *   -> RECRUIT + BUILD, DESTROY, SWITCH, DEPOSIT
     *
     * OFFICER
     *   -> MEMBER + INVITE, CLAIM
     *
     * MODERATOR
     *   -> OFFICER + KICK, SETHOME, TNT
     *
     * COLEADER
     *   -> MODERATOR + UNCLAIM, PROMOTE, DEMOTE, WITHDRAW, relations
     *
     * LEADER
     *   -> tout (géré par hasPermission)
     */
    private void initDefaultPermissions() {
        Set<PermissionAction> recruitPerms = newConcurrentSet();
        recruitPerms.add(PermissionAction.HOME);
        recruitPerms.add(PermissionAction.CONTAINER);
        permissions.put(FactionRole.RECRUIT, recruitPerms);

        Set<PermissionAction> memberPerms = copyPermissions(recruitPerms);
        memberPerms.add(PermissionAction.BUILD);
        memberPerms.add(PermissionAction.DESTROY);
        memberPerms.add(PermissionAction.SWITCH);
        memberPerms.add(PermissionAction.DEPOSIT);
        permissions.put(FactionRole.MEMBER, memberPerms);

        Set<PermissionAction> officerPerms = copyPermissions(memberPerms);
        officerPerms.add(PermissionAction.INVITE);
        officerPerms.add(PermissionAction.CLAIM);
        permissions.put(FactionRole.OFFICER, officerPerms);

        Set<PermissionAction> moderatorPerms = copyPermissions(officerPerms);
        moderatorPerms.add(PermissionAction.KICK);
        moderatorPerms.add(PermissionAction.SETHOME);
        moderatorPerms.add(PermissionAction.TNT);
        permissions.put(FactionRole.MODERATOR, moderatorPerms);

        Set<PermissionAction> coleaderPerms = copyPermissions(moderatorPerms);
        coleaderPerms.add(PermissionAction.UNCLAIM);
        coleaderPerms.add(PermissionAction.PROMOTE);
        coleaderPerms.add(PermissionAction.DEMOTE);
        coleaderPerms.add(PermissionAction.WITHDRAW);
        coleaderPerms.add(PermissionAction.RELATION_ALLY);
        coleaderPerms.add(PermissionAction.RELATION_ENEMY);
        coleaderPerms.add(PermissionAction.RELATION_TRUCE);
        coleaderPerms.add(PermissionAction.RELATION_NEUTRAL);
        permissions.put(FactionRole.COLEADER, coleaderPerms);

        Set<PermissionAction> allyPerms = newConcurrentSet();
        allyPerms.add(PermissionAction.SWITCH);
        relationPermissions.put(Relation.ALLY, allyPerms);

        relationPermissions.put(Relation.TRUCE, newConcurrentSet());
        relationPermissions.put(Relation.NEUTRAL, newConcurrentSet());
        relationPermissions.put(Relation.ENEMY, newConcurrentSet());
    }

    private static Set<PermissionAction> copyPermissions(Set<PermissionAction> source) {
        Set<PermissionAction> copy = newConcurrentSet();
        copy.addAll(source);
        return copy;
    }

    // ============================================================
    // Métadonnées
    // ============================================================

    public long getCreatedAt() {
        return createdAt;
    }

    public long getLastActivity() {
        return lastActivity;
    }

    public void updateActivity() {
        this.lastActivity = System.currentTimeMillis();
    }

    public long getInactivityTime() {
        return System.currentTimeMillis() - lastActivity;
    }

    public int getInactivityDays() {
        return (int) (getInactivityTime() / (1000L * 60L * 60L * 24L));
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

    // ============================================================
    // Factions système
    // ============================================================

    public boolean isSystemFaction() {
        return WILDERNESS_ID.equals(id)
                || SAFEZONE_ID.equals(id)
                || WARZONE_ID.equals(id);
    }

    public boolean isWilderness() {
        return WILDERNESS_ID.equals(id);
    }

    public boolean isSafezone() {
        return SAFEZONE_ID.equals(id);
    }

    public boolean isSafeZone() {
        return isSafezone();
    }

    public boolean isWarzone() {
        return WARZONE_ID.equals(id);
    }

    public boolean isWarZone() {
        return isWarzone();
    }

    private static String formatSystemName(String id) {
        switch (id) {
            case WILDERNESS_ID:
                return "Zone Sauvage";
            case SAFEZONE_ID:
                return "Zone Protégée";
            case WARZONE_ID:
                return "Zone de Guerre";
            default:
                return id;
        }
    }

    private static String formatSystemTag(String id) {
        switch (id) {
            case WILDERNESS_ID:
                return "~";
            case SAFEZONE_ID:
                return "✦";
            case WARZONE_ID:
                return "⚔";
            default:
                return "?";
        }
    }

    private void promoteNewLeader() {
        if (members.isEmpty()) {
            leader = null;
            return;
        }

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
            leader = newLeader;
            memberRoles.put(newLeader, FactionRole.LEADER);
        }
    }

    public void broadcast(String message) {
        for (Player player : getOnlinePlayers()) {
            player.sendMessage(message);
        }
    }

    // ============================================================
    // Object
    // ============================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

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
