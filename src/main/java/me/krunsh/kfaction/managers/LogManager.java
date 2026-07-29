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
import me.krunsh.kfaction.data.FactionLog;
import me.krunsh.kfaction.data.FactionLog.LogType;

/**
 * Gestionnaire des logs de faction — optimisé pour 1000+ joueurs
 * 
 * Optimisations clés:
 * - Écriture par batch (dirty-set + flush périodique) au lieu d'un write par log
 * - Listes synchronisées pour thread-safety
 * - Gson compact (pas de PrettyPrinting)
 * - Plafond d'entrées par faction (évite la croissance mémoire infinie)
 * - Cleanup uniquement sur le cache en mémoire (pas de chargement de fichiers non-cachés)
 * - Filtrage par boucle simple au lieu de streams (moins d'allocations)
 */
public class LogManager {
    
    private final Kfaction plugin;
    private final Gson gson;
    
    // Cache des logs par faction (listes synchronisées)
    private final Map<String, List<FactionLog>> logCache;
    
    // Dirty tracking: factions qui ont des logs non sauvegardés
    private final Set<String> dirtyFactions;
    
    // Dossier de stockage
    private File logsFolder;
    
    // Configuration (cachée pour perf)
    private long retentionHours;
    private int maxLogsPerFaction;
    
    // Tâches planifiées
    private BukkitTask flushTask;
    private BukkitTask cleanupTask;
    
    // Intervalle de flush en ticks (10 secondes par défaut)
    private static final long DEFAULT_FLUSH_INTERVAL_TICKS = 20L * 10;
    
    // Catégories pré-calculées (évite la re-création à chaque appel)
    private static final Set<LogType> CAT_MEMBRES = EnumSet.of(
        LogType.MEMBER_JOIN, LogType.MEMBER_LEAVE, 
        LogType.MEMBER_KICK, LogType.MEMBER_PROMOTE, LogType.MEMBER_DEMOTE);
    private static final Set<LogType> CAT_TERRITOIRE = EnumSet.of(
        LogType.TERRITORY_CLAIM, LogType.TERRITORY_UNCLAIM,
        LogType.TERRITORY_SETHOME, LogType.TERRITORY_SETWARP, LogType.TERRITORY_DELWARP);
    private static final Set<LogType> CAT_ECONOMIE = EnumSet.of(
        LogType.ECONOMY_DEPOSIT, LogType.ECONOMY_WITHDRAW);
    private static final Set<LogType> CAT_TP = EnumSet.of(
        LogType.TP_HOME, LogType.TP_WARP, LogType.TP_INVITE);
    private static final Set<LogType> CAT_COFFRE = EnumSet.of(
        LogType.CHEST_DEPOSIT, LogType.CHEST_WITHDRAW);
    private static final Set<LogType> CAT_ALL = EnumSet.allOf(LogType.class);
    
    public LogManager(Kfaction plugin) {
        this.plugin = plugin;
        // Compact JSON — pas de PrettyPrinting (économise CPU + disque)
        this.gson = new Gson();
        this.logCache = new ConcurrentHashMap<>();
        this.dirtyFactions = ConcurrentHashMap.newKeySet();
    }
    
    /**
     * Initialise le gestionnaire de logs
     */
    public void initialize() {
        // Créer le dossier logs
        logsFolder = new File(plugin.getDataFolder(), "logs");
        if (!logsFolder.exists()) {
            logsFolder.mkdirs();
        }
        
        // Charger la configuration
        retentionHours = plugin.getConfigManager().getLong("logs.retention-hours", 36);
        maxLogsPerFaction = plugin.getConfigManager().getInt("logs.max-per-faction", 500);
        long cleanupInterval = plugin.getConfigManager().getLong("logs.cleanup-interval-minutes", 30);
        long flushInterval = plugin.getConfigManager().getLong("logs.flush-interval-seconds", 10) * 20L;
        if (flushInterval <= 0) flushInterval = DEFAULT_FLUSH_INTERVAL_TICKS;
        
        // Tâche de flush batch — sauvegarde les factions dirty toutes les N secondes
        flushTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
            plugin,
            this::flushDirty,
            flushInterval,
            flushInterval
        );
        
