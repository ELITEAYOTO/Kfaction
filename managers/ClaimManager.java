package me.krunsh.kfaction.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.core.operation.OperationResult;
import me.krunsh.kfaction.core.operation.OperationSource;
import me.krunsh.kfaction.data.FLocation;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionWarp;
import me.krunsh.kfaction.data.StoredLocation;
import me.krunsh.kfaction.data.Relation;
import me.krunsh.kfaction.services.ClaimService;
import me.krunsh.kfaction.services.UnclaimService;
import me.krunsh.kfaction.services.ZoneService;
import me.krunsh.kfaction.services.claim.ClaimBatchResult;
import me.krunsh.kfaction.services.claim.UnclaimBatchResult;
import me.krunsh.kfaction.zones.GlobalZoneType;
import me.krunsh.kfaction.zones.ZoneDefinition;

/**
 * Index/runtime adapter des claims.
 *
 * V2:
 * - ClaimService possède la validation et la planification métier.
 * - ClaimManager possède l'index chunk -> faction et l'application bas niveau.
 * - les anciennes méthodes claim(...) restent compatibles.
 */
public class ClaimManager {

    private final Kfaction plugin;

    private final Map<FLocation, String> claimIndex;

    private final Map<UUID, String> adminAutoClaimPlayers;
    private final Map<UUID, String> adminAutoUnclaimPlayers;

    private final ClaimService service;
    private final UnclaimService unclaimService;
    private final ZoneService zoneService;

    public ClaimManager(Kfaction plugin) {
        this.plugin = plugin;

        this.claimIndex =
                new ConcurrentHashMap<FLocation, String>();

        this.adminAutoClaimPlayers =
                new ConcurrentHashMap<UUID, String>();

        this.adminAutoUnclaimPlayers =
                new ConcurrentHashMap<UUID, String>();

        this.service =
                new ClaimService(
                        plugin,
                        this
                );

        this.unclaimService =
                new UnclaimService(plugin);

        this.zoneService =
                new ZoneService(
                        plugin,
                        this
                );
    }

    public void initialize() {
        zoneService.initialize();

        plugin.getLogger().info(
                "ClaimManager V2 initialisé "
                        + "(claims + Global Zones V2)"
        );
    }

    public ClaimService getService() {
        return service;
    }

    public UnclaimService getUnclaimService() {
        return unclaimService;
    }

    public ZoneService getZoneService() {
        return zoneService;
    }

    public String getZoneIdAt(
            FLocation location
    ) {
        return zoneService.getZoneIdAt(
                location
        );
    }

    public ZoneDefinition getZoneDefinitionAt(
            FLocation location
    ) {
        return zoneService.getDefinitionAt(
                location
        );
    }

    /**
     * Retourne uniquement le propriétaire FACTION de l'index joueur.
     * Les Global Zones sont volontairement ignorées.
     *
     * Utilisé notamment par MapManager pour éviter une deuxième résolution
     * de ZoneService après avoir déjà capturé la ZoneDefinition.
     */
    public Faction getPlayerFactionAt(
            FLocation location
    ) {
        if (location == null) {
            return plugin.getFactionManager()
                    .getWilderness();
        }

        String factionId =
                claimIndex.get(location);

        if (factionId == null) {
            return plugin.getFactionManager()
                    .getWilderness();
        }

        Faction faction =
                plugin.getFactionManager()
                        .getFaction(
                                factionId
                        );

        return faction != null
                ? faction
                : plugin.getFactionManager()
                        .getWilderness();
    }

    public OperationResult<String> setZone(
            FLocation location,
            String zoneId,
            OperationContext context
    ) {
        return zoneService.setZone(
                location,
                zoneId,
                context
        );
    }

    public OperationResult<String> clearZone(
            FLocation location,
            String expectedZoneId,
            OperationContext context
    ) {
        return zoneService.clearZone(
                location,
                expectedZoneId,
                context
        );
    }

