package me.krunsh.kfaction.services;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.api.event.FactionClaimEvent;
import me.krunsh.kfaction.api.event.FactionClaimEvent.ClaimType;
import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.core.operation.OperationResult;
import me.krunsh.kfaction.core.operation.OperationResult.Status;
import me.krunsh.kfaction.data.FLocation;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.managers.ClaimManager;
import me.krunsh.kfaction.permissions.FactionCapability;
import me.krunsh.kfaction.services.claim.ClaimBatchResult;
import me.krunsh.kfaction.services.claim.ClaimPlan;

/**
 * Service applicatif Claims V2.
 *
 * Pipeline :
 * validate request
 * -> construire un plan immuable
 * -> valider TOUT le plan
 * -> appeler tous les events PRE
 * -> revalider l'état
 * -> commit de toutes les mutations
 * -> dirty/persist via ClaimManager
 *
 * Le thread principal Bukkit reste propriétaire des mutations du domaine.
 */
public final class ClaimService {

    private final Kfaction plugin;
    private final ClaimManager claimManager;

    public ClaimService(
            Kfaction plugin,
            ClaimManager claimManager
    ) {
        if (plugin == null || claimManager == null) {
            throw new IllegalArgumentException(
                    "plugin/claimManager cannot be null"
            );
        }

        this.plugin = plugin;
        this.claimManager = claimManager;
    }

    // ============================================================
    // API
    // ============================================================

    public OperationResult<ClaimBatchResult> claimSingle(
            Player actor,
            Faction faction,
            FLocation location,
            OperationContext context
    ) {
        if (location == null) {
            return failure(
                    Status.INVALID_INPUT,
                    "Location de claim invalide"
            );
        }

        List<FLocation> requested =
                new ArrayList<FLocation>(1);

        requested.add(location);

        return execute(
                actor,
                faction,
                requested,
                ClaimPlan.Mode.SINGLE,
                context
        );
    }

    /**
     * Compatibilité de sémantique actuelle :
     * radius=1 -> 1x1
     * radius=2 -> 3x3
     * radius=3 -> 5x5
     */
    public OperationResult<ClaimBatchResult> claimRadius(
            Player actor,
            Faction faction,
            FLocation center,
            int radius,
            OperationContext context
    ) {
        if (center == null || radius < 1) {
            return failure(
                    Status.INVALID_INPUT,
                    "Rayon de claim invalide"
            );
        }

        int maxRadius =
                clamp(
                        plugin.getConfigManager()
                                .getInt(
                                        "claims.radius.max",
                                        10
                                ),
                        1,
                        64
                );

        if (radius > maxRadius) {
            return failure(
                    Status.LIMIT_REACHED,
                    "Rayon maximum: " + maxRadius
            );
        }

        int offset = radius - 1;
        long side = (long) offset * 2L + 1L;
        long requestedCount = side * side;

        int maxBatch =
                getMaxBatchChunks();

        if (requestedCount > maxBatch) {
            return failure(
                    Status.LIMIT_REACHED,
                    "Ce rayon représente "
                            + requestedCount
                            + " chunks, limite batch: "
                            + maxBatch
            );
        }

        List<FLocation> requested =
                new ArrayList<FLocation>(
                        (int) requestedCount
                );

        for (int dx = -offset;
                dx <= offset;
                dx++) {
            for (int dz = -offset;
                    dz <= offset;
                    dz++) {
                requested.add(
                        center.getRelative(
                                dx,
                                dz
                        )
                );
            }
        }

        return execute(
                actor,
                faction,
                requested,
                radius == 1
                        ? ClaimPlan.Mode.SINGLE
                        : ClaimPlan.Mode.RADIUS,
                context
        );
    }

