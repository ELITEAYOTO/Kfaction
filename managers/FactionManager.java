package me.krunsh.kfaction.managers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.core.operation.OperationResult;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionRole;
import me.krunsh.kfaction.policy.FactionNamePolicy;
import me.krunsh.kfaction.services.FactionLifecycleService;
import me.krunsh.kfaction.services.HomeWarpService;
import me.krunsh.kfaction.services.MembershipService;
import me.krunsh.kfaction.services.MembershipService.ChangeReason;
import me.krunsh.kfaction.services.RoleService;

/**
 * Registry/cache principal des factions.
 *
 * Transition V2 :
 * les mutations d'appartenance/rôle/lifecycle sont déléguées aux services
 * applicatifs. Ce manager conserve surtout les index et les wrappers legacy.
 */
public class FactionManager {

    private final Kfaction plugin;

    private final Map<String, Faction> factionsById;
    private final Map<String, String> factionNameIndex;
    private final Map<String, String> factionTagIndex;

    private final HomeWarpService homeWarpService;

    private Faction wilderness;
    private Faction safezone;
    private Faction warzone;

    public FactionManager(Kfaction plugin) {
        this.plugin = plugin;
        this.factionsById = new ConcurrentHashMap<>();
        this.factionNameIndex = new ConcurrentHashMap<>();
        this.factionTagIndex = new ConcurrentHashMap<>();

        this.homeWarpService =
                new HomeWarpService(plugin);
    }

    public void initialize() {
        wilderness = new Faction(Faction.WILDERNESS_ID);
        safezone = new Faction(Faction.SAFEZONE_ID);
        warzone = new Faction(Faction.WARZONE_ID);

        factionsById.put(wilderness.getId(), wilderness);
        factionsById.put(safezone.getId(), safezone);
        factionsById.put(warzone.getId(), warzone);

        homeWarpService.initialize();

        plugin.getLogger().info(
                "FactionManager initialisé avec les factions système "
                        + "+ HomeWarpService V2"
        );
    }

    public void shutdown() {
        homeWarpService.shutdown();

        factionsById.clear();
        factionNameIndex.clear();
        factionTagIndex.clear();
    }

    public HomeWarpService getHomeWarpService() {
        return homeWarpService;
    }

    // ============================================================
    // Création / lifecycle
    // ============================================================

    public Faction createFaction(String name, UUID leader) {
        if (!Bukkit.isPrimaryThread()) {
            return null;
        }

        if (name == null || leader == null) {
            return null;
        }

        if (getFactionByName(name) != null) {
            return null;
        }

        FPlayer fPlayer = plugin
                .getFPlayerManager()
                .getOrCreate(leader);

        if (fPlayer == null || fPlayer.hasFaction()) {
            return null;
        }

        String id = UUID.randomUUID().toString();
        Faction faction = new Faction(id, name, leader);

        /*
         * V2: le constructeur domaine garde des defaults legacy de secours,
         * puis le moteur configurable remplace explicitement l'ACL d'une
         * NOUVELLE faction avant sa première persistance.
         */
        if (plugin.getPermissionManager() != null) {
            plugin.getPermissionManager()
                    .applyDefaultsToNewFaction(faction);
        }

        factionsById.put(id, faction);
        factionNameIndex.put(name.toLowerCase(Locale.ROOT), id);

        if (faction.getTag() != null) {
            factionTagIndex.put(
                    faction.getTag().toLowerCase(Locale.ROOT),
                    id
            );
        }

        fPlayer.joinFaction(id, FactionRole.LEADER);
        plugin.getFPlayerManager().notifyFactionChange(
                leader,
                null,
                id
        );

        plugin.getStorageManager().markDirty(faction);
        plugin.getStorageManager().markDirty(fPlayer);

        return faction;
    }

    /**
     * Wrapper legacy.
     *
     * Les nouvelles commandes utilisent directement FactionLifecycleService.
     */
    public boolean disbandFaction(Faction faction) {
        OperationResult<Integer> result =
                new FactionLifecycleService(plugin).disband(
                        faction,
                        OperationContext.system()
                );

        return result.isSuccessful();
    }

