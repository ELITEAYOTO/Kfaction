package me.krunsh.kfaction.services;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scheduler.BukkitRunnable;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.core.operation.OperationResult;
import me.krunsh.kfaction.core.operation.OperationResult.Status;
import me.krunsh.kfaction.data.FLocation;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionLog.LogType;
import me.krunsh.kfaction.data.FactionWarp;
import me.krunsh.kfaction.data.StoredLocation;
import me.krunsh.kfaction.permissions.FactionCapability;
import me.krunsh.kfaction.policy.HomeClaimPolicy;
import me.krunsh.kfaction.security.WarpPasswordHasher;

/**
 * Service central Home & Warps V2.
 *
 * - mutations validées et auditées ;
 * - mots de passe PBKDF2 uniquement ;
 * - prompt chat intercepté pour ne pas passer le secret dans la commande ;
 * - un seul warmup de téléportation par joueur ;
 * - revalidation complète au moment réel du TP.
 */
public final class HomeWarpService implements Listener {

    private enum PromptMode {
        AUTH_WARP,
        SET_WARP_PASSWORD
    }

    private enum DestinationKind {
        HOME,
        WARP
    }

    private final Kfaction plugin;

    private final Map<UUID, BukkitRunnable> pendingTeleports;
    private final Map<UUID, PasswordPrompt> pendingPasswordPrompts;

    private volatile boolean listenerRegistered;

    public HomeWarpService(Kfaction plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException(
                    "plugin cannot be null"
            );
        }

        this.plugin = plugin;

        this.pendingTeleports =
                new ConcurrentHashMap<UUID, BukkitRunnable>();

        this.pendingPasswordPrompts =
                new ConcurrentHashMap<UUID, PasswordPrompt>();