    /**
     * Remplit uniquement une poche Wilderness fermée par la propre faction.
     *
     * Si la recherche dépasse max-chunks, touche une faction tierce/zone
     * système ou n'a aucune bordure de la faction, aucune mutation n'est faite.
     */
    public OperationResult<ClaimBatchResult> claimFill(
            Player actor,
            Faction faction,
            FLocation start,
            OperationContext context
    ) {
        if (!plugin.getConfigManager()
                .getBoolean(
                        "claims.fill.enabled",
                        true
                )) {
            return failure(
                    Status.FORBIDDEN,
                    "Le claim fill est désactivé"
            );
        }

        if (start == null) {
            return failure(
                    Status.INVALID_INPUT,
                    "Position de fill invalide"
            );
        }

        if (faction == null
                || faction.isSystemFaction()) {
            return failure(
                    Status.INVALID_INPUT,
                    "Faction invalide"
            );
        }

        String startOwner =
                claimManager.getFactionIdAt(start);

        if (faction.getId().equals(startOwner)) {
            return OperationResult.noChange(
                    "claim.already-owned"
            );
        }

        if (startOwner != null) {
            return failure(
                    Status.CONFLICT,
                    "Le chunk de départ appartient déjà à une autre faction"
            );
        }

        int maxFill =
                clamp(
                        plugin.getConfigManager()
                                .getInt(
                                        "claims.fill.max-chunks",
                                        256
                                ),
                        1,
                        4096
                );

        int maxBatch =
                getMaxBatchChunks();

        maxFill =
                Math.min(
                        maxFill,
                        maxBatch
                );

        Queue<FLocation> queue =
                new ArrayDeque<FLocation>();

        LinkedHashSet<FLocation> region =
                new LinkedHashSet<FLocation>();

        HashSet<FLocation> queued =
                new HashSet<FLocation>();

        queue.add(start);
        queued.add(start);

        boolean ownBoundarySeen = false;

        while (!queue.isEmpty()) {
            FLocation current =
                    queue.remove();

            String ownerId =
                    claimManager.getFactionIdAt(
                            current
                    );

            if (ownerId != null) {
                if (faction.getId()
                        .equals(ownerId)) {
                    ownBoundarySeen = true;
                    continue;
                }

                return failure(
                        Status.CONFLICT,
                        "Fill refusé: la zone touche une faction/zone étrangère"
                );
            }

            region.add(current);

            if (region.size() > maxFill) {
                return failure(
                        Status.LIMIT_REACHED,
                        "Fill refusé: zone ouverte ou supérieure à "
                                + maxFill
                                + " chunks"
                );
            }

            FLocation[] adjacent =
                    current.getAdjacent();

            for (FLocation next : adjacent) {
                String adjacentOwner =
                        claimManager.getFactionIdAt(
                                next
                        );

                if (faction.getId()
                        .equals(adjacentOwner)) {
                    ownBoundarySeen = true;
                    continue;
                }

                if (adjacentOwner != null) {
                    return failure(
                            Status.CONFLICT,
                            "Fill refusé: bordure étrangère détectée à "
                                    + next.toReadableString()
                    );
                }

                if (queued.add(next)) {
                    queue.add(next);
                }
            }
        }

        if (!ownBoundarySeen) {
            return failure(
                    Status.CONFLICT,
                    "Fill refusé: aucune bordure de votre faction"
            );
        }

        return execute(
                actor,
                faction,
                region,
                ClaimPlan.Mode.FILL,
                context
        );
    }

    // ============================================================
    // Pipeline
    // ============================================================

    private OperationResult<ClaimBatchResult> execute(
            Player actor,
            Faction faction,
            Collection<FLocation> requested,
            ClaimPlan.Mode mode,
            OperationContext context
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return failure(
                    Status.UNAVAILABLE,
                    "Les mutations de claim doivent être exécutées sur le thread principal"
            );
        }

        if (faction == null
                || faction.isSystemFaction()) {
            return failure(
                    Status.INVALID_INPUT,
                    "Faction de claim invalide"
            );
        }

        if (context == null) {
            return failure(
                    Status.INVALID_INPUT,
                    "OperationContext manquant"
            );
        }

        if (actor != null) {
            if (!faction.isMember(
                    actor.getUniqueId()
            )) {
                return failure(
                        Status.FORBIDDEN,
                        "Vous n'êtes pas membre de cette faction"
                );
            }

            if (!plugin.getPermissionManager()
                    .can(
                            actor,
                            FactionCapability.CLAIM
                    )) {
                return failure(
                        Status.FORBIDDEN,
                        "Vous n'avez pas la permission de claim"
                );
            }
        }

        OperationResult<ClaimPlan> planned =
                buildPlan(
                        faction,
                        requested,
                        mode
                );

        if (!planned.isSuccess()) {
            if (planned.getStatus()
                    == Status.NO_CHANGE) {
                return OperationResult.noChange(
                        planned.getMessageKey()
                );
            }

            return OperationResult.failure(
                    planned.getStatus(),
                    planned.getMessageKey(),
                    planned.getDetail()
            );
        }

        ClaimPlan plan =
                planned.getValue();

        OperationResult<ClaimBatchResult> events =
                firePreEvents(
                        actor,
                        faction,
                        plan
                );