    public void rebuildClaimIndex() {
        claimIndex.clear();

        int legacyZonesImported = 0;

        for (Faction faction
                : plugin.getFactionManager()
                        .getAllFactions()) {
            if (faction.isSafezone()
                    || faction.isWarzone()) {
                GlobalZoneType type =
                        faction.isSafezone()
                                ? GlobalZoneType.SAFEZONE
                                : GlobalZoneType.WARZONE;

                List<FLocation> legacyClaims =
                        new ArrayList<FLocation>(
                                faction.getClaims()
                        );

                for (FLocation location
                        : legacyClaims) {
                    if (zoneService.importLegacy(
                            location,
                            type
                    )) {
                        legacyZonesImported++;
                    }
                }

                /*
                 * Après migration la façade système ne possède plus de claims.
                 * La source de vérité devient exclusivement ZoneService.
                 */
                faction.clearClaims();
                continue;
            }

            if (faction.isWilderness()) {
                continue;
            }

            for (FLocation location
                    : faction.getClaims()) {
                if (zoneService.hasZone(location)) {
                    /*
                     * Une zone globale est prioritaire. Nettoyer le conflit
                     * legacy pour éviter deux propriétaires du même chunk.
                     */
                    faction.removeClaim(location);
                    plugin.getStorageManager()
                            .markDirty(faction);
                    continue;
                }

                claimIndex.put(
                        location,
                        faction.getId()
                );
            }
        }

        zoneService.finishLegacyImport(
                legacyZonesImported
        );

        plugin.getLogger().info(
                "ClaimManager: index reconstruit ("
                        + claimIndex.size()
                        + " claims faction, "
                        + zoneService.getTotalZoneChunks()
                        + " chunks zone)"
        );
    }

    public void shutdown() {
        claimIndex.clear();
        adminAutoClaimPlayers.clear();
        adminAutoUnclaimPlayers.clear();
        zoneService.shutdown();
    }

    // ============================================================
    // Lookup
    // ============================================================

    /**
     * ID brut de l'index. null = Wilderness/non claim.
     */
    public String getFactionIdAt(
            FLocation location
    ) {
        if (location == null) {
            return null;
        }

        String zoneId =
                zoneService.getZoneIdAt(
                        location
                );

        if (zoneId != null) {
            GlobalZoneType legacy =
                    GlobalZoneType.parse(
                            zoneId
                    );

            /*
             * Une zone custom n'est PAS une Faction.
             * Les anciennes APIs ne reçoivent un factionId que pour les deux
             * façades historiques SafeZone/WarZone.
             */
            return legacy != null
                    ? legacy.getLegacyFactionId()
                    : null;
        }

        return claimIndex.get(location);
    }

    public Faction getFactionAt(
            FLocation location
    ) {
        if (location == null) {
            return plugin.getFactionManager()
                    .getWilderness();
        }

        String zoneId =
                zoneService.getZoneIdAt(
                        location
                );

        if (GlobalZoneType.SAFEZONE
                .getConfigKey()
                .equals(zoneId)) {
            return plugin.getFactionManager()
                    .getSafezone();
        }

        if (GlobalZoneType.WARZONE
                .getConfigKey()
                .equals(zoneId)) {
            return plugin.getFactionManager()
                    .getWarzone();
        }

        /*
         * Legacy getFactionAt() ne peut pas représenter une zone custom.
         * Elle est donc exposée comme Wilderness sur CETTE ancienne façade.
         * Le code V2 doit utiliser getZoneIdAt()/getZoneDefinitionAt().
         */
        if (zoneId != null) {
            return plugin.getFactionManager()
                    .getWilderness();
        }

        String factionId =
                claimIndex.get(location);

        if (factionId == null) {
            return plugin.getFactionManager()
                    .getWilderness();
        }

        Faction faction =
                plugin.getFactionManager()
                        .getFaction(factionId);

        return faction != null
                ? faction
                : plugin.getFactionManager()
                        .getWilderness();
    }

    public Faction getFactionAt(
            Location location
    ) {
        return getFactionAt(
                new FLocation(location)
        );
    }

    public Faction getFactionAt(
            Chunk chunk
    ) {
        return getFactionAt(
                new FLocation(chunk)
        );
    }

    public boolean isClaimed(
            FLocation location
    ) {
        return location != null
                && (zoneService.hasZone(location)
                || claimIndex.containsKey(location));
    }

    public boolean isSafezone(
            FLocation location
    ) {
        return zoneService.isZone(
                location,
                GlobalZoneType.SAFEZONE
        );
    }

