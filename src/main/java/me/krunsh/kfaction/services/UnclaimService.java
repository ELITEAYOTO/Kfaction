package me.krunsh.kfaction.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.api.event.FactionUnclaimEvent;
import me.krunsh.kfaction.api.event.FactionUnclaimEvent.UnclaimType;
import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.core.operation.OperationResult;
import me.krunsh.kfaction.core.operation.OperationResult.Status;
import me.krunsh.kfaction.data.FLocation;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionWarp;
import me.krunsh.kfaction.data.StoredLocation;
import me.krunsh.kfaction.permissions.FactionCapability;
import me.krunsh.kfaction.services.claim.UnclaimBatchResult;
import me.krunsh.kfaction.services.claim.UnclaimPlan;

/**
 * Service applicatif Unclaim V2.
 *
 * Pipeline:
 * validate -> plan immutable -> PRE events -> revalidation -> commit
 * -> cleanup home/warps -> dirty -> notifications.
 */
public final class UnclaimService {

    private final Kfaction plugin;

    public UnclaimService(Kfaction plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException(
                    "plugin cannot be null"
            );
        }

        this.plugin = plugin;
    }

    // ============================================================
    // Preview
    // ============================================================

    /**
     * Retourne uniquement les chunks actuellement possédés par la faction
     * dans le rayon demandé.
     *
     * Sémantique commune avec /f claim:
     * 1 = 1x1
     * 2 = 3x3
     * 3 = 5x5
     */
    public OperationResult<List<FLocation>> previewRadius(
            Faction faction,
            FLocation center,
            int radius
    ) {
        if (!isValidFaction(faction)
                || center == null
                || radius < 1) {
            return failure(
                    Status.INVALID_INPUT,
                    "Faction/centre/rayon invalide"
            );
        }

        int maxRadius =
                getMaxRadius();

        if (radius > maxRadius) {
            return failure(
                    Status.LIMIT_REACHED,
                    "Rayon maximum: " + maxRadius
            );
        }

        int offset = radius - 1;

        List<FLocation> locations =
                new ArrayList<FLocation>();

        for (int dx = -offset;
                dx <= offset;
                dx++) {
            for (int dz = -offset;
                    dz <= offset;
                    dz++) {
                FLocation location =
                        center.getRelative(
                                dx,
                                dz
                        );

                String ownerId =
                        plugin.getClaimManager()
                                .getFactionIdAt(
                                        location
                                );

                if (faction.getId()
                        .equals(ownerId)) {
                    locations.add(location);
                }
            }
        }

        if (locations.isEmpty()) {
            return OperationResult.noChange(
                    "unclaim.none-owned"
            );
        }

        return OperationResult.success(
                Collections.unmodifiableList(
                        new ArrayList<FLocation>(
                                locations
                        )
                )
        );
    }

    // ============================================================
    // API mutation
    // ============================================================

    public OperationResult<UnclaimBatchResult> unclaimSingle(
            Player actor,
            Faction faction,
            FLocation location,
            OperationContext context
    ) {
        if (location == null) {
            return failure(
                    Status.INVALID_INPUT,
                    "Chunk invalide"
            );
        }

        List<FLocation> locations =
                new ArrayList<FLocation>(1);

        locations.add(location);

        return execute(
                actor,
                faction,
                locations,
                UnclaimType.SINGLE,
                context
        );
    }

    public OperationResult<UnclaimBatchResult> unclaimRadius(
            Player actor,
            Faction faction,
            FLocation center,
            int radius,
            OperationContext context
    ) {
        OperationResult<List<FLocation>> preview =
                previewRadius(
                        faction,
                        center,
                        radius
                );

        if (!preview.isSuccess()) {
            if (preview.getStatus()
                    == Status.NO_CHANGE) {
                return OperationResult.noChange(
                        preview.getMessageKey()
                );
            }

            return OperationResult.failure(
                    preview.getStatus(),
                    preview.getMessageKey(),
                    preview.getDetail()
            );
        }

        return execute(
                actor,
                faction,
                preview.getValue(),
                radius == 1
                        ? UnclaimType.SINGLE
                        : UnclaimType.RADIUS,
                context
        );
    }

    /**
     * Exécute exactement le snapshot fourni.
     *
     * Utilisé après confirmation afin qu'un nouveau claim apparu dans le
     * rayon entre la demande et le clic ne soit jamais supprimé par surprise.
     */
    public OperationResult<UnclaimBatchResult> unclaimExact(
            Player actor,
            Faction faction,
            Collection<FLocation> locations,
            UnclaimType type,
            OperationContext context
    ) {
        return execute(
                actor,
                faction,
                locations,
                type,
                context
        );
    }

    public OperationResult<UnclaimBatchResult> unclaimAll(
            Player actor,
            Faction faction,
            OperationContext context
    ) {
        if (!isValidFaction(faction)) {
            return failure(
                    Status.INVALID_INPUT,
                    "Faction invalide"
            );
        }

        return execute(
                actor,
                faction,
                new ArrayList<FLocation>(
                        faction.getClaims()
                ),
                UnclaimType.ALL,
                context
        );
    }

    // ============================================================
    // Pipeline
    // ============================================================

    private OperationResult<UnclaimBatchResult> execute(
            Player actor,
            Faction faction,
            Collection<FLocation> requested,
            UnclaimType type,
            OperationContext context
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return failure(
                    Status.UNAVAILABLE,
                    "L'unclaim doit être exécuté sur le thread principal"
            );
        }

        if (!isValidFaction(faction)
                || requested == null
                || type == null
                || context == null) {
            return failure(
                    Status.INVALID_INPUT,
                    "Paramètres d'unclaim invalides"
            );
        }

        if (actor != null) {
            if (!faction.isMember(
                    actor.getUniqueId()
            )) {
                return failure(
                        Status.FORBIDDEN,
                        "Vous n'êtes plus membre de cette faction"
                );
            }

            if (!plugin.getPermissionManager()
                    .can(
                            actor,
                            FactionCapability.UNCLAIM
                    )) {
                return failure(
                        Status.FORBIDDEN,
                        "Vous n'avez pas la permission d'unclaim"
                );
            }
        }

        OperationResult<UnclaimPlan> planned =
                buildPlan(
                        faction,
                        requested,
                        type
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

        UnclaimPlan plan =
                planned.getValue();

        OperationResult<UnclaimBatchResult> eventFailure =
                firePreEvents(
                        actor,
                        faction,
                        plan
                );

        if (eventFailure != null) {
            return eventFailure;
        }

        OperationResult<UnclaimBatchResult> stale =
                revalidate(
                        faction,
                        plan
                );

        if (stale != null) {
            return stale;
        }

        CleanupSnapshot cleanup =
                captureCleanup(
                        faction,
                        plan
                );

        return commit(
                faction,
                plan,
                cleanup
        );
    }

    private OperationResult<UnclaimPlan> buildPlan(
            Faction faction,
            Collection<FLocation> requested,
            UnclaimType type
    ) {
        if (requested.isEmpty()) {
            return OperationResult.noChange(
                    "unclaim.none-owned"
            );
        }

        LinkedHashSet<FLocation> unique =
                new LinkedHashSet<FLocation>();

        for (FLocation location : requested) {
            if (location == null) {
                return failurePlan(
                        Status.INVALID_INPUT,
                        "Un chunk du batch est invalide"
                );
            }

            unique.add(location);
        }

        int maxBatch =
                getMaxBatchChunks();

        if (unique.size() > maxBatch) {
            return failurePlan(
                    Status.LIMIT_REACHED,
                    "Batch d'unclaim trop grand: "
                            + unique.size()
                            + "/"
                            + maxBatch
            );
        }

        List<UnclaimPlan.Entry> entries =
                new ArrayList<UnclaimPlan.Entry>(
                        unique.size()
                );

        for (FLocation location : unique) {
            String ownerId =
                    plugin.getClaimManager()
                            .getFactionIdAt(
                                    location
                            );

            if (!faction.getId()
                    .equals(ownerId)
                    || !faction.hasClaim(location)) {
                return failurePlan(
                        Status.CONFLICT,
                        "Le chunk "
                                + location.toReadableString()
                                + " n'appartient plus à la faction"
                );
            }

            entries.add(
                    new UnclaimPlan.Entry(
                            location,
                            faction.getClaimGroupId(
                                    location
                            )
                    )
            );
        }

        return OperationResult.success(
                new UnclaimPlan(
                        type,
                        entries
                )
        );
    }

    private OperationResult<UnclaimBatchResult> firePreEvents(
            Player actor,
            Faction faction,
            UnclaimPlan plan
    ) {
        if (actor == null) {
            return null;
        }

        for (UnclaimPlan.Entry entry
                : plan.getEntries()) {
            FLocation location =
                    entry.getLocation();

            World world =
                    location.getWorld();

            if (world == null) {
                return failure(
                        Status.UNAVAILABLE,
                        "Le monde "
                                + location.getWorldName()
                                + " n'est plus chargé"
                );
            }

            Chunk chunk =
                    world.getChunkAt(
                            location.getX(),
                            location.getZ()
                    );

            FactionUnclaimEvent event =
                    new FactionUnclaimEvent(
                            actor,
                            faction,
                            chunk,
                            plan.getType()
                    );

            Bukkit.getPluginManager()
                    .callEvent(event);

            if (event.isCancelled()) {
                String reason =
                        event.getCancelReason();

                if (reason == null
                        || reason.trim().isEmpty()) {
                    reason =
                            "Unclaim annulé par un plugin externe";
                }

                return failure(
                        Status.CANCELLED,
                        reason
                );
            }
        }

        return null;
    }

    private OperationResult<UnclaimBatchResult> revalidate(
            Faction faction,
            UnclaimPlan plan
    ) {
        for (UnclaimPlan.Entry entry
                : plan.getEntries()) {
            FLocation location =
                    entry.getLocation();

            String ownerId =
                    plugin.getClaimManager()
                            .getFactionIdAt(
                                    location
                            );

            if (!faction.getId()
                    .equals(ownerId)
                    || !faction.hasClaim(location)) {
                return failure(
                        Status.CONFLICT,
                        "Le territoire a changé pendant la validation; aucun chunk n'a été libéré"
                );
            }

            String groupId =
                    faction.getClaimGroupId(
                            location
                    );

            if (!same(
                    groupId,
                    entry.getClaimGroupId()
            )) {
                return failure(
                        Status.CONFLICT,
                        "L'affectation Claim Group a changé pendant la validation"
                );
            }
        }

        return null;
    }

    private OperationResult<UnclaimBatchResult> commit(
            Faction faction,
            UnclaimPlan plan,
            CleanupSnapshot cleanup
    ) {
        List<UnclaimPlan.Entry> committed =
                new ArrayList<UnclaimPlan.Entry>();

        try {
            for (UnclaimPlan.Entry entry
                    : plan.getEntries()) {
                plugin.getClaimManager()
                        .commitPlannedUnclaim(
                                faction,
                                entry.getLocation()
                        );

                committed.add(entry);
            }

            applyCleanup(
                    faction,
                    cleanup
            );

            plugin.getStorageManager()
                    .markDirty(faction);

        } catch (RuntimeException exception) {
            rollback(
                    faction,
                    committed,
                    cleanup
            );

            plugin.getStorageManager()
                    .markDirty(faction);

            plugin.getLogger().severe(
                    "UnclaimService atomic commit failure: "
                            + exception.getMessage()
            );

            return failure(
                    Status.FAILED,
                    "Erreur interne: batch restauré"
            );
        }

        notifyCleanup(
                faction,
                cleanup
        );

        List<FLocation> locations =
                new ArrayList<FLocation>(
                        plan.size()
                );

        int groupAssignments = 0;

        for (UnclaimPlan.Entry entry
                : plan.getEntries()) {
            locations.add(
                    entry.getLocation()
            );

            if (entry.getClaimGroupId() != null) {
                groupAssignments++;
            }
        }

        return OperationResult.success(
                new UnclaimBatchResult(
                        plan.getType(),
                        plan.size(),
                        groupAssignments,
                        cleanup.home != null,
                        new ArrayList<String>(
                                cleanup.warps.keySet()
                        ),
                        locations
                )
        );
    }

    private void rollback(
            Faction faction,
            List<UnclaimPlan.Entry> committed,
            CleanupSnapshot cleanup
    ) {
        // Le cleanup n'est normalement appliqué qu'après tous les chunks,
        // mais restaurer ces valeurs rend le rollback idempotent.
        if (cleanup.home != null) {
            faction.restoreHome(
                    cleanup.home
            );
        }

        for (FactionWarp warp
                : cleanup.warps.values()) {
            faction.restoreWarp(warp);
        }

        for (int i = committed.size() - 1;
                i >= 0;
                i--) {
            UnclaimPlan.Entry entry =
                    committed.get(i);

            plugin.getClaimManager()
                    .rollbackPlannedUnclaim(
                            faction,
                            entry.getLocation(),
                            entry.getClaimGroupId()
                    );
        }
    }

    // ============================================================
    // Cleanup home/warps
    // ============================================================

    private CleanupSnapshot captureCleanup(
            Faction faction,
            UnclaimPlan plan
    ) {
        Set<FLocation> locations =
                new HashSet<FLocation>();

        for (UnclaimPlan.Entry entry
                : plan.getEntries()) {
            locations.add(
                    entry.getLocation()
            );
        }

        StoredLocation home = null;

        if (faction.hasHome()
                && faction.getStoredHome() != null) {
            StoredLocation candidate =
                    faction.getStoredHome();

            for (FLocation location : locations) {
                if (candidate.isInChunk(
                        location
                )) {
                    home = candidate;
                    break;
                }
            }
        }

        Map<String, FactionWarp> warps =
                new HashMap<String, FactionWarp>();

        for (Map.Entry<String, FactionWarp> entry
                : faction.getWarpDataSnapshot()
                        .entrySet()) {
            FactionWarp warp =
                    entry.getValue();

            if (warp == null
                    || warp.getStoredLocation() == null) {
                continue;
            }

            for (FLocation location : locations) {
                if (warp.getStoredLocation()
                        .isInChunk(
                                location
                        )) {
                    warps.put(
                            entry.getKey(),
                            warp
                    );
                    break;
                }
            }
        }

        return new CleanupSnapshot(
                home,
                warps
        );
    }

    private void applyCleanup(
            Faction faction,
            CleanupSnapshot cleanup
    ) {
        if (cleanup.home != null) {
            faction.restoreHome(null);
        }

        for (String warpName
                : cleanup.warps.keySet()) {
            if (!faction.removeWarp(
                    warpName
            )) {
                throw new IllegalStateException(
                        "Unable to remove warp "
                                + warpName
                );
            }
        }
    }

    private void notifyCleanup(
            Faction faction,
            CleanupSnapshot cleanup
    ) {
        if (cleanup.home != null) {
            for (Player player
                    : faction.getOnlinePlayers()) {
                plugin.getMessageManager()
                        .send(
                                player,
                                "home.removed-unclaim"
                        );
            }
        }

        for (String warpName
                : cleanup.warps.keySet()) {
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

    // ============================================================
    // Config/helpers
    // ============================================================

    private int getMaxRadius() {
        return clamp(
                plugin.getConfigManager()
                        .getInt(
                                "claims.unclaim.radius.max",
                                plugin.getConfigManager()
                                        .getInt(
                                                "claims.radius.max",
                                                10
                                        )
                        ),
                1,
                64
        );
    }

    private int getMaxBatchChunks() {
        return clamp(
                plugin.getConfigManager()
                        .getInt(
                                "claims.unclaim.max-batch-chunks",
                                50000
                        ),
                1,
                50000
        );
    }

    private static boolean isValidFaction(
            Faction faction
    ) {
        return faction != null
                && !faction.isSystemFaction();
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
                "unclaim.failed",
                detail
        );
    }

    private static OperationResult<UnclaimPlan> failurePlan(
            Status status,
            String detail
    ) {
        return OperationResult.failure(
                status,
                "unclaim.failed",
                detail
        );
    }

    private static final class CleanupSnapshot {

        private final StoredLocation home;
        private final Map<String, FactionWarp> warps;

        private CleanupSnapshot(
                StoredLocation home,
                Map<String, FactionWarp> warps
        ) {
            this.home = home;
            this.warps =
                    new HashMap<String, FactionWarp>(
                            warps
                    );
        }
    }
}