    /**
     * Retire uniquement la faction des index mémoire.
     * Réservé au lifecycle V2 après nettoyage de ses dépendances.
     */
    public boolean unregisterFaction(Faction faction) {
        if (!Bukkit.isPrimaryThread()) {
            return false;
        }

        if (faction == null || faction.isSystemFaction()) {
            return false;
        }

        String id = faction.getId();

        boolean removed = factionsById.remove(id, faction);
        factionNameIndex.remove(
                faction.getName().toLowerCase(Locale.ROOT),
                id
        );

        if (faction.getTag() != null) {
            factionTagIndex.remove(
                    faction.getTag().toLowerCase(Locale.ROOT),
                    id
            );
        }

        return removed;
    }

    // ============================================================
    // Recherche
    // ============================================================

    public Faction getFaction(String id) {
        return id != null ? factionsById.get(id) : null;
    }

    public Faction getFactionByName(String name) {
        if (name == null) {
            return null;
        }

        String id = factionNameIndex.get(name.toLowerCase(Locale.ROOT));
        return id != null ? factionsById.get(id) : null;
    }

    public Faction getFactionByTag(String tag) {
        if (tag == null) {
            return null;
        }

        String id = factionTagIndex.get(tag.toLowerCase(Locale.ROOT));
        return id != null ? factionsById.get(id) : null;
    }

    public void updateFactionTag(
            Faction faction,
            String newTag
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return;
        }

        if (faction == null) {
            return;
        }

        if (faction.getTag() != null) {
            factionTagIndex.remove(
                    faction.getTag().toLowerCase(Locale.ROOT),
                    faction.getId()
            );
        }

        faction.setTag(newTag);

        if (newTag != null) {
            factionTagIndex.put(
                    newTag.toLowerCase(Locale.ROOT),
                    faction.getId()
            );
        }