    public boolean isWarzone(
            FLocation location
    ) {
        return zoneService.isZone(
                location,
                GlobalZoneType.WARZONE
        );
    }

    public boolean isSafezone(
            Location location
    ) {
        return isSafezone(
                new FLocation(location)
        );
    }

    public boolean isWarzone(
            Location location
    ) {
        return isWarzone(
                new FLocation(location)
        );
    }

    // ============================================================
    // Legacy claim API -> ClaimService V2
    // ============================================================

    public ClaimResult claim(
            Faction faction,
            FLocation location
    ) {
        OperationResult<ClaimBatchResult> result =
                service.claimSingle(
                        null,
                        faction,
                        location,
                        OperationContext.system()
                );

        return ClaimResult.from(result);
    }

    public ClaimResult claim(
            Player player,
            Faction faction,
            FLocation location
    ) {
        OperationContext context =
                player == null
                        ? OperationContext.system()
                        : OperationContext.actor(
                                player.getUniqueId(),
                                player.getName(),
                                OperationSource.COMMAND
                        );

        OperationResult<ClaimBatchResult> result =
                service.claimSingle(
                        player,
                        faction,
                        location,
                        context
                );

        return ClaimResult.from(result);
    }

    /**
     * Commit bas niveau réservé à ClaimService V2.
     *
     * Le propriétaire attendu est revalidé une dernière fois ici afin
     * d'empêcher un appel stale de corrompre l'index.
     */
    public void commitPlannedClaim(
            Faction newOwner,
            FLocation location,
            String expectedOwnerId
    ) {
        if (newOwner == null
                || newOwner.isSystemFaction()
                || location == null) {
            throw new IllegalArgumentException(
                    "Invalid planned claim"
            );
        }

        String actualOwnerId =
                claimIndex.get(location);

        if (!Objects.equals(
                actualOwnerId,
                expectedOwnerId
        )) {
            throw new IllegalStateException(
                    "Claim owner changed for "
                            + location.getKey()
                            + " expected="
                            + expectedOwnerId
                            + " actual="
                            + actualOwnerId
            );
        }

        Faction oldOwner = null;
        String oldClaimGroupId = null;
        StoredLocation oldHome = null;
        Map<String, FactionWarp> oldWarps =
                new HashMap<String, FactionWarp>();

        if (actualOwnerId != null) {
            oldOwner =
                    plugin.getFactionManager()
                            .getFaction(
                                    actualOwnerId
                            );

            if (oldOwner == null) {
                throw new IllegalStateException(
                        "Unknown old claim owner "
                                + actualOwnerId
                );
            }

            oldClaimGroupId =
                    oldOwner.getClaimGroupId(
                            location
                    );

            StoredLocation candidateHome =
                    oldOwner.getStoredHome();

            if (candidateHome != null
                    && candidateHome.isInChunk(
                            location
                    )) {
                oldHome = candidateHome;
            }

            for (Map.Entry<String, FactionWarp> entry
                    : oldOwner.getWarpDataSnapshot()
                            .entrySet()) {
                FactionWarp warp =
                        entry.getValue();

                if (warp != null
                        && warp.getStoredLocation() != null
                        && warp.getStoredLocation()
                                .isInChunk(
                                        location
                                )) {
                    oldWarps.put(
                            entry.getKey(),
                            warp
                    );
                }
            }

            oldOwner.removeClaim(location);
            checkAndRemoveHomeInChunk(
                    oldOwner,
                    location
            );
            checkAndRemoveWarpsInChunk(
                    oldOwner,
                    location
            );
        }

        if (!newOwner.addClaim(location)) {
            /*
             * Violation d'invariant après revalidation:
             * restauration complète de l'ancien owner, y compris
             * Claim Group + home + warps protégés.
             */
            if (oldOwner != null) {
                oldOwner.addClaim(location);

                if (oldClaimGroupId != null) {
                    oldOwner.restoreClaimGroupAssignment(
                            location,
                            oldClaimGroupId
                    );
                }

                if (oldHome != null) {
                    oldOwner.restoreHome(
                            oldHome
                    );
                }

                for (FactionWarp warp
                        : oldWarps.values()) {
                    oldOwner.restoreWarp(warp);
                }
            }

            throw new IllegalStateException(
                    "New owner already contains "
                            + location.getKey()
            );
        }

        claimIndex.put(
                location,
                newOwner.getId()
        );

        if (oldOwner != null) {
            plugin.getStorageManager()
                    .markDirty(oldOwner);
        }

        plugin.getStorageManager()
                .markDirty(newOwner);
    }

