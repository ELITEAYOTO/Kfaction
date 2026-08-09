package me.krunsh.kfaction.managers;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.audit.AuditCategory;
import me.krunsh.kfaction.audit.AuditOutcome;
import me.krunsh.kfaction.audit.AuditService;
import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.data.FactionLog;
import me.krunsh.kfaction.data.FactionLog.LogType;

/**
 * Façade de logs V2.
 *
 * 1. Garde le cache/fichier JSON legacy pour /f logs et Kgui.
 * 2. Dual-write chaque FactionLog dans audit.db via AuditService.
 * 3. Expose l'API structurée d'audit pour les opérations globales.
 */
public class LogManager {

    private static final long DEFAULT_FLUSH_INTERVAL_TICKS =
            20L * 10L;

    private static final Set<LogType> CAT_MEMBRES =
            EnumSet.of(
                    LogType.MEMBER_JOIN,
                    LogType.MEMBER_LEAVE,
                    LogType.MEMBER_KICK,
                    LogType.MEMBER_PROMOTE,
                    LogType.MEMBER_DEMOTE
            );

    private static final Set<LogType> CAT_TERRITOIRE =
            EnumSet.of(
                    LogType.TERRITORY_CLAIM,
                    LogType.TERRITORY_UNCLAIM,
                    LogType.TERRITORY_SETHOME,
                    LogType.TERRITORY_SETWARP,
                    LogType.TERRITORY_DELWARP,
                    LogType.CLAIM_GROUP_CHANGE
            );

    private static final Set<LogType> CAT_ECONOMIE =
            EnumSet.of(
                    LogType.ECONOMY_DEPOSIT,
                    LogType.ECONOMY_WITHDRAW
            );

    private static final Set<LogType> CAT_TP =
            EnumSet.of(
                    LogType.TP_HOME,
                    LogType.TP_WARP,
                    LogType.TP_INVITE
            );

    private static final Set<LogType> CAT_COFFRE =
            EnumSet.of(
                    LogType.CHEST_DEPOSIT,
                    LogType.CHEST_WITHDRAW
            );

    private static final Set<LogType> CAT_ALL =
            EnumSet.allOf(
                    LogType.class
            );

    private final Kfaction plugin;
    private final Gson gson;

    private final Map<String, List<FactionLog>>
            logCache;

    private final Set<String> dirtyFactions;

    private final AuditService auditService;

    private File logsFolder;

    private long retentionHours;
    private int maxLogsPerFaction;

    private BukkitTask flushTask;
    private BukkitTask cleanupTask;

    public LogManager(Kfaction plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException(
                    "plugin cannot be null"
            );
        }

        this.plugin = plugin;
        this.gson = new Gson();

        this.logCache =
                new ConcurrentHashMap<String, List<FactionLog>>();

        this.dirtyFactions =
                ConcurrentHashMap.newKeySet();

