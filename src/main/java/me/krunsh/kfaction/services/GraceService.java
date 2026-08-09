package me.krunsh.kfaction.services;

import java.util.EnumSet;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.api.event.GracePeriodEvent;
import me.krunsh.kfaction.audit.AuditCategory;
import me.krunsh.kfaction.audit.AuditOutcome;
import me.krunsh.kfaction.api.event.GracePeriodEvent.Action;
import me.krunsh.kfaction.api.event.GracePeriodEvent.Phase;
import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.core.operation.OperationResult;
import me.krunsh.kfaction.core.operation.OperationResult.Status;
import me.krunsh.kfaction.data.Relation;
import me.krunsh.kfaction.grace.GraceState;
import me.krunsh.kfaction.permissions.TerritoryAction;

/**
 * Grace Period V2.
 *
 * Etat global, persistant et indépendant des factions.
 */
public final class GraceService {

    private static final int PAYLOAD_SCHEMA = 1;

    private final Kfaction plugin;

    private volatile GraceState state;
    private volatile boolean initialized;

    private volatile boolean protectRaids;
    private volatile boolean protectTerritoryActions;
    private volatile boolean blockEnemyPvp;
    private volatile boolean protectExplosionBlockDamage;
    private volatile boolean broadcastChanges;
    private volatile long maxDurationMillis;

    private final EnumSet<TerritoryAction> protectedActions;

    public GraceService(Kfaction plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException(
                    "plugin cannot be null"
            );
        }

        this.plugin = plugin;
        this.state = GraceState.inactive();
        this.initialized = false;
        this.protectedActions =
                EnumSet.noneOf(
                        TerritoryAction.class
                );