    // ============================================================
    // Unclaim
    // ============================================================

    public boolean unclaim(
            Faction faction,
            FLocation location
    ) {
        OperationResult<UnclaimBatchResult> result =
                unclaimService.unclaimSingle(
                        null,
                        faction,
                        location,
                        OperationContext.system()
                );

        return result.isSuccess();
    }

    /**
     * Commit bas niveau réservé à UnclaimService V2.
     *
     * Aucun home/warp n'est supprimé ici: ces side-effects sont appliqués
     * uniquement après réussite de tout le batch.
     */
    public void commitPlannedUnclaim(
            Faction owner,
            FLocation location
    ) {
        if (owner == null
                || owner.isSystemFaction()
                || location == null) {
            throw new IllegalArgumentException(
                    "Invalid planned unclaim"
            );
        }

        String ownerId =
                claimIndex.get(location);

        if (!owner.getId()
                .equals(ownerId)
                || !owner.hasClaim(location)) {
            throw new IllegalStateException(
                    "Unclaim owner changed for "
                            + location.getKey()
            );
        }

        if (!claimIndex.remove(
                location,
                owner.getId()
        )) {
            throw new IllegalStateException(
                    "Unable to remove claim index for "
                            + location.getKey()
            );
        }

        if (!owner.removeClaim(location)) {
            claimIndex.put(
                    location,
                    owner.getId()
            );

            throw new IllegalStateException(
                    "Unable to remove faction claim for "
                            + location.getKey()
            );
        }
    }

    /**
     * Rollback interne d'un commit d'unclaim partiellement appliqué.
     */
    public void rollbackPlannedUnclaim(
            Faction owner,
            FLocation location,
            String claimGroupId
    ) {
        if (owner == null
                || owner.isSystemFaction()
                || location == null) {
            throw new IllegalArgumentException(
                    "Invalid unclaim rollback"
            );
        }

        String indexed =
                claimIndex.get(location);

        if (indexed != null
                && !owner.getId()
                        .equals(indexed)) {
            throw new IllegalStateException(
                    "Rollback conflict at "
                            + location.getKey()
                            + " owner="
                            + indexed
            );
        }

        if (!owner.hasClaim(location)) {
            if (!owner.addClaim(location)) {
                throw new IllegalStateException(
                        "Unable to restore faction claim "
                                + location.getKey()
                );
            }
        }

        claimIndex.put(
                location,
                owner.getId()
        );

        if (claimGroupId != null
                && !owner.restoreClaimGroupAssignment(
                        location,
                        claimGroupId
                )) {
            throw new IllegalStateException(
                    "Unable to restore Claim Group assignment "
                            + claimGroupId
                            + " for "
                            + location.getKey()
            );
        }
    }

    private void checkAndRemoveHomeInChunk(
            Faction faction,
            FLocation chunkLocation
    ) {
        if (!faction.hasHome()) {
            return;
        }

        StoredLocation home =
                faction.getStoredHome();

        if (home == null
                || !home.isInChunk(
                        chunkLocation
                )) {
            return;
        }

        faction.restoreHome(null);

        for (Player player
                : faction.getOnlinePlayers()) {
            plugin.getMessageManager()
                    .send(
                            player,
                            "home.removed-unclaim"
                    );
        }
    }

    private void checkAndRemoveWarpsInChunk(
            Faction faction,
            FLocation chunkLocation
    ) {
        List<String> toRemove =
                new ArrayList<String>();

        for (Map.Entry<String, FactionWarp> entry
                : faction.getWarpDataSnapshot()
                        .entrySet()) {
            if (entry.getValue() == null
                    || entry.getValue()
                            .getStoredLocation() == null) {
                continue;
            }

            if (entry.getValue()
                    .getStoredLocation()
                    .isInChunk(
                            chunkLocation
                    )) {
                toRemove.add(
                        entry.getKey()
                );
            }
        }

        for (String warpName : toRemove) {
            faction.removeWarp(warpName);

            for (Player player
                    : faction.getOnlinePlayers()) {
                plugin.getMessageManager()
                        .send(
                                player,
                                "warp.removed-unclaim",
                                "{name}",
                                warpName
                        );
            }
        }
    }