        this.auditService =
                new AuditService(plugin);
    }

    public void initialize() {
        logsFolder =
                new File(
                        plugin.getDataFolder(),
                        "logs"
                );

        if (!logsFolder.exists()
                && !logsFolder.mkdirs()) {
            plugin.getLogger().warning(
                    "Impossible de créer le dossier logs legacy"
            );
        }

        retentionHours =
                Math.max(
                        1L,
                        plugin.getConfigManager()
                                .getLong(
                                        "logs.retention-hours",
                                        36L
                                )
                );

        maxLogsPerFaction =
                clamp(
                        plugin.getConfigManager()
                                .getInt(
                                        "logs.max-per-faction",
                                        500
                                ),
                        10,
                        10000
                );

        long cleanupIntervalMinutes =
                Math.max(
                        1L,
                        plugin.getConfigManager()
                                .getLong(
                                        "logs.cleanup-interval-minutes",
                                        30L
                                )
                );

        long flushIntervalTicks =
                plugin.getConfigManager()
                        .getLong(
                                "logs.flush-interval-seconds",
                                10L
                        )
                        * 20L;

        if (flushIntervalTicks <= 0L) {
            flushIntervalTicks =
                    DEFAULT_FLUSH_INTERVAL_TICKS;
        }

        auditService.initialize();

        flushTask =
                plugin.getServer()
                        .getScheduler()
                        .runTaskTimerAsynchronously(
                                plugin,
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        flushDirty();
                                    }
                                },
                                flushIntervalTicks,
                                flushIntervalTicks
                        );

        long cleanupTicks =
                20L
                        * 60L
                        * cleanupIntervalMinutes;

        cleanupTask =
                plugin.getServer()
                        .getScheduler()
                        .runTaskTimerAsynchronously(
                                plugin,
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        cleanupExpiredLogs();
                                    }
                                },
                                cleanupTicks,
                                cleanupTicks
                        );

        plugin.getLogger().info(
                "LogManager V2 initialisé "
                        + "(legacy JSON + audit.db)"
        );
    }

    public void shutdown() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }

        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }

        flushDirty();
        auditService.shutdown();
        logCache.clear();
        dirtyFactions.clear();
    }

    public AuditService getAuditService() {
        return auditService;
    }

    // ============================================================
    // Legacy log API
    // ============================================================

    public void log(
            String factionId,
            LogType type,
            Player player,
            String details
    ) {
        log(
                factionId,
                type,
                player != null
                        ? player.getUniqueId()
                        : null,
                player != null
                        ? player.getName()
                        : "SYSTEM",
                null,
                null,
                details
        );
    }

    public void log(
            String factionId,
            LogType type,
            Player player,
            Player target,
            String details
    ) {
        log(
                factionId,
                type,
                player != null
                        ? player.getUniqueId()
                        : null,
                player != null
                        ? player.getName()
                        : "SYSTEM",
                target != null
                        ? target.getUniqueId()
                        : null,
                target != null
                        ? target.getName()
                        : null,
                details
        );
    }

    public void log(
            String factionId,
            LogType type,
            UUID playerUuid,
            String playerName,
            UUID targetUuid,
            String targetName,
            String details
    ) {
        if (factionId == null
                || factionId.trim().isEmpty()
                || type == null) {
            return;
        }

        FactionLog entry =
                new FactionLog(
                        factionId,
                        type,
                        playerUuid,
                        playerName,
                        targetUuid,
                        targetName,
                        sanitizeLegacyDetails(
                                details
                        )
                );

        /*
         * audit.db reste la trace sécurité complète, même lorsqu'une catégorie
         * est masquée du petit historique legacy /f logs.
         */
        auditService.recordLegacy(
                entry
        );

        if (!isLegacyTypeEnabled(
                type
        )) {
            return;
        }

        List<FactionLog> logs =
                getOrCreateLogs(
                        factionId
                );

        synchronized (logs) {
            logs.add(entry);

            while (logs.size()
                    > maxLogsPerFaction) {
                logs.remove(0);
            }
        }

        dirtyFactions.add(
                factionId
        );
    }

    /**
     * Les toggles logs.types.* ne concernent que l'historique legacy /f logs.
     * L'audit structuré reste toujours enregistré.
     */
    private boolean isLegacyTypeEnabled(
            LogType type
    ) {
        if (type == null) {
            return false;
        }

        switch (type) {
            case MEMBER_JOIN:
            case MEMBER_LEAVE:
            case MEMBER_KICK:
            case MEMBER_PROMOTE:
            case MEMBER_DEMOTE:
                return plugin.getConfigManager()
                        .getBoolean(
                                "logs.types.members",
                                true
                        );

            case TERRITORY_CLAIM:
            case TERRITORY_UNCLAIM:
            case TERRITORY_SETHOME:
            case TERRITORY_SETWARP:
            case TERRITORY_DELWARP:
                return plugin.getConfigManager()
                        .getBoolean(
                                "logs.types.territory",
                                true
                        );

            case ECONOMY_DEPOSIT:
            case ECONOMY_WITHDRAW:
                return plugin.getConfigManager()
                        .getBoolean(
                                "logs.types.economy",
                                true
                        );

            case TP_HOME:
            case TP_WARP:
            case TP_INVITE:
                return plugin.getConfigManager()
                        .getBoolean(
                                "logs.types.teleport",
                                true
                        );

            case CHEST_DEPOSIT:
            case CHEST_WITHDRAW:
                return plugin.getConfigManager()
                        .getBoolean(
                                "logs.types.chest",
                                true
                        );

            default:
                /*
                 * Types V2 sans toggle historique dédié: visibles par défaut.
                 */
                return true;
        }
    }

    // ============================================================
    // Structured audit API
    // ============================================================

    public boolean audit(
            OperationContext context,
            AuditCategory category,
            String action,
            AuditOutcome outcome,
            String factionId,
            UUID targetId,
            String targetName,
            String details
    ) {
        return auditService.record(
                category,
                action,
                outcome,
                factionId,
                context,
                targetId,
                targetName,
                details
        );
    }

    public boolean auditSystem(
            AuditCategory category,
            String action,
            AuditOutcome outcome,
            String factionId,
            String details
    ) {
        return auditService.recordSystem(
                category,
                action,
                outcome,
                factionId,
                details
        );
    }

    // ============================================================
    // Legacy retrieval
    // ============================================================

    public List<FactionLog> getLogs(
            String factionId
    ) {
        List<FactionLog> logs =
                getOrLoadLogs(
                        factionId
                );

        synchronized (logs) {
            return new ArrayList<FactionLog>(
                    logs
            );
        }
    }

    public List<FactionLog> getLogs(
            String factionId,
            LogType type
    ) {
        List<FactionLog> logs =
                getOrLoadLogs(
                        factionId
                );

        List<FactionLog> result =
                new ArrayList<FactionLog>();

        synchronized (logs) {
            for (FactionLog log : logs) {
                if (log.getType() == type) {
                    result.add(log);
                }
            }
        }

        return result;
    }

    public List<FactionLog> getLogs(
            String factionId,
            Set<LogType> types
    ) {
        List<FactionLog> logs =
                getOrLoadLogs(
                        factionId
                );

        List<FactionLog> result =
                new ArrayList<FactionLog>();

        if (types == null
                || types.isEmpty()) {
            return result;
        }

        synchronized (logs) {
            for (FactionLog log : logs) {
                if (types.contains(
                        log.getType()
                )) {
                    result.add(log);
                }
            }
        }

        return result;
    }

    public List<FactionLog> getLogsByPlayer(
            String factionId,
            UUID playerUuid
    ) {
        List<FactionLog> result =
                new ArrayList<FactionLog>();

        if (playerUuid == null) {
            return result;
        }

        List<FactionLog> logs =
                getOrLoadLogs(
                        factionId
                );

        synchronized (logs) {
            for (FactionLog log : logs) {
                if (playerUuid.equals(
                        log.getPlayerUuid()
                )
                        || playerUuid.equals(
                                log.getTargetUuid()
                        )) {
                    result.add(log);
                }
            }
        }

        return result;
    }

    public List<FactionLog> getRecentLogs(
            String factionId,
            int limit
    ) {
        List<FactionLog> logs =
                getOrLoadLogs(
                        factionId
                );

        synchronized (logs) {
            int safeLimit =
                    Math.max(
                            0,
                            limit
                    );

            int start =
                    Math.max(
                            0,
                            logs.size()
                                    - safeLimit
                    );

            return new ArrayList<FactionLog>(
                    logs.subList(
                            start,
                            logs.size()
                    )
            );
        }
    }

    public List<FactionLog> getLogsByCategory(
            String factionId,
            String category
    ) {
        return getLogs(
                factionId,
                getTypesForCategory(
                        category
                )
        );
    }

    public void deleteFactionLogs(
            String factionId
    ) {
        if (factionId == null) {
            return;
        }

        logCache.remove(factionId);
        dirtyFactions.remove(factionId);

        File file =
                new File(
                        logsFolder,
                        factionId + ".json"
                );

        if (file.exists()
                && !file.delete()) {
            plugin.getLogger().warning(
                    "Impossible de supprimer "
                            + file.getName()
            );
        }

        /*
         * audit.db n'est volontairement PAS purgée.
         * Une faction dissoute doit rester auditable.
         */
    }

    public int getLogCount(
            String factionId
    ) {
        return getOrLoadLogs(
                factionId
        ).size();
    }

    // ============================================================
    // Legacy cache / JSON
    // ============================================================

    private Set<LogType> getTypesForCategory(
            String category
    ) {
        String normalized =
                category != null
                        ? category.toLowerCase()
                        : "";

        switch (normalized) {
            case "membres":
            case "members":
                return CAT_MEMBRES;

            case "territoire":
            case "territory":
                return CAT_TERRITOIRE;

            case "economie":
            case "economy":
                return CAT_ECONOMIE;

            case "tp":
            case "teleport":
                return CAT_TP;

            case "coffre":
            case "chest":
                return CAT_COFFRE;

            default:
                return CAT_ALL;
        }
    }

    private List<FactionLog> getOrCreateLogs(
            String factionId
    ) {
        List<FactionLog> existing =
                logCache.get(
                        factionId
                );

        if (existing != null) {
            return existing;
        }

        List<FactionLog> created =
                Collections.synchronizedList(
                        new ArrayList<FactionLog>()
                );

        List<FactionLog> previous =
                logCache.putIfAbsent(
                        factionId,
                        created
                );

        return previous != null
                ? previous
                : created;
    }

    private List<FactionLog> getOrLoadLogs(
            String factionId
    ) {
        if (factionId == null) {
            return Collections.synchronizedList(
                    new ArrayList<FactionLog>()
            );
        }

        List<FactionLog> cached =
                logCache.get(
                        factionId
                );

        if (cached != null) {
            return cached;
        }

        loadLogs(factionId);

        cached =
                logCache.get(
                        factionId
                );

        return cached != null
                ? cached
                : getOrCreateLogs(
                        factionId
                );
    }

    private void flushDirty() {
        if (dirtyFactions.isEmpty()) {
            return;
        }

        Set<String> toFlush =
                ConcurrentHashMap.newKeySet();

        toFlush.addAll(
                dirtyFactions
        );

        dirtyFactions.removeAll(
                toFlush
        );

        for (String factionId : toFlush) {
            if (!saveLogs(
                    factionId
            )) {
                dirtyFactions.add(
                        factionId
                );
            }
        }
    }

    private void loadLogs(
            String factionId
    ) {
        File file =
                new File(
                        logsFolder,
                        factionId + ".json"
                );

        if (!file.exists()) {
            logCache.put(
                    factionId,
                    Collections.synchronizedList(
                            new ArrayList<FactionLog>()
                    )
            );

            return;
        }

        List<FactionLog> logs =
                new ArrayList<FactionLog>();

        try (FileReader reader =
                     new FileReader(file)) {

            JsonElement element =
                    JsonParser.parseReader(
                            reader
                    );

            if (element != null
                    && element.isJsonArray()) {

                for (JsonElement item
                        : element.getAsJsonArray()) {
                    if (item == null
                            || !item.isJsonObject()) {
                        continue;
                    }

                    FactionLog log =
                            parseLogEntry(
                                    item.getAsJsonObject(),
                                    factionId
                            );

                    if (log != null
                            && !log.isExpired(
                                    retentionHours
                            )) {
                        logs.add(log);
                    }
                }
            }

        } catch (IOException exception) {
            plugin.getLogger().warning(
                    "Erreur chargement logs "
                            + factionId
                            + ": "
                            + exception.getMessage()
            );
        }

        while (logs.size()
                > maxLogsPerFaction) {
            logs.remove(0);
        }

        logCache.put(
                factionId,
                Collections.synchronizedList(
                        logs
                )
        );
    }

    private boolean saveLogs(
            String factionId
    ) {
        List<FactionLog> logs =
                logCache.get(
                        factionId
                );

        if (logs == null) {
            return true;
        }

        List<FactionLog> snapshot;

        synchronized (logs) {
            snapshot =
                    new ArrayList<FactionLog>(
                            logs
                    );
        }

        File file =
                new File(
                        logsFolder,
                        factionId + ".json"
                );

        if (snapshot.isEmpty()) {
            return !file.exists()
                    || file.delete();
        }

        try (FileWriter writer =
                     new FileWriter(file)) {

            JsonArray array =
                    new JsonArray();

            for (FactionLog log : snapshot) {
                if (log != null
                        && !log.isExpired(
                                retentionHours
                        )) {
                    array.add(
                            serializeLogEntry(
                                    log
                            )
                    );
                }
            }

            gson.toJson(
                    array,
                    writer
            );

            return true;

        } catch (IOException exception) {
            plugin.getLogger().severe(
                    "Erreur sauvegarde logs "
                            + factionId
                            + ": "
                            + exception.getMessage()
            );

            return false;
        }
    }

    private FactionLog parseLogEntry(
            JsonObject json,
            String factionId
    ) {
        try {
            String id =
                    json.has("id")
                            ? json.get("id")
                                    .getAsString()
                            : UUID.randomUUID()
                                    .toString();

            LogType type =
                    LogType.valueOf(
                            json.get("type")
                                    .getAsString()
                    );

            UUID playerUuid =
                    parseUuid(
                            json.has("playerUuid")
                                    && !json.get("playerUuid")
                                            .isJsonNull()
                                    ? json.get("playerUuid")
                                            .getAsString()
                                    : null
                    );

            String playerName =
                    json.has("playerName")
                            && !json.get("playerName")
                                    .isJsonNull()
                            ? json.get("playerName")
                                    .getAsString()
                            : "SYSTEM";

            UUID targetUuid =
                    parseUuid(
                            json.has("targetUuid")
                                    && !json.get("targetUuid")
                                            .isJsonNull()
                                    ? json.get("targetUuid")
                                            .getAsString()
                                    : null
                    );

            String targetName =
                    json.has("targetName")
                            && !json.get("targetName")
                                    .isJsonNull()
                            ? json.get("targetName")
                                    .getAsString()
                            : null;

            String details =
                    json.has("details")
                            && !json.get("details")
                                    .isJsonNull()
                            ? sanitizeLegacyDetails(
                                    json.get("details")
                                            .getAsString()
                            )
                            : "";

            long timestamp =
                    json.has("timestamp")
                            ? json.get("timestamp")
                                    .getAsLong()
                            : System.currentTimeMillis();

            return new FactionLog(
                    id,
                    factionId,
                    type,
                    playerUuid,
                    playerName,
                    targetUuid,
                    targetName,
                    details,
                    timestamp
            );

        } catch (RuntimeException exception) {
            plugin.getLogger().warning(
                    "Erreur parsing log "
                            + factionId
                            + ": "
                            + exception.getMessage()
            );

            return null;
        }
    }

    private JsonObject serializeLogEntry(
            FactionLog log
    ) {
        JsonObject json =
                new JsonObject();

        json.addProperty(
                "id",
                log.getId()
        );

        json.addProperty(
                "type",
                log.getType().name()
        );

        if (log.getPlayerUuid() != null) {
            json.addProperty(
                    "playerUuid",
                    log.getPlayerUuid()
                            .toString()
            );
        }

        if (log.getPlayerName() != null) {
            json.addProperty(
                    "playerName",
                    log.getPlayerName()
            );
        }

        if (log.getTargetUuid() != null) {
            json.addProperty(
                    "targetUuid",
                    log.getTargetUuid()
                            .toString()
            );
        }

        if (log.getTargetName() != null) {
            json.addProperty(
                    "targetName",
                    log.getTargetName()
            );
        }

        json.addProperty(
                "details",
                sanitizeLegacyDetails(
                        log.getDetails()
                )
        );

        json.addProperty(
                "timestamp",
                log.getTimestamp()
        );

        return json;
    }

    private void cleanupExpiredLogs() {
        int cleaned = 0;

        for (Map.Entry<String, List<FactionLog>> entry
                : logCache.entrySet()) {
            List<FactionLog> logs =
                    entry.getValue();

            int before;

            synchronized (logs) {
                before = logs.size();

                for (int i = logs.size() - 1;
                        i >= 0;
                        i--) {
                    if (logs.get(i)
                            .isExpired(
                                    retentionHours
                            )) {
                        logs.remove(i);
                    }
                }

                if (logs.size() < before) {
                    cleaned += before
                            - logs.size();

                    dirtyFactions.add(
                            entry.getKey()
                    );
                }
            }
        }

        if (cleaned > 0) {
            plugin.getLogger().info(
                    "Nettoyage logs legacy: "
                            + cleaned
                            + " entrées"
            );
        }
    }

    private static UUID parseUuid(
            String value
    ) {
        if (value == null
                || value.trim().isEmpty()) {
            return null;
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String sanitizeLegacyDetails(
            String details
    ) {
        if (details == null) {
            return "";
        }

        String result =
                details.replaceAll(
                        "(?i)(password|passwd|pwd)=([^;\\s]+)",
                        "$1=[REDACTED]"
                );

        return result.length() > 4096
                ? result.substring(0, 4096)
                : result;
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
}