        // Tâche de nettoyage des logs expirés (uniquement en mémoire)
        cleanupTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
            plugin, 
            this::cleanupExpiredLogs, 
            20L * 60 * cleanupInterval,
            20L * 60 * cleanupInterval
        );
        
        plugin.getLogger().info("LogManager initialisé (rétention: " + retentionHours 
            + "h, max/faction: " + maxLogsPerFaction 
            + ", flush: " + (flushInterval / 20) + "s)");
    }
    
    /**
     * Arrête le gestionnaire de logs
     */
    public void shutdown() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        
        // Flush final synchrone — sauvegarder tout
        flushDirty();
        logCache.clear();
    }
    
    // ============================================================
    // LOGGING METHODS
    // ============================================================
    
    /**
     * Ajoute un log pour une action simple (sans cible)
     */
    public void log(String factionId, LogType type, Player player, String details) {
        log(factionId, type, player.getUniqueId(), player.getName(), null, null, details);
    }
    
    /**
     * Ajoute un log pour une action avec cible
     */
    public void log(String factionId, LogType type, Player player, Player target, String details) {
        UUID targetUuid = target != null ? target.getUniqueId() : null;
        String targetName = target != null ? target.getName() : null;
        log(factionId, type, player.getUniqueId(), player.getName(), targetUuid, targetName, details);
    }
    
    /**
     * Ajoute un log avec tous les paramètres.
     * Le log est ajouté en mémoire et la faction est marquée dirty.
     * L'écriture disque est différée au prochain flush batch.
     */
    public void log(String factionId, LogType type, 
                    UUID playerUuid, String playerName,
                    UUID targetUuid, String targetName,
                    String details) {
        FactionLog logEntry = new FactionLog(factionId, type, 
            playerUuid, playerName, targetUuid, targetName, details);
        
        List<FactionLog> logs = getOrCreateLogs(factionId);
        synchronized (logs) {
            logs.add(logEntry);
            // Élaguer si au-dessus du plafond (supprimer les plus anciens)
            while (logs.size() > maxLogsPerFaction) {
                logs.remove(0);
            }
        }
        
        // Marquer dirty — sera sauvegardé au prochain flush
        dirtyFactions.add(factionId);
    }
    
    // ============================================================
    // LOG RETRIEVAL (sans streams — boucles simples pour réduire les allocations)
    // ============================================================
    
    /**
     * Récupère tous les logs d'une faction
     */
    public List<FactionLog> getLogs(String factionId) {
        List<FactionLog> logs = getOrLoadLogs(factionId);
        synchronized (logs) {
            return new ArrayList<>(logs);
        }
    }
    
    /**
     * Récupère les logs filtrés par type
     */
    public List<FactionLog> getLogs(String factionId, LogType type) {
        List<FactionLog> logs = getOrLoadLogs(factionId);
        List<FactionLog> result = new ArrayList<>();
        synchronized (logs) {
            for (int i = 0, n = logs.size(); i < n; i++) {
                FactionLog log = logs.get(i);
                if (log.getType() == type) {
                    result.add(log);
                }
            }
        }
        return result;
    }
    
    /**
     * Récupère les logs filtrés par types multiples
     */
    public List<FactionLog> getLogs(String factionId, Set<LogType> types) {
        List<FactionLog> logs = getOrLoadLogs(factionId);
        List<FactionLog> result = new ArrayList<>();
        synchronized (logs) {
            for (int i = 0, n = logs.size(); i < n; i++) {
                FactionLog log = logs.get(i);
                if (types.contains(log.getType())) {
                    result.add(log);
                }
            }
        }
        return result;
    }
    
    /**
     * Récupère les logs d'un joueur spécifique
     */
    public List<FactionLog> getLogsByPlayer(String factionId, UUID playerUuid) {
        List<FactionLog> logs = getOrLoadLogs(factionId);
        List<FactionLog> result = new ArrayList<>();
        synchronized (logs) {
            for (int i = 0, n = logs.size(); i < n; i++) {
                FactionLog log = logs.get(i);
                if (log.getPlayerUuid().equals(playerUuid) || 
                    (log.getTargetUuid() != null && log.getTargetUuid().equals(playerUuid))) {
                    result.add(log);
                }
            }
        }
        return result;
    }
    
    /**
     * Récupère les N derniers logs
     */
    public List<FactionLog> getRecentLogs(String factionId, int limit) {
        List<FactionLog> logs = getOrLoadLogs(factionId);
        synchronized (logs) {
            int start = Math.max(0, logs.size() - limit);
            return new ArrayList<>(logs.subList(start, logs.size()));
        }
    }
    
    /**
     * Récupère les logs pour une catégorie (membres, territoire, économie)
     */
    public List<FactionLog> getLogsByCategory(String factionId, String category) {
        Set<LogType> types = getTypesForCategory(category);
        return getLogs(factionId, types);
    }
    
    /**
     * Retourne les types de log pour une catégorie (instances pré-calculées)
     */
    private Set<LogType> getTypesForCategory(String category) {
        switch (category.toLowerCase()) {
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
    
    // ============================================================
    // CACHE MANAGEMENT
    // ============================================================
    
    /**
     * Récupère ou crée la liste des logs pour une faction.
     * Retourne une liste synchronisée pour thread-safety.
     */
    private List<FactionLog> getOrCreateLogs(String factionId) {
        return logCache.computeIfAbsent(factionId, 
            k -> Collections.synchronizedList(new ArrayList<>()));
    }
    
    /**
     * Récupère ou charge les logs pour une faction
     */
    private List<FactionLog> getOrLoadLogs(String factionId) {
        List<FactionLog> cached = logCache.get(factionId);
        if (cached != null) {
            return cached;
        }
        loadLogs(factionId);
        cached = logCache.get(factionId);
        return cached != null ? cached : Collections.synchronizedList(new ArrayList<>());
    }
    
    // ============================================================
    // BATCH FLUSH (cœur de l'optimisation)
    // ============================================================
    
    /**
     * Flush toutes les factions dirty vers le disque.
     * Appelé périodiquement par la tâche async, et une fois à shutdown.
     * Utilise un drain atomique du dirty-set pour éviter les race conditions.
     */
    private void flushDirty() {
        if (dirtyFactions.isEmpty()) return;
        
        // Drain atomique: copier et vider en une seule opération
        Set<String> toFlush = ConcurrentHashMap.newKeySet();
        for (String id : dirtyFactions) {
            toFlush.add(id);
        }
        dirtyFactions.removeAll(toFlush);
        
        for (String factionId : toFlush) {
            saveLogs(factionId);
        }
    }
    
    // ============================================================
    // FILE I/O
    // ============================================================
    
    /**
     * Charge les logs d'une faction depuis le fichier
     */
    private void loadLogs(String factionId) {
        File file = new File(logsFolder, factionId + ".json");
        if (!file.exists()) {
            logCache.put(factionId, Collections.synchronizedList(new ArrayList<>()));
            return;
        }
        
        try (FileReader reader = new FileReader(file)) {
            JsonElement element = new JsonParser().parse(reader);
            if (element.isJsonArray()) {
                List<FactionLog> logs = new ArrayList<>();
                JsonArray array = element.getAsJsonArray();
                
                for (JsonElement logElement : array) {
                    FactionLog log = parseLogEntry(logElement.getAsJsonObject(), factionId);
                    if (log != null && !log.isExpired(retentionHours)) {
                        logs.add(log);
                    }
                }
                
                // Élaguer si trop de logs chargés
                while (logs.size() > maxLogsPerFaction) {
                    logs.remove(0);
                }
                
                logCache.put(factionId, Collections.synchronizedList(logs));
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Erreur chargement logs " + factionId + ": " + e.getMessage());
            logCache.put(factionId, Collections.synchronizedList(new ArrayList<>()));
        }
    }
    
    /**
     * Sauvegarde les logs d'une faction dans le fichier.
     * Snapshot la liste sous synchronized pour éviter les ConcurrentModificationException.
     */
    private void saveLogs(String factionId) {
        List<FactionLog> logs = logCache.get(factionId);
        if (logs == null) return;
        
        // Snapshot thread-safe
        List<FactionLog> snapshot;
        synchronized (logs) {
            if (logs.isEmpty()) {
                File file = new File(logsFolder, factionId + ".json");
                if (file.exists()) {
                    file.delete();
                }
                return;
            }
            snapshot = new ArrayList<>(logs);
        }
        
        File file = new File(logsFolder, factionId + ".json");
        
        try (FileWriter writer = new FileWriter(file)) {
            JsonArray array = new JsonArray();
            
            for (FactionLog log : snapshot) {
                if (!log.isExpired(retentionHours)) {
                    array.add(serializeLogEntry(log));
                }
            }
            
            gson.toJson(array, writer);
        } catch (IOException e) {
            plugin.getLogger().severe("Erreur sauvegarde logs " + factionId + ": " + e.getMessage());
        }
    }
    
    /**
     * Parse un log depuis JSON
     */
    private FactionLog parseLogEntry(JsonObject json, String factionId) {
        try {
            String id = json.has("id") ? json.get("id").getAsString() : UUID.randomUUID().toString();
            LogType type = LogType.valueOf(json.get("type").getAsString());
            UUID playerUuid = UUID.fromString(json.get("playerUuid").getAsString());
            String playerName = json.get("playerName").getAsString();
            
            UUID targetUuid = null;
            String targetName = null;
            if (json.has("targetUuid") && !json.get("targetUuid").isJsonNull()) {
                targetUuid = UUID.fromString(json.get("targetUuid").getAsString());
                targetName = json.get("targetName").getAsString();
            }
            
            String details = json.has("details") ? json.get("details").getAsString() : "";
            long timestamp = json.get("timestamp").getAsLong();
            
            return new FactionLog(id, factionId, type, playerUuid, playerName, 
                targetUuid, targetName, details, timestamp);
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur parsing log: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Sérialise un log en JSON
     */
    private JsonObject serializeLogEntry(FactionLog log) {
        JsonObject json = new JsonObject();
        json.addProperty("id", log.getId());
        json.addProperty("type", log.getType().name());
        json.addProperty("playerUuid", log.getPlayerUuid().toString());
        json.addProperty("playerName", log.getPlayerName());
        
        if (log.hasTarget()) {
            json.addProperty("targetUuid", log.getTargetUuid().toString());
            json.addProperty("targetName", log.getTargetName());
        }
        
        json.addProperty("details", log.getDetails());
        json.addProperty("timestamp", log.getTimestamp());
        
        return json;
    }
    
    // ============================================================
    // CLEANUP (uniquement le cache en mémoire — pas de chargement de fichiers non-cachés)
    // ============================================================
    
    /**
     * Nettoie les logs expirés des factions en cache.
     * Ne charge PAS les fichiers non-cachés (économie I/O massive avec beaucoup de factions).
     * Les fichiers non-cachés seront nettoyés naturellement au prochain chargement.
     */
    private void cleanupExpiredLogs() {
        int cleaned = 0;
        for (Map.Entry<String, List<FactionLog>> entry : logCache.entrySet()) {
            List<FactionLog> logs = entry.getValue();
            int before;
            synchronized (logs) {
                before = logs.size();
                logs.removeIf(log -> log.isExpired(retentionHours));
                if (logs.size() < before) {
                    cleaned += (before - logs.size());
                    dirtyFactions.add(entry.getKey());
                }
            }
        }
        
        // Supprimer les fichiers de factions qui n'existent plus
        // (check rapide par liste de fichiers, pas de chargement)
        File[] files = logsFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File file : files) {
                // Supprimer si le fichier est très ancien (> 2x rétention)
                long ageHours = (System.currentTimeMillis() - file.lastModified()) / 3600000L;
                if (ageHours > retentionHours * 2) {
                    file.delete();
                    cleaned++;
                }
            }
        }
        
        if (cleaned > 0) {
            plugin.getLogger().info("Nettoyage logs: " + cleaned + " entrées/fichiers supprimés");
        }
    }
    
    /**
     * Supprime tous les logs d'une faction
     */
    public void deleteFactionLogs(String factionId) {
        logCache.remove(factionId);
        dirtyFactions.remove(factionId);
        File file = new File(logsFolder, factionId + ".json");
        if (file.exists()) {
            file.delete();
        }
    }
    
    /**
     * Retourne le nombre total de logs pour une faction
     */
    public int getLogCount(String factionId) {
        List<FactionLog> logs = getOrLoadLogs(factionId);
        synchronized (logs) {
            return logs.size();
        }
    }
}