    public void unclaimAll(
            Faction faction
    ) {
        if (faction == null
                || faction.isSystemFaction()) {
            return;
        }

        unclaimService.unclaimAll(
                null,
                faction,
                OperationContext.system()
        );
    }

    public boolean unclaim(
            FLocation location
    ) {
        if (location == null) {
            return false;
        }

        String zoneId =
                zoneService.getZoneIdAt(
                        location
                );

        if (zoneId != null) {
            return zoneService.clearZone(
                    location,
                    zoneId,
                    OperationContext.system()
            ).isSuccess();
        }

        String ownerId =
                claimIndex.get(location);

        if (ownerId == null) {
            return false;
        }

        Faction owner =
                plugin.getFactionManager()
                        .getFaction(ownerId);

        if (owner != null) {
            owner.removeClaim(location);

            checkAndRemoveHomeInChunk(
                    owner,
                    location
            );

            checkAndRemoveWarpsInChunk(
                    owner,
                    location
            );

            if (!owner.isSystemFaction()) {
                plugin.getStorageManager()
                        .markDirty(owner);
            }
        }

        claimIndex.remove(location);

        return true;
    }

    /**
     * Retire un éventuel claim joueur avant création d'une zone globale.
     *
     * Ne touche jamais ZoneService afin d'éviter toute récursion.
     */
    public boolean removePlayerClaimForZone(
            FLocation location
    ) {
        if (location == null) {
            return false;
        }

        String ownerId =
                claimIndex.get(location);

        if (ownerId == null) {
            return false;
        }

        Faction owner =
                plugin.getFactionManager()
                        .getFaction(ownerId);

        if (owner == null
                || owner.isSystemFaction()) {
            claimIndex.remove(location);
            return false;
        }

        owner.removeClaim(location);

        checkAndRemoveHomeInChunk(
                owner,
                location
        );

        checkAndRemoveWarpsInChunk(
                owner,
                location
        );

        claimIndex.remove(
                location,
                ownerId
        );

        plugin.getStorageManager()
                .markDirty(owner);

        return true;
    }

    // ============================================================
    // Zones système / admin
    // ============================================================

    public void claimWarzone(
            FLocation location
    ) {
        zoneService.setZone(
                location,
                GlobalZoneType.WARZONE,
                OperationContext.system()
        );
    }

    public void claimSafezone(
            FLocation location
    ) {
        zoneService.setZone(
                location,
                GlobalZoneType.SAFEZONE,
                OperationContext.system()
        );
    }

    public int getClaimCount() {
        return claimIndex.size()
                + zoneService.getTotalZoneChunks();
    }

    // ============================================================
    // Overclaim / limits
    // ============================================================

    public boolean isRaidable(
            Faction faction
    ) {
        if (faction == null
                || faction.isSystemFaction()) {
            return false;
        }

        if (plugin.getPermissionManager() != null
                && plugin.getPermissionManager()
                        .getGraceService()
                        .blocksRaids()) {
            return false;
        }

        double power =
                plugin.getPowerManager()
                        .getFactionPower(
                                faction
                        );

        return faction.getClaimCount()
                > power;
    }

    public int getOverclaimableCount(
            Faction faction
    ) {
        if (faction == null
                || faction.isSystemFaction()) {
            return 0;
        }

        if (plugin.getPermissionManager() != null
                && plugin.getPermissionManager()
                        .getGraceService()
                        .blocksRaids()) {
            return 0;
        }

        double power =
                plugin.getPowerManager()
                        .getFactionPower(
                                faction
                        );

        int deficit =
                faction.getClaimCount()
                        - (int) power;

        return Math.max(
                0,
                deficit
        );
    }