        plugin.getStorageManager().markDirty(faction);
    }

    /**
     * Lecture uniquement : ne crée plus de FPlayer.
     */
    public Faction getPlayerFaction(UUID playerUuid) {
        FPlayer fPlayer = plugin
                .getFPlayerManager()
                .find(playerUuid);

        if (fPlayer == null || !fPlayer.hasFaction()) {
            return null;
        }

        return getFaction(fPlayer.getFactionId());
    }

    public Faction getPlayerFaction(Player player) {
        return player != null
                ? getPlayerFaction(player.getUniqueId())
                : null;
    }

    public Collection<Faction> getAllFactions() {
        return Collections.unmodifiableList(
                new ArrayList<Faction>(
                        factionsById.values()
                )
        );
    }

    public Collection<Faction> getPlayerFactions() {
        List<Faction> result =
                new ArrayList<Faction>();

        for (Faction faction
                : factionsById.values()) {
            if (faction != null
                    && !faction.isSystemFaction()) {
                result.add(faction);
            }
        }

        return result;
    }

    public int getFactionCount() {
        int count = 0;

        for (Faction faction
                : factionsById.values()) {
            if (faction != null
                    && !faction.isSystemFaction()) {
                count++;
            }
        }

        return count;
    }

    // ============================================================
    // Factions système
    // ============================================================

    public Faction getWilderness() {
        return wilderness;
    }

    public Faction getSafezone() {
        return safezone;
    }

    public Faction getWarzone() {
        return warzone;
    }

    // ============================================================
    // Wrappers membership/rôles V1
    // ============================================================

    public boolean addMember(
            Faction faction,
            UUID playerUuid,
            FactionRole role
    ) {
        OperationResult<FactionRole> result =
                new MembershipService(plugin).join(
                        faction,
                        playerUuid,
                        role,
                        ChangeReason.JOIN,
                        OperationContext.system(),
                        false
                );

        return result.isSuccessful();
    }

    public boolean removeMember(
            Faction faction,
            UUID playerUuid
    ) {
        MembershipService membershipService =
                new MembershipService(plugin);

        OperationResult<Void> result =
                membershipService.remove(
                        faction,
                        playerUuid,
                        ChangeReason.LEAVE,
                        OperationContext.system(),
                        true
                );

        if (!result.isSuccessful()) {
            return false;
        }

        if (faction.getMemberCount() == 0) {
            return new FactionLifecycleService(plugin)
                    .disband(
                            faction,
                            OperationContext.system()
                    )
                    .isSuccessful();
        }

        return true;
    }

    public boolean setMemberRole(
            Faction faction,
            UUID playerUuid,
            FactionRole newRole
    ) {
        RoleService roleService = new RoleService(plugin);

        if (newRole == FactionRole.LEADER) {
            return roleService
                    .transferLeadership(
                            faction,
                            playerUuid,
                            OperationContext.system()
                    )
                    .isSuccessful();
        }

        return roleService
                .setRole(
                        faction,
                        playerUuid,
                        newRole,
                        OperationContext.system()
                )
                .isSuccessful();
    }

    public boolean transferLeadership(
            Faction faction,
            UUID newLeader
    ) {
        return new RoleService(plugin)
                .transferLeadership(
                        faction,
                        newLeader,
                        OperationContext.system()
                )
                .isSuccessful();
    }

    // ============================================================
    // Validation / chargement
    // ============================================================

    public boolean isValidName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }

        int minLength = plugin
                .getConfigManager()
                .getInt("factions.name.min-length", 3);

        int maxLength = plugin
                .getConfigManager()
                .getInt("factions.name.max-length", 16);

        String pattern = plugin
                .getConfigManager()
                .getString(
                        "factions.name.regex",
                        plugin.getConfigManager().getString(
                                "factions.name.pattern",
                                "^[a-zA-Z0-9_]+$"
                        )
                );

        List<String> reserved = new ArrayList<>(
                plugin.getConfigManager()
                        .getStringList("factions.name.blocked-words")
        );

        reserved.addAll(Arrays.asList(
                "wilderness",
                "safezone",
                "warzone",
                "admin",
                "server"
        ));

        return FactionNamePolicy.isValid(
                name,
                minLength,
                maxLength,
                pattern,
                reserved
        );
    }

    public boolean isNameAvailable(String name) {
        return getFactionByName(name) == null;
    }

    public void loadFaction(Faction faction) {
        if (faction == null) {
            return;
        }

        /*
         * Migration Global Zones V2:
         * d'anciens backends peuvent encore contenir une Faction système
         * sérialisée avec ses claims. On ne remplace jamais les façades
         * runtime créées par initialize(); on copie seulement les anciens
         * claims afin que ClaimManager.rebuildClaimIndex() puisse les migrer
         * vers ZoneService.
         */
        if (faction.isSystemFaction()) {
            Faction canonical = null;

            if (faction.isWilderness()) {
                canonical = wilderness;
            } else if (faction.isSafezone()) {
                canonical = safezone;
            } else if (faction.isWarzone()) {
                canonical = warzone;
            }

            if (canonical != null) {
                for (me.krunsh.kfaction.data.FLocation location
                        : faction.getClaims()) {
                    canonical.addClaim(location);
                }
            }

            return;
        }

        factionsById.put(
                faction.getId(),
                faction
        );

        factionNameIndex.put(
                faction.getName().toLowerCase(Locale.ROOT),
                faction.getId()
        );

        if (faction.getTag() != null) {
            factionTagIndex.put(
                    faction.getTag().toLowerCase(Locale.ROOT),
                    faction.getId()
            );
        }
    }

    public boolean renameFaction(
            Faction faction,
            String newName
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return false;
        }

        if (faction == null || faction.isSystemFaction()) {
            return false;
        }

        if (!isValidName(newName)
                || !isNameAvailable(newName)) {
            return false;
        }

        factionNameIndex.remove(
                faction.getName().toLowerCase(Locale.ROOT),
                faction.getId()
        );

        faction.setName(newName);

        factionNameIndex.put(
                newName.toLowerCase(Locale.ROOT),
                faction.getId()
        );

        plugin.getStorageManager().markDirty(faction);
        return true;
    }
}