        reload();
    }

    // ============================================================
    // Lifecycle
    // ============================================================

    public void initialize() {
        if (initialized) {
            return;
        }

        reload();

        String payload =
                plugin.getStorageManager() != null
                        ? plugin.getStorageManager()
                                .loadGraceStatePayload()
                        : null;

        if (payload != null
                && !payload.trim().isEmpty()) {
            restorePayload(payload);
        }

        initialized = true;

        expireIfNeeded();

        plugin.getLogger().info(
                "GraceService V2 initialisé: "
                        + (isActive()
                                ? "ACTIVE"
                                : "inactive")
        );
    }

    public void reload() {
        protectRaids =
                plugin.getConfigManager()
                        .getBoolean(
                                "grace.protections.raids",
                                true
                        );

        protectTerritoryActions =
                plugin.getConfigManager()
                        .getBoolean(
                                "grace.protections.territory-actions",
                                true
                        );

        blockEnemyPvp =
                plugin.getConfigManager()
                        .getBoolean(
                                "grace.protections.enemy-pvp",
                                false
                        );

        protectExplosionBlockDamage =
                plugin.getConfigManager()
                        .getBoolean(
                                "grace.protections.explosion-block-damage",
                                true
                        );

        broadcastChanges =
                plugin.getConfigManager()
                        .getBoolean(
                                "grace.broadcast-changes",
                                true
                        );

        long maxSeconds =
                Math.max(
                        60L,
                        plugin.getConfigManager()
                                .getInt(
                                        "grace.max-duration-seconds",
                                        2592000
                                )
                );

        maxDurationMillis =
                maxSeconds * 1000L;

        protectedActions.clear();

        if (!plugin.getConfigManager()
                .contains(
                        "grace.protected-actions"
                )) {
            addDefaultProtectedActions();
            return;
        }

        List<String> configured =
                plugin.getConfigManager()
                        .getStringList(
                                "grace.protected-actions"
                        );

        if (configured == null) {
            return;
        }

        for (String value : configured) {
            TerritoryAction action =
                    TerritoryAction.fromConfigKey(
                            value
                    );

            if (action == null) {
                plugin.getLogger().warning(
                        "TerritoryAction grace inconnue: "
                                + value
                );
                continue;
            }

            protectedActions.add(action);
        }
    }

    public void shutdown() {
        // Aucun task permanent: expiration évaluée à la demande.
    }

    // ============================================================
    // Etat / policy
    // ============================================================

    public GraceState getStateSnapshot() {
        expireIfNeeded();
        return state;
    }

    public boolean isActive() {
        GraceState current = state;
        long now = System.currentTimeMillis();

        if (current.isExpiredAt(now)) {
            expireIfNeeded();
            current = state;
        }

        return current.isActiveAt(now);
    }

    public long getRemainingMillis() {
        GraceState current =
                getStateSnapshot();

        return current.getRemainingMillis(
                System.currentTimeMillis()
        );
    }

    public boolean blocksRaids() {
        return protectRaids
                && isActive();
    }

    public boolean blocksEnemyPvp() {
        return blockEnemyPvp
                && isActive();
    }

    public boolean blocksExplosionBlockDamage() {
        return protectExplosionBlockDamage
                && isActive();
    }

    public boolean blocksTerritoryAction(
            Relation relation,
            TerritoryAction action
    ) {
        if (!protectTerritoryActions
                || relation != Relation.ENEMY
                || action == null
                || !isActive()) {
            return false;
        }

        return protectedActions.contains(action);
    }

    public long getMaxDurationMillis() {
        return maxDurationMillis;
    }

    // ============================================================
    // Mutations
    // ============================================================

    public OperationResult<GraceState> start(
            long durationMillis,
            String reason,
            OperationContext context
    ) {
        if (!requirePrimaryThread()) {
            return failure(
                    Status.UNAVAILABLE,
                    "La Grace Period doit être modifiée sur le thread principal"
            );
        }

        if (context == null) {
            return failure(
                    Status.INVALID_INPUT,
                    "OperationContext manquant"
            );
        }

        expireIfNeeded();

        if (state.isActiveAt(
                System.currentTimeMillis()
        )) {
            return failure(
                    Status.CONFLICT,
                    "Une Grace Period est déjà active"
            );
        }

        if (!isDurationValid(durationMillis)) {
            return failure(
                    Status.INVALID_INPUT,
                    "Durée invalide ou supérieure au maximum configuré"
            );
        }

        long now =
                System.currentTimeMillis();

        GraceState next =
                new GraceState(
                        true,
                        now,
                        safeAdd(
                                now,
                                durationMillis
                        ),
                        resolveActor(context),
                        normalizeReason(reason),
                        state.getRevision() + 1L
                );

        if (firePre(
                Action.START,
                state,
                next,
                context
        )) {
            return failure(
                    Status.CANCELLED,
                    "Activation annulée par un listener"
            );
        }

        GraceState previous = state;
        state = next;
        dirty();

        firePost(
                Action.START,
                previous,
                next,
                context
        );

        audit(
                context,
                "START endsAt="
                        + next.getEndsAt()
                        + " reason="
                        + safe(next.getReason())
        );

        broadcast(
                "§6[Kfaction] §eGrace Period activée pour §f"
                        + formatDuration(
                                durationMillis
                        )
                        + "§e."
        );

        return OperationResult.success(next);
    }

    public OperationResult<GraceState> stop(
            OperationContext context
    ) {
        if (!requirePrimaryThread()) {
            return failure(
                    Status.UNAVAILABLE,
                    "La Grace Period doit être modifiée sur le thread principal"
            );
        }

        if (context == null) {
            return failure(
                    Status.INVALID_INPUT,
                    "OperationContext manquant"
            );
        }

        expireIfNeeded();

        GraceState previous = state;

        if (!previous.isActiveAt(
                System.currentTimeMillis()
        )) {
            return OperationResult.noChange(
                    "grace.already-inactive"
            );
        }

        GraceState next =
                previous.stopped(
                        previous.getRevision() + 1L
                );

        if (firePre(
                Action.STOP,
                previous,
                next,
                context
        )) {
            return failure(
                    Status.CANCELLED,
                    "Arrêt annulé par un listener"
            );
        }

        state = next;
        dirty();

        firePost(
                Action.STOP,
                previous,
                next,
                context
        );

        audit(
                context,
                "STOP"
        );

        broadcast(
                "§6[Kfaction] §cGrace Period terminée manuellement."
        );

        return OperationResult.success(next);
    }

    public OperationResult<GraceState> extend(
            long additionalMillis,
            OperationContext context
    ) {
        if (!requirePrimaryThread()) {
            return failure(
                    Status.UNAVAILABLE,
                    "La Grace Period doit être modifiée sur le thread principal"
            );
        }

        if (context == null) {
            return failure(
                    Status.INVALID_INPUT,
                    "OperationContext manquant"
            );
        }

        expireIfNeeded();

        GraceState previous = state;
        long now = System.currentTimeMillis();

        if (!previous.isActiveAt(now)) {
            return failure(
                    Status.CONFLICT,
                    "Aucune Grace Period active"
            );
        }

        if (additionalMillis <= 0L) {
            return failure(
                    Status.INVALID_INPUT,
                    "Durée d'extension invalide"
            );
        }

        long newEnd =
                safeAdd(
                        previous.getEndsAt(),
                        additionalMillis
                );

        long remainingAfter =
                newEnd - now;

        if (remainingAfter <= 0L
                || remainingAfter > maxDurationMillis) {
            return failure(
                    Status.LIMIT_REACHED,
                    "L'extension dépasserait la durée maximale restante"
            );
        }

        GraceState next =
                new GraceState(
                        true,
                        previous.getStartedAt(),
                        newEnd,
                        previous.getStartedBy(),
                        previous.getReason(),
                        previous.getRevision() + 1L
                );

        if (firePre(
                Action.EXTEND,
                previous,
                next,
                context
        )) {
            return failure(
                    Status.CANCELLED,
                    "Extension annulée par un listener"
            );
        }

        state = next;
        dirty();

        firePost(
                Action.EXTEND,
                previous,
                next,
                context
        );

        audit(
                context,
                "EXTEND +"
                        + additionalMillis
                        + " endsAt="
                        + newEnd
        );

        broadcast(
                "§6[Kfaction] §eGrace Period prolongée de §f"
                        + formatDuration(
                                additionalMillis
                        )
                        + "§e."
        );

        return OperationResult.success(next);
    }

    // ============================================================
    // Persistence
    // ============================================================

    public String capturePayloadJson() {
        expireIfNeeded();

        GraceState current = state;

        JsonObject root =
                new JsonObject();

        root.addProperty(
                "schema",
                PAYLOAD_SCHEMA
        );

        root.addProperty(
                "active",
                current.isActive()
        );

        root.addProperty(
                "startedAt",
                current.getStartedAt()
        );

        root.addProperty(
                "endsAt",
                current.getEndsAt()
        );

        if (current.getStartedBy() != null) {
            root.addProperty(
                    "startedBy",
                    current.getStartedBy()
            );
        }

        if (current.getReason() != null) {
            root.addProperty(
                    "reason",
                    current.getReason()
            );
        }

        root.addProperty(
                "revision",
                current.getRevision()
        );

        return root.toString();
    }

    private void restorePayload(
            String payload
    ) {
        try {
            JsonElement parsed =
                    new JsonParser().parse(payload);

            if (parsed == null
                    || !parsed.isJsonObject()) {
                throw new IllegalArgumentException(
                        "payload non objet"
                );
            }

            JsonObject root =
                    parsed.getAsJsonObject();

            boolean active =
                    getBoolean(
                            root,
                            "active",
                            false
                    );

            long startedAt =
                    getLong(
                            root,
                            "startedAt",
                            0L
                    );

            long endsAt =
                    getLong(
                            root,
                            "endsAt",
                            0L
                    );

            String startedBy =
                    getString(
                            root,
                            "startedBy"
                    );

            String reason =
                    getString(
                            root,
                            "reason"
                    );

            long revision =
                    getLong(
                            root,
                            "revision",
                            0L
                    );

            if (active
                    && (startedAt <= 0L
                    || endsAt <= startedAt)) {
                throw new IllegalArgumentException(
                        "timestamps invalides"
                );
            }

            state =
                    new GraceState(
                            active,
                            startedAt,
                            endsAt,
                            startedBy,
                            reason,
                            revision
                    );

        } catch (RuntimeException exception) {
            plugin.getLogger().severe(
                    "Grace payload invalide, état inactif utilisé: "
                            + exception.getMessage()
            );

            state = GraceState.inactive();
        }
    }

    // ============================================================
    // Expiration
    // ============================================================

    private void expireIfNeeded() {
        GraceState current = state;
        long now = System.currentTimeMillis();

        if (!current.isExpiredAt(now)) {
            return;
        }

        if (!Bukkit.isPrimaryThread()) {
            /*
             * Un check async peut constater l'expiration mais ne mute jamais
             * le domaine. isActiveAt(now) retournera quand même false.
             */
            return;
        }

        GraceState next =
                current.stopped(
                        current.getRevision() + 1L
                );

        state = next;
        dirty();

        OperationContext context =
                OperationContext.system();

        firePost(
                Action.EXPIRE,
                current,
                next,
                context
        );

        audit(
                context,
                "EXPIRE"
        );

        broadcast(
                "§6[Kfaction] §cLa Grace Period est terminée."
        );
    }

    // ============================================================
    // Events
    // ============================================================

    private boolean firePre(
            Action action,
            GraceState previous,
            GraceState next,
            OperationContext context
    ) {
        GracePeriodEvent event =
                new GracePeriodEvent(
                        Phase.PRE,
                        action,
                        previous,
                        next,
                        context
                );

        Bukkit.getPluginManager()
                .callEvent(event);

        return event.isCancelled();
    }

    private void firePost(
            Action action,
            GraceState previous,
            GraceState next,
            OperationContext context
    ) {
        Bukkit.getPluginManager()
                .callEvent(
                        new GracePeriodEvent(
                                Phase.POST,
                                action,
                                previous,
                                next,
                                context
                        )
                );
    }

    // ============================================================
    // Helpers
    // ============================================================

    private boolean requirePrimaryThread() {
        return Bukkit.isPrimaryThread();
    }

    private boolean isDurationValid(
            long durationMillis
    ) {
        return durationMillis >= 1000L
                && durationMillis <= maxDurationMillis;
    }

    private void dirty() {
        if (plugin.getStorageManager() != null) {
            plugin.getStorageManager()
                    .markGraceStateDirty();
        }
    }

    private void audit(
            OperationContext context,
            String detail
    ) {
        String action =
                firstToken(detail);

        if (plugin.getLogManager() != null) {
            plugin.getLogManager()
                    .audit(
                            context,
                            AuditCategory.GRACE,
                            "GRACE_" + action,
                            AuditOutcome.SUCCESS,
                            null,
                            null,
                            null,
                            detail
                    );
        }

        plugin.getLogger().info(
                "[Grace] actor="
                        + resolveActor(context)
                        + " source="
                        + context.getSource()
                        + " correlation="
                        + context.getCorrelationId()
                        + " "
                        + detail
        );
    }

    private static String firstToken(
            String detail
    ) {
        if (detail == null
                || detail.trim().isEmpty()) {
            return "CHANGE";
        }

        String trimmed =
                detail.trim();

        int space =
                trimmed.indexOf(' ');

        return (space >= 0
                ? trimmed.substring(0, space)
                : trimmed)
                .toUpperCase(
                        java.util.Locale.ROOT
                );
    }

    private void broadcast(
            String message
    ) {
        if (!broadcastChanges) {
            return;
        }

        for (Player player
                : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
        }
    }

    private void addDefaultProtectedActions() {
        protectedActions.add(
                TerritoryAction.BLOCK_PLACE
        );
        protectedActions.add(
                TerritoryAction.BLOCK_BREAK
        );
        protectedActions.add(
                TerritoryAction.SWITCH
        );
        protectedActions.add(
                TerritoryAction.REDSTONE
        );
        protectedActions.add(
                TerritoryAction.CONTAINER_OPEN
        );
        protectedActions.add(
                TerritoryAction.CONTAINER_DEPOSIT
        );
        protectedActions.add(
                TerritoryAction.CONTAINER_WITHDRAW
        );
        protectedActions.add(
                TerritoryAction.HOPPER
        );
        protectedActions.add(
                TerritoryAction.FURNACE
        );
        protectedActions.add(
                TerritoryAction.BREWING
        );
        protectedActions.add(
                TerritoryAction.ITEM_FRAME
        );
        protectedActions.add(
                TerritoryAction.ARMOR_STAND
        );
        protectedActions.add(
                TerritoryAction.SPAWNER_PLACE
        );
        protectedActions.add(
                TerritoryAction.SPAWNER_BREAK
        );
        protectedActions.add(
                TerritoryAction.SPAWNER_INTERACT
        );
        protectedActions.add(
                TerritoryAction.TNT_PLACE
        );
        protectedActions.add(
                TerritoryAction.TNT_IGNITE
        );
        protectedActions.add(
                TerritoryAction.FLINT_AND_STEEL
        );
        protectedActions.add(
                TerritoryAction.BUCKET_EMPTY
        );
        protectedActions.add(
                TerritoryAction.BUCKET_FILL
        );
    }

    private static long safeAdd(
            long left,
            long right
    ) {
        if (right > 0L
                && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }

        return left + right;
    }

    private static String resolveActor(
            OperationContext context
    ) {
        if (context == null) {
            return "SYSTEM";
        }

        if (context.hasActorName()) {
            return context.getActorName();
        }

        if (context.hasActor()) {
            return context.getActorId()
                    .toString();
        }

        return "SYSTEM";
    }

    private static String normalizeReason(
            String reason
    ) {
        if (reason == null) {
            return null;
        }

        String trimmed =
                reason.trim();

        if (trimmed.isEmpty()) {
            return null;
        }

        return trimmed.length() > 160
                ? trimmed.substring(0, 160)
                : trimmed;
    }

    private static String safe(String value) {
        return value != null
                ? value
                : "-";
    }

    public static String formatDuration(
            long millis
    ) {
        long seconds =
                Math.max(
                        0L,
                        millis / 1000L
                );

        long days =
                seconds / 86400L;
        seconds %= 86400L;

        long hours =
                seconds / 3600L;
        seconds %= 3600L;

        long minutes =
                seconds / 60L;
        seconds %= 60L;

        StringBuilder builder =
                new StringBuilder();

        if (days > 0L) {
            builder.append(days)
                    .append("j ");
        }

        if (hours > 0L) {
            builder.append(hours)
                    .append("h ");
        }

        if (minutes > 0L) {
            builder.append(minutes)
                    .append("m ");
        }

        if (seconds > 0L
                || builder.length() == 0) {
            builder.append(seconds)
                    .append("s");
        }

        return builder.toString()
                .trim();
    }

    private static boolean getBoolean(
            JsonObject root,
            String key,
            boolean fallback
    ) {
        return root.has(key)
                && !root.get(key).isJsonNull()
                ? root.get(key).getAsBoolean()
                : fallback;
    }

    private static long getLong(
            JsonObject root,
            String key,
            long fallback
    ) {
        return root.has(key)
                && !root.get(key).isJsonNull()
                ? root.get(key).getAsLong()
                : fallback;
    }

    private static String getString(
            JsonObject root,
            String key
    ) {
        return root.has(key)
                && !root.get(key).isJsonNull()
                ? root.get(key).getAsString()
                : null;
    }

    private static <T> OperationResult<T> failure(
            Status status,
            String detail
    ) {
        return OperationResult.failure(
                status,
                "grace.failed",
                detail
        );
    }
}