    public int getMaxClaims(
            Faction faction
    ) {
        if (faction == null
                || faction.isSystemFaction()) {
            return 0;
        }

        boolean limitByPower =
                plugin.getConfigManager()
                        .getBoolean(
                                "claims.limit-by-power",
                                true
                        );

        int configuredMax =
                plugin.getConfigManager()
                        .getInt(
                                "claims.max-per-faction",
                                -1
                        );

        if (!limitByPower) {
            return configuredMax > 0
                    ? configuredMax
                    : Integer.MAX_VALUE;
        }

        int powerMax =
                Math.max(
                        0,
                        (int) Math.floor(
                                plugin.getPowerManager()
                                        .getFactionPower(
                                                faction
                                        )
                        )
                );

        if (configuredMax > 0) {
            return Math.min(
                    powerMax,
                    configuredMax
            );
        }

        return powerMax;
    }

    public boolean isAdjacentToFaction(
            FLocation location,
            Faction faction
    ) {
        if (location == null
                || faction == null) {
            return false;
        }

        for (FLocation adjacent
                : location.getAdjacent()) {
            if (faction.hasClaim(
                    adjacent
            )) {
                return true;
            }
        }

        return false;
    }

    // ============================================================
    // Admin auto modes
    // ============================================================

    public boolean toggleAdminAutoClaim(
            UUID uuid,
            String type
    ) {
        String normalized =
                ZoneDefinition.normalizeId(
                        type
                );

        if (uuid == null
                || normalized == null
                || !zoneService.hasDefinition(
                        normalized
                )) {
            return false;
        }

        adminAutoUnclaimPlayers.remove(uuid);

        if (normalized.equals(
                adminAutoClaimPlayers.get(
                        uuid
                )
        )) {
            adminAutoClaimPlayers.remove(uuid);
            return false;
        }

        adminAutoClaimPlayers.put(
                uuid,
                normalized
        );

        return true;
    }

    public boolean isAdminAutoClaiming(
            UUID uuid
    ) {
        return adminAutoClaimPlayers
                .containsKey(uuid);
    }

    public String getAdminAutoClaimType(
            UUID uuid
    ) {
        return adminAutoClaimPlayers
                .get(uuid);
    }

    public void stopAdminAutoClaim(
            UUID uuid
    ) {
        adminAutoClaimPlayers.remove(uuid);
    }

    public boolean toggleAdminAutoUnclaim(
            UUID uuid,
            String type
    ) {
        String normalized =
                ZoneDefinition.normalizeId(
                        type
                );

        if (uuid == null
                || normalized == null
                || !zoneService.isKnownOrAssignedZoneId(
                        normalized
                )) {
            return false;
        }

        adminAutoClaimPlayers.remove(uuid);

        if (normalized.equals(
                adminAutoUnclaimPlayers.get(
                        uuid
                )
        )) {
            adminAutoUnclaimPlayers.remove(
                    uuid
            );
            return false;
        }

        adminAutoUnclaimPlayers.put(
                uuid,
                normalized
        );

        return true;
    }

    public boolean isAdminAutoUnclaiming(
            UUID uuid
    ) {
        return adminAutoUnclaimPlayers
                .containsKey(uuid);
    }

    public String getAdminAutoUnclaimType(
            UUID uuid
    ) {
        return adminAutoUnclaimPlayers
                .get(uuid);
    }

    public void stopAdminAutoUnclaim(
            UUID uuid
    ) {
        adminAutoUnclaimPlayers.remove(uuid);
    }

    public List<FLocation> getZoneClaims(
            String type
    ) {
        String zoneId =
                ZoneDefinition.normalizeId(
                        type
                );

        return zoneId == null
                ? new ArrayList<FLocation>()
                : new ArrayList<FLocation>(
                        zoneService.getLocations(
                                zoneId
                        )
                );
    }

    public boolean unclaimZone(
            String type,
            FLocation location
    ) {
        String zoneId =
                ZoneDefinition.normalizeId(
                        type
                );

        if (zoneId == null) {
            return false;
        }

        OperationResult<String> result =
                zoneService.clearZone(
                        location,
                        zoneId,
                        OperationContext.system()
                );

        return result.isSuccess();
    }

    public int unclaimZone(
            String type,
            Collection<FLocation> locations
    ) {
        int count = 0;

        if (locations == null) {
            return count;
        }

        for (FLocation location : locations) {
            if (unclaimZone(
                    type,
                    location
            )) {
                count++;
            }
        }

        return count;
    }

    // ============================================================
    // Index / diagnostics
    // ============================================================