        this.listenerRegistered = false;
    }

    // ============================================================
    // Lifecycle
    // ============================================================

    public void initialize() {
        if (!listenerRegistered) {
            Bukkit.getPluginManager()
                    .registerEvents(
                            this,
                            plugin
                    );

            listenerRegistered = true;
        }

        plugin.getLogger().info(
                "HomeWarpService V2 initialisé "
                        + "(StoredLocation + PBKDF2 + warmup)"
        );
    }

    public void shutdown() {
        for (BukkitRunnable task
                : pendingTeleports.values()) {
            if (task != null) {
                task.cancel();
            }
        }

        pendingTeleports.clear();
        pendingPasswordPrompts.clear();

        if (listenerRegistered) {
            HandlerList.unregisterAll(this);
            listenerRegistered = false;
        }
    }

    // ============================================================
    // Home mutation
    // ============================================================

    public OperationResult<StoredLocation> setHome(
            Player actor,
            Faction faction,
            OperationContext context
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return failure(
                    Status.UNAVAILABLE,
                    "setHome doit être exécuté sur le thread principal"
            );
        }

        OperationResult<Void> actorCheck =
                validateActorFaction(
                        actor,
                        faction,
                        FactionCapability.SET_HOME
                );

        if (!actorCheck.isSuccess()) {
            return copyFailure(actorCheck);
        }

        Location location =
                actor.getLocation();

        StoredLocation stored =
                StoredLocation.fromBukkit(
                        location
                );

        if (stored == null) {
            return failure(
                    Status.UNAVAILABLE,
                    "Le monde courant est indisponible"
            );
        }

        Faction territoryOwner =
                plugin.getClaimManager()
                        .getFactionAt(
                                new FLocation(location)
                        );

        String territoryOwnerId =
                territoryOwner != null
                        ? territoryOwner.getId()
                        : null;

        boolean explicitStaffBypass =
                actor.hasPermission(
                        "kfaction.admin.bypass"
                )
                        && plugin.isBypassing(
                                actor.getUniqueId()
                        );

        if (!HomeClaimPolicy.canSetHome(
                faction.getId(),
                territoryOwnerId,
                explicitStaffBypass
        )) {
            return failure(
                    Status.FORBIDDEN,
                    "Le home doit être dans un territoire de la faction"
            );
        }

        faction.restoreHome(stored);

        plugin.getStorageManager()
                .markDirty(faction);

        plugin.getLogManager()
                .log(
                        faction.getId(),
                        LogType.TERRITORY_SETHOME,
                        actor,
                        null,
                        formatStored(stored)
                                + (explicitStaffBypass
                                        ? " BYPASS territoire="
                                                + territoryOwnerId
                                        : "")
                );

        if (explicitStaffBypass) {
            plugin.getLogger().warning(
                    "[AUDIT] "
                            + actor.getName()
                            + " a utilisé le bypass pour setHome faction="
                            + faction.getId()
                            + " territoire="
                            + territoryOwnerId
            );
        }

        audit(
                context,
                faction,
                "SET_HOME "
                        + formatStored(stored)
        );

        return OperationResult.success(stored);
    }

    // ============================================================
    // Warp mutation
    // ============================================================

    public OperationResult<FactionWarp> setWarp(
            Player actor,
            Faction faction,
            String rawName,
            String optionalPlainPassword,
            OperationContext context
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return failure(
                    Status.UNAVAILABLE,
                    "setWarp doit être exécuté sur le thread principal"
            );
        }

        OperationResult<Void> actorCheck =
                validateActorFaction(
                        actor,
                        faction,
                        FactionCapability.SET_WARP
                );

        if (!actorCheck.isSuccess()) {
            return copyFailure(actorCheck);
        }

        String name =
                normalizeWarpName(rawName);

        OperationResult<Void> nameCheck =
                validateWarpName(name);

        if (!nameCheck.isSuccess()) {
            return copyFailure(nameCheck);
        }

        FactionWarp existing =
                faction.getWarpData(name);

        int maxWarps =
                getMaxWarps(faction);

        if (existing == null
                && faction.getWarpCount()
                        >= maxWarps) {
            return failure(
                    Status.LIMIT_REACHED,
                    "Limite de warps atteinte: "
                            + maxWarps
            );
        }

        Location current =
                actor.getLocation();

        StoredLocation stored =
                StoredLocation.fromBukkit(
                        current
                );

        if (stored == null) {
            return failure(
                    Status.UNAVAILABLE,
                    "Le monde courant est indisponible"
            );
        }

        Faction owner =
                plugin.getClaimManager()
                        .getFactionAt(
                                new FLocation(current)
                        );

        if (owner == null
                || !faction.getId()
                        .equals(
                                owner.getId()
                        )) {
            return failure(
                    Status.FORBIDDEN,
                    "Le warp doit être dans un territoire de la faction"
            );
        }

        String passwordHash =
                existing != null
                        ? existing.getPasswordHash()
                        : null;

        if (optionalPlainPassword != null) {
            OperationResult<Void> passwordCheck =
                    validatePassword(
                            optionalPlainPassword
                    );

            if (!passwordCheck.isSuccess()) {
                return copyFailure(passwordCheck);
            }

            passwordHash =
                    hashPassword(
                            optionalPlainPassword
                    );
        }

        long now =
                System.currentTimeMillis();

        FactionWarp warp =
                new FactionWarp(
                        name,
                        stored,
                        passwordHash,
                        existing != null
                                ? existing.getCreatedAt()
                                : now,
                        existing != null
                                && existing.getCreatedBy() != null
                                ? existing.getCreatedBy()
                                : actor.getName(),
                        now
                );

        faction.putWarp(warp);

        plugin.getStorageManager()
                .markDirty(faction);

        plugin.getLogManager()
                .log(
                        faction.getId(),
                        LogType.TERRITORY_SETWARP,
                        actor,
                        null,
                        name
                                + " "
                                + formatStored(stored)
                                + " protected="
                                + warp.isPasswordProtected()
                );

        audit(
                context,
                faction,
                "SET_WARP name="
                        + name
                        + " protected="
                        + warp.isPasswordProtected()
        );

        return OperationResult.success(warp);
    }

    public OperationResult<FactionWarp> setWarpPassword(
            Player actor,
            Faction faction,
            String rawName,
            String plainPassword,
            OperationContext context
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return failure(
                    Status.UNAVAILABLE,
                    "setWarpPassword doit être exécuté sur le thread principal"
            );
        }

        OperationResult<Void> actorCheck =
                validateActorFaction(
                        actor,
                        faction,
                        FactionCapability.SET_WARP
                );

        if (!actorCheck.isSuccess()) {
            return copyFailure(actorCheck);
        }

        String name =
                normalizeWarpName(rawName);

        FactionWarp existing =
                faction.getWarpData(name);

        if (existing == null) {
            return failure(
                    Status.NOT_FOUND,
                    "Warp introuvable: "
                            + rawName
            );
        }

        String passwordHash = null;

        if (plainPassword != null) {
            OperationResult<Void> passwordCheck =
                    validatePassword(
                            plainPassword
                    );

            if (!passwordCheck.isSuccess()) {
                return copyFailure(passwordCheck);
            }

            passwordHash =
                    hashPassword(
                            plainPassword
                    );
        }

        FactionWarp updated =
                existing.withPasswordHash(
                        passwordHash
                );

        faction.putWarp(updated);

        plugin.getStorageManager()
                .markDirty(faction);

        plugin.getLogManager()
                .log(
                        faction.getId(),
                        LogType.TERRITORY_SETWARP,
                        actor,
                        null,
                        name
                                + " password="
                                + (passwordHash != null
                                        ? "ENABLED"
                                        : "DISABLED")
                );

        audit(
                context,
                faction,
                "WARP_PASSWORD name="
                        + name
                        + " enabled="
                        + (passwordHash != null)
        );

        return OperationResult.success(updated);
    }

    public OperationResult<FactionWarp> deleteWarp(
            Player actor,
            Faction faction,
            String rawName,
            OperationContext context
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return failure(
                    Status.UNAVAILABLE,
                    "deleteWarp doit être exécuté sur le thread principal"
            );
        }

        OperationResult<Void> actorCheck =
                validateActorFaction(
                        actor,
                        faction,
                        FactionCapability.DELETE_WARP
                );

        if (!actorCheck.isSuccess()) {
            return copyFailure(actorCheck);
        }

        String name =
                normalizeWarpName(rawName);

        FactionWarp existing =
                faction.getWarpData(name);

        if (existing == null) {
            return failure(
                    Status.NOT_FOUND,
                    "Warp introuvable: "
                            + rawName
            );
        }

        if (!faction.removeWarp(name)) {
            return failure(
                    Status.CONFLICT,
                    "Le warp a changé pendant la suppression"
            );
        }

        plugin.getStorageManager()
                .markDirty(faction);

        plugin.getLogManager()
                .log(
                        faction.getId(),
                        LogType.TERRITORY_DELWARP,
                        actor,
                        null,
                        name
                );

        audit(
                context,
                faction,
                "DELETE_WARP name="
                        + name
        );

        return OperationResult.success(existing);
    }

    // ============================================================
    // Password prompts
    // ============================================================

    public OperationResult<String> requestWarpTeleportInteractive(
            Player actor,
            Faction faction,
            String rawName,
            OperationContext context
    ) {
        FactionWarp warp =
                faction != null
                        ? faction.getWarpData(
                                normalizeWarpName(
                                        rawName
                                )
                        )
                        : null;

        if (warp == null) {
            return failure(
                    Status.NOT_FOUND,
                    "Warp introuvable: "
                            + rawName
            );
        }

        if (!warp.isPasswordProtected()) {
            return requestWarpTeleport(
                    actor,
                    faction,
                    rawName,
                    null,
                    context
            );
        }

        OperationResult<Void> actorCheck =
                validateActorFaction(
                        actor,
                        faction,
                        FactionCapability.USE_WARP
                );

        if (!actorCheck.isSuccess()) {
            return copyFailure(actorCheck);
        }

        beginPasswordPrompt(
                actor,
                faction,
                warp.getName(),
                PromptMode.AUTH_WARP,
                context
        );

        return OperationResult.success(
                "PASSWORD_PROMPT"
        );
    }

    public OperationResult<String> requestWarpPasswordSetup(
            Player actor,
            Faction faction,
            String rawName,
            OperationContext context
    ) {
        OperationResult<Void> actorCheck =
                validateActorFaction(
                        actor,
                        faction,
                        FactionCapability.SET_WARP
                );

        if (!actorCheck.isSuccess()) {
            return copyFailure(actorCheck);
        }

        String name =
                normalizeWarpName(rawName);

        if (faction.getWarpData(name) == null) {
            return failure(
                    Status.NOT_FOUND,
                    "Warp introuvable: "
                            + rawName
            );
        }

        beginPasswordPrompt(
                actor,
                faction,
                name,
                PromptMode.SET_WARP_PASSWORD,
                context
        );

        return OperationResult.success(
                "PASSWORD_PROMPT"
        );
    }

    private void beginPasswordPrompt(
            Player actor,
            Faction faction,
            String warpName,
            PromptMode mode,
            OperationContext context
    ) {
        final UUID playerId =
                actor.getUniqueId();

        final PasswordPrompt prompt =
                new PasswordPrompt(
                        faction.getId(),
                        warpName,
                        mode,
                        context,
                        System.currentTimeMillis()
                );

        pendingPasswordPrompts.put(
                playerId,
                prompt
        );

        plugin.getMessageManager()
                .send(
                        actor,
                        mode == PromptMode.AUTH_WARP
                                ? "warp.password.prompt-auth"
                                : "warp.password.prompt-set",
                        "{name}",
                        warpName
                );

        plugin.getMessageManager()
                .send(
                        actor,
                        "warp.password.prompt-hint"
                );

        long timeoutTicks =
                Math.max(
                        5,
                        plugin.getConfigManager()
                                .getInt(
                                        "warps.password-prompt-timeout-seconds",
                                        30
                                )
                ) * 20L;

        plugin.getServer()
                .getScheduler()
                .runTaskLater(
                        plugin,
                        new Runnable() {
                            @Override
                            public void run() {
                                PasswordPrompt current =
                                        pendingPasswordPrompts.get(
                                                playerId
                                        );

                                if (current == prompt
                                        && pendingPasswordPrompts.remove(
                                                playerId,
                                                prompt
                                        )) {
                                    Player player =
                                            Bukkit.getPlayer(
                                                    playerId
                                            );

                                    if (player != null
                                            && player.isOnline()) {
                                        plugin.getMessageManager()
                                                .send(
                                                        player,
                                                        "warp.password.prompt-expired"
                                                );
                                    }
                                }
                            }
                        },
                        timeoutTicks
                );
    }

    @EventHandler(
            priority = EventPriority.LOWEST,
            ignoreCancelled = false
    )
    public void onPasswordChat(
            AsyncPlayerChatEvent event
    ) {
        final Player player =
                event.getPlayer();

        final PasswordPrompt prompt =
                pendingPasswordPrompts.remove(
                        player.getUniqueId()
                );

        if (prompt == null) {
            return;
        }

        /*
         * Le chat est annulé le plus tôt possible pour ne jamais broadcaster
         * le secret par le chemin Bukkit normal.
         */
        event.setCancelled(true);

        final String input =
                event.getMessage();

        Bukkit.getScheduler()
                .runTask(
                        plugin,
                        new Runnable() {
                            @Override
                            public void run() {
                                handlePasswordInput(
                                        player,
                                        prompt,
                                        input
                                );
                            }
                        }
                );
    }

    private void handlePasswordInput(
            Player player,
            PasswordPrompt prompt,
            String input
    ) {
        if (player == null
                || !player.isOnline()) {
            return;
        }

        if ("cancel".equalsIgnoreCase(
                input != null
                        ? input.trim()
                        : ""
        )) {
            plugin.getMessageManager()
                    .send(
                            player,
                            "warp.password.prompt-cancelled"
                    );
            return;
        }

        Faction faction =
                plugin.getFactionManager()
                        .getFaction(
                                prompt.factionId
                        );

        if (faction == null
                || !faction.isMember(
                        player.getUniqueId()
                )) {
            plugin.getMessageManager()
                    .send(
                            player,
                            "warp.password.faction-changed"
                    );
            return;
        }

        if (prompt.mode
                == PromptMode.AUTH_WARP) {
            OperationResult<String> result =
                    requestWarpTeleport(
                            player,
                            faction,
                            prompt.warpName,
                            input,
                            prompt.context
                    );

            sendFailureIfNeeded(
                    player,
                    result
            );

            return;
        }

        OperationResult<FactionWarp> result =
                setWarpPassword(
                        player,
                        faction,
                        prompt.warpName,
                        input,
                        prompt.context
                );

        if (!result.isSuccess()) {
            sendFailureIfNeeded(
                    player,
                    result
            );
            return;
        }

        plugin.getMessageManager()
                .send(
                        player,
                        "warp.password.enabled",
                        "{name}",
                        prompt.warpName
                );
    }

    // ============================================================
    // Teleport requests
    // ============================================================

    public OperationResult<String> requestHomeTeleport(
            Player actor,
            Faction faction,
            OperationContext context
    ) {
        OperationResult<Void> actorCheck =
                validateActorFaction(
                        actor,
                        faction,
                        FactionCapability.USE_HOME
                );

        if (!actorCheck.isSuccess()) {
            return copyFailure(actorCheck);
        }

        StoredLocation home =
                faction.getStoredHome();

        if (home == null) {
            return failure(
                    Status.NOT_FOUND,
                    "Le home de faction n'est pas défini"
            );
        }

        if (!home.isWorldAvailable()) {
            return failure(
                    Status.UNAVAILABLE,
                    "Le monde du home n'est pas chargé: "
                            + home.getWorldName()
            );
        }

        return beginTeleport(
                actor,
                faction,
                DestinationKind.HOME,
                null,
                null,
                home,
                null,
                context
        );
    }

    public OperationResult<String> requestWarpTeleport(
            Player actor,
            Faction faction,
            String rawName,
            String suppliedPassword,
            OperationContext context
    ) {
        OperationResult<Void> actorCheck =
                validateActorFaction(
                        actor,
                        faction,
                        FactionCapability.USE_WARP
                );

        if (!actorCheck.isSuccess()) {
            return copyFailure(actorCheck);
        }

        String name =
                normalizeWarpName(rawName);

        FactionWarp warp =
                faction.getWarpData(name);

        if (warp == null) {
            return failure(
                    Status.NOT_FOUND,
                    "Warp introuvable: "
                            + rawName
            );
        }

        if (warp.isPasswordProtected()) {
            if (suppliedPassword == null) {
                return failure(
                        Status.FORBIDDEN,
                        "Ce warp nécessite un mot de passe"
                );
            }

            if (!WarpPasswordHasher.verify(
                    suppliedPassword,
                    warp.getPasswordHash()
            )) {
                return failure(
                        Status.FORBIDDEN,
                        "Mot de passe incorrect"
                );
            }
        }

        StoredLocation stored =
                warp.getStoredLocation();

        if (!stored.isWorldAvailable()) {
            return failure(
                    Status.UNAVAILABLE,
                    "Le monde du warp n'est pas chargé: "
                            + stored.getWorldName()
            );
        }

        return beginTeleport(
                actor,
                faction,
                DestinationKind.WARP,
                name,
                suppliedPassword,
                stored,
                warp,
                context
        );
    }

    private OperationResult<String> beginTeleport(
            final Player actor,
            final Faction faction,
            final DestinationKind kind,
            final String warpName,
            final String suppliedPassword,
            final StoredLocation expectedLocation,
            final FactionWarp expectedWarp,
            final OperationContext context
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return failure(
                    Status.UNAVAILABLE,
                    "La téléportation doit être demandée sur le thread principal"
            );
        }

        cancelPendingTeleport(
                actor.getUniqueId()
        );

        final int delaySeconds =
                Math.max(
                        0,
                        plugin.getConfigManager()
                                .getInt(
                                        kind == DestinationKind.HOME
                                                ? "home.delay"
                                                : "warps.teleport-delay",
                                        3
                                )
                );

        if (delaySeconds == 0) {
            return finishTeleport(
                    actor,
                    faction,
                    kind,
                    warpName,
                    suppliedPassword,
                    expectedLocation,
                    expectedWarp,
                    context
            );
        }

        if (kind == DestinationKind.HOME) {
            plugin.getMessageManager()
                    .send(
                            actor,
                            "home.teleporting",
                            "{seconds}",
                            String.valueOf(
                                    delaySeconds
                            )
                    );
        } else {
            plugin.getMessageManager()
                    .send(
                            actor,
                            "warp.teleporting",
                            "{name}",
                            warpName,
                            "{seconds}",
                            String.valueOf(
                                    delaySeconds
                            )
                    );
        }

        final UUID playerId =
                actor.getUniqueId();

        final Location startLocation =
                actor.getLocation()
                        .clone();

        BukkitRunnable task =
                new BukkitRunnable() {

                    private int countdown =
                            delaySeconds;

                    @Override
                    public void run() {
                        if (!actor.isOnline()) {
                            pendingTeleports.remove(
                                    playerId
                            );
                            cancel();
                            return;
                        }

                        if (hasMoved(
                                startLocation,
                                actor.getLocation()
                        )) {
                            if (kind
                                    == DestinationKind.HOME) {
                                plugin.getMessageManager()
                                        .send(
                                                actor,
                                                "home.cancelled-move"
                                        );
                            } else {
                                plugin.getMessageManager()
                                        .send(
                                                actor,
                                                "warp.cancelled-move"
                                        );
                            }

                            pendingTeleports.remove(
                                    playerId
                            );
                            cancel();
                            return;
                        }

                        countdown--;

                        if (countdown > 0) {
                            return;
                        }

                        OperationResult<String> result =
                                finishTeleport(
                                        actor,
                                        faction,
                                        kind,
                                        warpName,
                                        suppliedPassword,
                                        expectedLocation,
                                        expectedWarp,
                                        context
                                );

                        pendingTeleports.remove(
                                playerId
                        );

                        if (!result.isSuccess()) {
                            sendFailureIfNeeded(
                                    actor,
                                    result
                            );
                        }

                        cancel();
                    }
                };

        pendingTeleports.put(
                playerId,
                task
        );

        task.runTaskTimer(
                plugin,
                20L,
                20L
        );

        return OperationResult.success(
                "SCHEDULED"
        );
    }

    private OperationResult<String> finishTeleport(
            Player actor,
            Faction faction,
            DestinationKind kind,
            String warpName,
            String suppliedPassword,
            StoredLocation expectedLocation,
            FactionWarp expectedWarp,
            OperationContext context
    ) {
        FactionCapability capability =
                kind == DestinationKind.HOME
                        ? FactionCapability.USE_HOME
                        : FactionCapability.USE_WARP;

        OperationResult<Void> actorCheck =
                validateActorFaction(
                        actor,
                        faction,
                        capability
                );

        if (!actorCheck.isSuccess()) {
            return copyFailure(actorCheck);
        }

        StoredLocation currentStored;
        FactionWarp currentWarp = null;

        if (kind == DestinationKind.HOME) {
            currentStored =
                    faction.getStoredHome();

            if (currentStored == null) {
                return failure(
                        Status.NOT_FOUND,
                        "Le home a été supprimé pendant le warmup"
                );
            }

            if (!currentStored.samePosition(
                    expectedLocation
            )) {
                return failure(
                        Status.CONFLICT,
                        "Le home a changé pendant le warmup"
                );
            }
        } else {
            currentWarp =
                    faction.getWarpData(
                            warpName
                    );

            if (currentWarp == null) {
                return failure(
                        Status.NOT_FOUND,
                        "Le warp a été supprimé pendant le warmup"
                );
            }

            if (expectedWarp == null
                    || !currentWarp
                            .sameSecurityAndDestination(
                                    expectedWarp
                            )) {
                return failure(
                        Status.CONFLICT,
                        "Le warp a changé pendant le warmup"
                );
            }

            if (currentWarp.isPasswordProtected()
                    && !WarpPasswordHasher.verify(
                            suppliedPassword,
                            currentWarp.getPasswordHash()
                    )) {
                return failure(
                        Status.FORBIDDEN,
                        "Le mot de passe du warp a changé"
                );
            }

            currentStored =
                    currentWarp.getStoredLocation();
        }

        Location destination =
                currentStored.toBukkitLocation();

        if (destination == null
                || destination.getWorld() == null) {
            return failure(
                    Status.UNAVAILABLE,
                    "Le monde de destination n'est pas chargé: "
                            + currentStored.getWorldName()
            );
        }

        if (!actor.teleport(destination)) {
            return failure(
                    Status.FAILED,
                    "Bukkit a refusé la téléportation"
            );
        }

        if (kind == DestinationKind.HOME) {
            plugin.getLogManager()
                    .log(
                            faction.getId(),
                            LogType.TP_HOME,
                            actor,
                            null,
                            formatStored(
                                    currentStored
                            )
                    );

            plugin.getMessageManager()
                    .send(
                            actor,
                            "home.teleported"
                    );

            audit(
                    context,
                    faction,
                    "TP_HOME"
            );

            return OperationResult.success(
                    "TELEPORTED"
            );
        }

        plugin.getLogManager()
                .log(
                        faction.getId(),
                        LogType.TP_WARP,
                        actor,
                        null,
                        warpName
                                + " "
                                + formatStored(
                                        currentStored
                                )
                );

        plugin.getMessageManager()
                .send(
                        actor,
                        "warp.teleported",
                        "{name}",
                        warpName
                );

        audit(
                context,
                faction,
                "TP_WARP name="
                        + warpName
        );

        return OperationResult.success(
                "TELEPORTED"
        );
    }

    public void cancelPendingTeleport(
            UUID playerId
    ) {
        if (playerId == null) {
            return;
        }

        BukkitRunnable previous =
                pendingTeleports.remove(
                        playerId
                );

        if (previous != null) {
            previous.cancel();
        }
    }

    // ============================================================
    // Validation/helpers
    // ============================================================

    private OperationResult<Void> validateActorFaction(
            Player actor,
            Faction faction,
            FactionCapability capability
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return failure(
                    Status.UNAVAILABLE,
                    "Home/Warp operation must run on Bukkit primary thread"
            );
        }

        if (actor == null
                || faction == null
                || faction.isSystemFaction()
                || capability == null) {
            return failure(
                    Status.INVALID_INPUT,
                    "Acteur/faction/capability invalide"
            );
        }

        FPlayer fPlayer =
                plugin.getFPlayerManager()
                        .findLoaded(
                                actor.getUniqueId()
                        );

        if (fPlayer == null
                || !fPlayer.hasFaction()
                || !faction.getId()
                        .equals(
                                fPlayer.getFactionId()
                        )
                || !faction.isMember(
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
                        capability
                )) {
            return failure(
                    Status.FORBIDDEN,
                    "Permission faction refusée: "
                            + capability.name()
            );
        }

        return OperationResult.success();
    }

    private OperationResult<Void> validateWarpName(
            String name
    ) {
        if (name == null) {
            return failure(
                    Status.INVALID_INPUT,
                    "Nom de warp vide"
            );
        }

        int maxLength =
                Math.max(
                        1,
                        Math.min(
                                32,
                                plugin.getConfigManager()
                                        .getInt(
                                                "warps.max-name-length",
                                                16
                                        )
                        )
                );

        if (name.length() > maxLength) {
            return failure(
                    Status.INVALID_INPUT,
                    "Nom de warp trop long: maximum "
                            + maxLength
            );
        }

        if (!name.matches(
                "^[a-z0-9_-]+$"
        )) {
            return failure(
                    Status.INVALID_INPUT,
                    "Nom invalide: a-z, 0-9, _ et -"
            );
        }

        return OperationResult.success();
    }

    private OperationResult<Void> validatePassword(
            String password
    ) {
        if (password == null) {
            return OperationResult.success();
        }

        int minLength =
                Math.max(
                        1,
                        plugin.getConfigManager()
                                .getInt(
                                        "warps.password.min-length",
                                        4
                                )
                );

        int maxLength =
                Math.max(
                        minLength,
                        Math.min(
                                128,
                                plugin.getConfigManager()
                                        .getInt(
                                                "warps.password.max-length",
                                                64
                                        )
                        )
                );

        int length =
                password.length();

        if (length < minLength
                || length > maxLength) {
            return failure(
                    Status.INVALID_INPUT,
                    "Le mot de passe doit faire entre "
                            + minLength
                            + " et "
                            + maxLength
                            + " caractères"
            );
        }

        return OperationResult.success();
    }

    private String hashPassword(
            String password
    ) {
        int iterations =
                plugin.getConfigManager()
                        .getInt(
                                "warps.password.pbkdf2-iterations",
                                60000
                        );

        return WarpPasswordHasher.hash(
                password,
                iterations
        );
    }

    private int getMaxWarps(
            Faction faction
    ) {
        int base =
                Math.max(
                        0,
                        plugin.getConfigManager()
                                .getInt(
                                        "warps.max-per-faction",
                                        1
                                )
                );

        return base
                + Math.max(
                        0,
                        faction.getExtraWarps()
                );
    }

    private boolean hasMoved(
            Location start,
            Location current
    ) {
        if (start == null
                || current == null
                || start.getWorld() == null
                || current.getWorld() == null
                || !start.getWorld()
                        .getName()
                        .equalsIgnoreCase(
                                current.getWorld()
                                        .getName()
                        )) {
            return true;
        }

        double allowed =
                Math.max(
                        0.0D,
                        plugin.getConfigManager()
                                .getDouble(
                                        "teleport.cancel-move-distance",
                                        0.5D
                                )
                );

        return start.distanceSquared(current)
                > allowed * allowed;
    }

    private void sendFailureIfNeeded(
            Player player,
            OperationResult<?> result
    ) {
        if (player == null
                || result == null
                || result.isSuccess()) {
            return;
        }

        player.sendMessage(
                "§c✖ "
                        + (result.hasDetail()
                                ? result.getDetail()
                                : "Opération refusée")
        );
    }

    private void audit(
            OperationContext context,
            Faction faction,
            String detail
    ) {
        plugin.getLogger().info(
                "[HomeWarp] faction="
                        + (faction != null
                                ? faction.getId()
                                : "-")
                        + " actor="
                        + (context != null
                                && context.hasActorName()
                                ? context.getActorName()
                                : "SYSTEM")
                        + " source="
                        + (context != null
                                ? context.getSource()
                                : "-")
                        + " "
                        + detail
        );
    }

    private static String normalizeWarpName(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String normalized =
                value.trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        return normalized.isEmpty()
                ? null
                : normalized;
    }

    private static String formatStored(
            StoredLocation location
    ) {
        if (location == null) {
            return "null";
        }

        return "["
                + location.getWorldName()
                + ", "
                + ((int) Math.floor(
                        location.getX()
                ))
                + ", "
                + ((int) Math.floor(
                        location.getY()
                ))
                + ", "
                + ((int) Math.floor(
                        location.getZ()
                ))
                + "]";
    }

    private static <T> OperationResult<T> failure(
            Status status,
            String detail
    ) {
        return OperationResult.failure(
                status,
                "home-warp.failed",
                detail
        );
    }

    private static <T> OperationResult<T> copyFailure(
            OperationResult<?> source
    ) {
        if (source == null) {
            return failure(
                    Status.FAILED,
                    "Résultat source null"
            );
        }

        return OperationResult.failure(
                source.getStatus(),
                source.hasMessageKey()
                        ? source.getMessageKey()
                        : "home-warp.failed",
                source.getDetail()
        );
    }

    private static final class PasswordPrompt {

        private final String factionId;
        private final String warpName;
        private final PromptMode mode;
        private final OperationContext context;
        private final long createdAt;

        private PasswordPrompt(
                String factionId,
                String warpName,
                PromptMode mode,
                OperationContext context,
                long createdAt
        ) {
            this.factionId = factionId;
            this.warpName = warpName;
            this.mode = mode;
            this.context = context;
            this.createdAt = createdAt;
        }
    }
}