        if (events != null) {
            return events;
        }

        OperationResult<ClaimBatchResult> stale =
                revalidateBeforeCommit(
                        faction,
                        plan
                );

        if (stale != null) {
            return stale;
        }

        return commit(
                faction,
                plan
        );
    }

    private OperationResult<ClaimPlan> buildPlan(
            Faction faction,
            Collection<FLocation> requested,
            ClaimPlan.Mode mode
    ) {
        if (requested == null
                || requested.isEmpty()) {
            return OperationResult.noChange(
                    "claim.already-owned"
            );
        }

        LinkedHashSet<FLocation> unique =
                new LinkedHashSet<FLocation>();

        for (FLocation location : requested) {
            if (location == null) {
                return failurePlan(
                        Status.INVALID_INPUT,
                        "Une location du batch est invalide"
                );
            }

            unique.add(location);
        }

        int maxBatch =
                getMaxBatchChunks();

        if (unique.size() > maxBatch) {
            return failurePlan(
                    Status.LIMIT_REACHED,
                    "Batch trop grand: "
                            + unique.size()
                            + "/"
                            + maxBatch
            );
        }

        List<ClaimPlan.Entry> entries =
                new ArrayList<ClaimPlan.Entry>();

        int skippedOwned = 0;

        for (FLocation location : unique) {
            OperationResult<ClaimPlan.Entry> entry =
                    validateEntry(
                            faction,
                            location
                    );

            if (entry.getStatus()
                    == Status.NO_CHANGE) {
                skippedOwned++;
                continue;
            }

            if (!entry.isSuccess()) {
                return OperationResult.failure(
                        entry.getStatus(),
                        entry.getMessageKey(),
                        entry.getDetail()
                );
            }

            entries.add(
                    entry.getValue()
            );
        }

        if (entries.isEmpty()) {
            return OperationResult.noChange(
                    "claim.already-owned"
            );
        }

        int maxClaims =
                claimManager.getMaxClaims(
                        faction
                );

        long futureClaims =
                (long) faction.getClaimCount()
                        + entries.size();

        if (futureClaims > maxClaims) {
            int available =
                    Math.max(
                            0,
                            maxClaims
                                    - faction.getClaimCount()
                    );

            return failurePlan(
                    Status.LIMIT_REACHED,
                    "Power/limite insuffisant: "
                            + entries.size()
                            + " nouveaux chunks demandés, "
                            + available
                            + " disponibles"
            );
        }

        if (plugin.getConfigManager()
                .getBoolean(
                        "claims.require-connected",
                        false
                )) {
            if (!isPlanConnectedToFaction(
                    faction,
                    entries
            )) {
                return failurePlan(
                        Status.CONFLICT,
                        "Le batch doit être connecté à votre territoire"
                );
            }
        }

        return OperationResult.success(
                new ClaimPlan(
                        mode,
                        entries,
                        unique.size(),
                        skippedOwned
                )
        );
    }

    private OperationResult<ClaimPlan.Entry> validateEntry(
            Faction faction,
            FLocation location
    ) {
        if (!isWorldAllowed(
                location.getWorldName()
        )) {
            return OperationResult.failure(
                    Status.FORBIDDEN,
                    "claim.world-not-allowed",
                    "Les claims sont désactivés dans ce monde"
            );
        }

        World world =
                location.getWorld();

        if (world == null) {
            return OperationResult.failure(
                    Status.UNAVAILABLE,
                    "claim.world-not-allowed",
                    "Le monde '"
                            + location.getWorldName()
                            + "' n'est pas chargé"
            );
        }

        int minSpawnDistance =
                Math.max(
                        0,
                        plugin.getConfigManager()
                                .getInt(
                                        "claims.min-distance-spawn",
                                        0
                                )
                );

        if (minSpawnDistance > 0) {
            FLocation spawn =
                    new FLocation(
                            world.getSpawnLocation()
                    );

            int distance =
                    location.distanceTo(
                            spawn
                    );

            if (distance >= 0
                    && distance < minSpawnDistance) {
                return OperationResult.failure(
                        Status.FORBIDDEN,
                        "claim.failed",
                        "Trop proche du spawn (minimum "
                                + minSpawnDistance
                                + " chunks)"
                );
            }
        }

        String currentOwnerId =
                claimManager.getFactionIdAt(
                        location
                );

        if (currentOwnerId == null) {
            return OperationResult.success(
                    new ClaimPlan.Entry(
                            location,
                            null,
                            ClaimType.CLAIM
                    )
            );
        }

        if (faction.getId()
                .equals(currentOwnerId)) {
            return OperationResult.noChange(
                    "claim.already-owned"
            );
        }

        Faction currentOwner =
                plugin.getFactionManager()
                        .getFaction(
                                currentOwnerId
                        );

        if (currentOwner == null) {
            return OperationResult.failure(
                    Status.CONFLICT,
                    "claim.failed",
                    "Index de claim incohérent: propriétaire introuvable "
                            + currentOwnerId
            );
        }

        if (currentOwner.isSystemFaction()) {
            return OperationResult.failure(
                    Status.FORBIDDEN,
                    "claim.cannot-claim-zone",
                    "Impossible de surclaim une zone système"
            );
        }

        if (!claimManager.isRaidable(
                currentOwner
        )) {
            return OperationResult.failure(
                    Status.CONFLICT,
                    "claim.cannot-surclaim",
                    "Le chunk appartient à "
                            + currentOwner.getName()
                            + " et cette faction n'est pas raidable"
            );
        }

        return OperationResult.success(
                new ClaimPlan.Entry(
                        location,
                        currentOwnerId,
                        ClaimType.OVERCLAIM
                )
        );
    }

    private OperationResult<ClaimBatchResult> firePreEvents(
            Player actor,
            Faction faction,
            ClaimPlan plan
    ) {
        if (actor == null) {
            return null;
        }

        for (ClaimPlan.Entry entry
                : plan.getEntries()) {
            FLocation location =
                    entry.getLocation();

            World world =
                    location.getWorld();

            if (world == null) {
                return failure(
                        Status.UNAVAILABLE,
                        "Monde déchargé avant l'event"
                );
            }

            Chunk chunk =
                    world.getChunkAt(
                            location.getX(),
                            location.getZ()
                    );

            Faction previousOwner = null;

            if (entry.getPreviousOwnerId()
                    != null) {
                previousOwner =
                        plugin.getFactionManager()
                                .getFaction(
                                        entry.getPreviousOwnerId()
                                );
            }

            FactionClaimEvent event =
                    new FactionClaimEvent(
                            actor,
                            faction,
                            chunk,
                            previousOwner,
                            entry.getClaimType()
                    );

            Bukkit.getPluginManager()
                    .callEvent(event);

            if (event.isCancelled()) {
                String reason =
                        event.getCancelReason();

                if (reason == null
                        || reason.trim().isEmpty()) {
                    reason =
                            "Claim annulé par un plugin externe";
                }

                return failure(
                        Status.CANCELLED,
                        reason
                );
            }
        }

        return null;
    }

    /**
     * Les listeners d'un event PRE peuvent eux-mêmes modifier le monde/domaine.
     * On revalide donc les propriétaires et la limite juste avant le commit.
     */
    private OperationResult<ClaimBatchResult> revalidateBeforeCommit(
            Faction faction,
            ClaimPlan plan
    ) {
        for (ClaimPlan.Entry entry
                : plan.getEntries()) {
            String current =
                    claimManager.getFactionIdAt(
                            entry.getLocation()
                    );

            String expected =
                    entry.getPreviousOwnerId();

            if (!same(
                    current,
                    expected
            )) {
                return failure(
                        Status.CONFLICT,
                        "Le territoire a changé pendant la validation; aucun chunk n'a été appliqué"
                );
            }

            if (expected != null) {
                Faction owner =
                        plugin.getFactionManager()
                                .getFaction(expected);

                if (owner == null
                        || owner.isSystemFaction()
                        || !claimManager.isRaidable(
                                owner
                        )) {
                    return failure(
                            Status.CONFLICT,
                            "Un surclaim n'est plus valide; aucun chunk n'a été appliqué"
                    );
                }
            }
        }

        int maxClaims =
                claimManager.getMaxClaims(
                        faction
                );

        if ((long) faction.getClaimCount()
                + plan.getMutationCount()
                > maxClaims) {
            return failure(
                    Status.LIMIT_REACHED,
                    "La limite de claims a changé avant le commit"
            );
        }

        if (plugin.getConfigManager()
                .getBoolean(
                        "claims.require-connected",
                        false
                )
                && !isPlanConnectedToFaction(
                        faction,
                        plan.getEntries()
                )) {
            return failure(
                    Status.CONFLICT,
                    "Le territoire n'est plus connecté avant le commit"
            );
        }

        return null;
    }

    private OperationResult<ClaimBatchResult> commit(
            Faction faction,
            ClaimPlan plan
    ) {
        List<FLocation> changed =
                new ArrayList<FLocation>(
                        plan.getMutationCount()
                );

        try {
            for (ClaimPlan.Entry entry
                    : plan.getEntries()) {
                claimManager.commitPlannedClaim(
                        faction,
                        entry.getLocation(),
                        entry.getPreviousOwnerId()
                );

                changed.add(
                        entry.getLocation()
                );
            }
        } catch (RuntimeException exception) {
            /*
             * Ce chemin ne devrait jamais être atteint après revalidation sur
             * le main thread. On log très fort car une exception ici indique
             * une violation d'invariant interne, pas une erreur utilisateur.
             */
            plugin.getLogger().severe(
                    "ClaimService commit invariant failure: "
                            + exception.getMessage()
            );

            return failure(
                    Status.FAILED,
                    "Erreur interne pendant le commit des claims"
            );
        }

        return OperationResult.success(
                new ClaimBatchResult(
                        plan.getMode(),
                        plan.getRequestedCount(),
                        changed.size(),
                        plan.getSkippedOwnedCount(),
                        plan.getOverclaimCount(),
                        changed
                )
        );
    }

    // ============================================================
    // Connectivité / règles
    // ============================================================

    private boolean isPlanConnectedToFaction(
            Faction faction,
            List<ClaimPlan.Entry> entries
    ) {
        if (entries.isEmpty()
                || faction.getClaimCount() == 0) {
            return true;
        }

        Set<FLocation> pending =
                new HashSet<FLocation>();

        for (ClaimPlan.Entry entry : entries) {
            pending.add(
                    entry.getLocation()
            );
        }

        Queue<FLocation> queue =
                new ArrayDeque<FLocation>();

        Set<FLocation> visited =
                new HashSet<FLocation>();

        for (FLocation location : pending) {
            if (touchesExistingClaim(
                    faction,
                    location
            )) {
                queue.add(location);
                visited.add(location);
            }
        }

        if (queue.isEmpty()) {
            return false;
        }

        while (!queue.isEmpty()) {
            FLocation current =
                    queue.remove();

            for (FLocation adjacent
                    : current.getAdjacent()) {
                if (pending.contains(adjacent)
                        && visited.add(adjacent)) {
                    queue.add(adjacent);
                }
            }
        }

        return visited.size()
                == pending.size();
    }

    private boolean touchesExistingClaim(
            Faction faction,
            FLocation location
    ) {
        for (FLocation adjacent
                : location.getAdjacent()) {
            if (faction.hasClaim(adjacent)) {
                return true;
            }
        }

        return false;
    }

    private boolean isWorldAllowed(
            String worldName
    ) {
        if (worldName == null) {
            return false;
        }

        List<String> denied =
                plugin.getConfigManager()
                        .getStringList(
                                "claims.denied-worlds"
                        );

        if (containsIgnoreCase(
                denied,
                worldName
        )) {
            return false;
        }

        List<String> allowed =
                plugin.getConfigManager()
                        .getStringList(
                                "claims.allowed-worlds"
                        );

        return allowed == null
                || allowed.isEmpty()
                || containsIgnoreCase(
                        allowed,
                        worldName
                );
    }

    private int getMaxBatchChunks() {
        return clamp(
                plugin.getConfigManager()
                        .getInt(
                                "claims.batch.max-chunks",
                                400
                        ),
                1,
                4096
        );
    }

    private static boolean containsIgnoreCase(
            List<String> values,
            String target
    ) {
        if (values == null || target == null) {
            return false;
        }

        String normalized =
                target.toLowerCase(
                        Locale.ROOT
                );

        for (String value : values) {
            if (value != null
                    && value.toLowerCase(
                            Locale.ROOT
                    ).equals(normalized)) {
                return true;
            }
        }

        return false;
    }

    private static boolean same(
            String left,
            String right
    ) {
        return left == null
                ? right == null
                : left.equals(right);
    }

    private static int clamp(
            int value,
            int min,
            int max
    ) {
        return Math.max(
                min,
                Math.min(
                        max,
                        value
                )
        );
    }

    private static <T> OperationResult<T> failure(
            Status status,
            String detail
    ) {
        return OperationResult.failure(
                status,
                "claim.failed",
                detail
        );
    }

    private static OperationResult<ClaimPlan> failurePlan(
            Status status,
            String detail
    ) {
        return OperationResult.failure(
                status,
                "claim.failed",
                detail
        );
    }
}