    public int getClaimsInWorld(
            String worldName
    ) {
        int count = 0;

        for (FLocation location
                : claimIndex.keySet()) {
            if (location.getWorldName()
                    .equals(worldName)) {
                count++;
            }
        }

        return count;
    }

    public void registerClaim(
            FLocation location,
            String factionId
    ) {
        if (location != null
                && factionId != null) {
            claimIndex.put(
                    location,
                    factionId
            );
        }
    }

    public void rebuildIndex() {
        rebuildClaimIndex();
    }

    public int getTotalClaims() {
        return claimIndex.size();
    }

    public Relation getPlayerRelationToLocation(
            Player player
    ) {
        Faction factionAt =
                getFactionAt(
                        player.getLocation()
                );

        Faction playerFaction =
                plugin.getFactionManager()
                        .getPlayerFaction(
                                player
                        );

        if (playerFaction == null) {
            return Relation.NEUTRAL;
        }

        return playerFaction.getRelationTo(
                factionAt
        );
    }

    // ============================================================
    // Legacy result
    // ============================================================

    public static class ClaimResult {

        public static final ClaimResult SUCCESS =
                new ClaimResult(
                        true,
                        "Claim réussi"
                );

        public static final ClaimResult ALREADY_OWNED =
                new ClaimResult(
                        false,
                        "Vous possédez déjà ce chunk"
                );

        public static final ClaimResult OWNED_BY_OTHER =
                new ClaimResult(
                        false,
                        "Ce chunk appartient à une autre faction"
                );

        public static final ClaimResult LIMIT_REACHED =
                new ClaimResult(
                        false,
                        "Limite de claims atteinte (power insuffisant)"
                );

        public static final ClaimResult NOT_CONNECTED =
                new ClaimResult(
                        false,
                        "Le claim doit être connecté à votre territoire"
                );

        public static final ClaimResult TOO_CLOSE_TO_SPAWN =
                new ClaimResult(
                        false,
                        "Trop proche du spawn"
                );

        public static final ClaimResult NO_PERMISSION =
                new ClaimResult(
                        false,
                        "Vous n'avez pas la permission"
                );

        public static final ClaimResult WORLD_DISABLED =
                new ClaimResult(
                        false,
                        "Les claims sont désactivés dans ce monde"
                );

        public static final ClaimResult CANCELLED =
                new ClaimResult(
                        false,
                        "Claim annulé"
                );

        private final boolean success;
        private final String message;

        public ClaimResult(
                boolean success,
                String message
        ) {
            this.success = success;
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        public boolean isSuccess() {
            return success;
        }

        private static ClaimResult from(
                OperationResult<ClaimBatchResult> result
        ) {
            if (result == null) {
                return new ClaimResult(
                        false,
                        "Résultat de claim absent"
                );
            }

            if (result.isSuccess()) {
                return SUCCESS;
            }

            if (result.getStatus()
                    == OperationResult.Status.NO_CHANGE) {
                return ALREADY_OWNED;
            }

            if (result.getStatus()
                    == OperationResult.Status.CANCELLED) {
                return new ClaimResult(
                        false,
                        result.hasDetail()
                                ? result.getDetail()
                                : CANCELLED.getMessage()
                );
            }

            if (result.getStatus()
                    == OperationResult.Status.LIMIT_REACHED) {
                return new ClaimResult(
                        false,
                        result.hasDetail()
                                ? result.getDetail()
                                : LIMIT_REACHED.getMessage()
                );
            }

            if (result.getStatus()
                    == OperationResult.Status.FORBIDDEN) {
                return new ClaimResult(
                        false,
                        result.hasDetail()
                                ? result.getDetail()
                                : NO_PERMISSION.getMessage()
                );
            }

            return new ClaimResult(
                    false,
                    result.hasDetail()
                            ? result.getDetail()
                            : "Échec du claim"
            );
        }
    }

    public void showMap(
            Player player,
            FLocation location
    ) {
        player.sendMessage(
                "§6[§eMap§6] §7Position: "
                        + location.getX()
                        + ", "
                        + location.getZ()
        );

        Faction factionAt =
                getFactionAt(location);

        player.sendMessage(
                "§6[§eMap§6] §7Zone: "
                        + factionAt.getName()
        );
    }
}
