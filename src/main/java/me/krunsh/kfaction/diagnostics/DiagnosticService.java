package me.krunsh.kfaction.diagnostics;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.api.v2.KfactionApiV2;
import me.krunsh.kfaction.api.v2.KfactionApis;
import me.krunsh.kfaction.audit.AuditService;
import me.krunsh.kfaction.audit.AuditStore;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionRole;
import me.krunsh.kfaction.data.Relation;
import me.krunsh.kfaction.hooks.IntegrationState;
import me.krunsh.kfaction.managers.FPlayerManager;
import me.krunsh.kfaction.managers.FactionManager;
import me.krunsh.kfaction.managers.QuestManager;
import me.krunsh.kfaction.managers.StorageManager;
import me.krunsh.kfaction.progression.FactionProgressState;
import me.krunsh.kfaction.progression.ValidationIssue;
import me.krunsh.kfaction.storage.SQLiteStorage;
import me.krunsh.kfaction.storage.Storage;
import me.krunsh.kfaction.storage.StorageSnapshot;
import me.krunsh.kfaction.storage.StorageWriterStats;

/**
 * Diagnostic read-only de Kfaction V2.
 *
 * Invariants:
 * - aucune création de FPlayer;
 * - aucun rebuild d'index;
 * - aucune écriture storage;
 * - aucun appel ProgressionService#getStatus car celui-ci peut réconcilier
 *   un état durable;
 * - aucune réparation automatique.
 */
public final class DiagnosticService {

    private final Kfaction plugin;

    public DiagnosticService(
            Kfaction plugin
    ) {
        if (plugin == null) {
            throw new IllegalArgumentException(
                    "plugin cannot be null"
            );
        }

        this.plugin = plugin;
    }

    public DiagnosticReport run(
            DiagnosticScope requestedScope,
            boolean full
    ) {
        long startedAt =
                System.currentTimeMillis();

        DiagnosticScope scope =
                requestedScope != null
                        ? requestedScope
                        : DiagnosticScope.ALL;

        List<DiagnosticCheck> checks =
                new ArrayList<DiagnosticCheck>();

        if (!Bukkit.isPrimaryThread()) {
            checks.add(
                    DiagnosticCheck.problem(
                            "runtime.main-thread",
                            DiagnosticSeverity.ERROR,
                            "Thread d'exécution",
                            "Le doctor doit être exécuté sur le thread Bukkit principal.",
                            "Relancer /kf doctor depuis une commande Bukkit normale."
                    )
            );

            return new DiagnosticReport(
                    scope,
                    full,
                    startedAt,
                    System.currentTimeMillis()
                            - startedAt,
                    checks
            );
        }

        try {
            if (scope == DiagnosticScope.ALL
                    || scope == DiagnosticScope.RUNTIME) {
                checkRuntime(checks);
            }

            if (scope == DiagnosticScope.ALL
                    || scope == DiagnosticScope.STORAGE) {
                checkStorage(checks);
            }

            if (scope == DiagnosticScope.ALL
                    || scope == DiagnosticScope.AUDIT) {
                checkAudit(checks);
            }

            if (scope == DiagnosticScope.ALL
                    || scope == DiagnosticScope.INTEGRATIONS) {
                checkIntegrations(checks);
            }

            if (scope == DiagnosticScope.ALL
                    || scope == DiagnosticScope.INDEXES) {
                checkIndexes(
                        checks,
                        full
                );
            }

            if (scope == DiagnosticScope.ALL
                    || scope == DiagnosticScope.PROGRESSION) {
                checkProgression(
                        checks,
                        full
                );
            }

            if (scope == DiagnosticScope.ALL
                    || scope == DiagnosticScope.ZONES) {
                checkZones(
                        checks,
                        full
                );
            }

        } catch (Throwable throwable) {
            checks.add(
                    DiagnosticCheck.problem(
                            "doctor.internal-exception",
                            DiagnosticSeverity.ERROR,
                            "Diagnostic interrompu",
                            throwable.getClass()
                                    .getSimpleName()
                                    + ": "
                                    + safe(
                                            throwable.getMessage()
                                    ),
                            "Consulter la console puis relancer /kf doctor avec un scope précis."
                    )
            );
        }

        return new DiagnosticReport(
                scope,
                full,
                startedAt,
                System.currentTimeMillis()
                        - startedAt,
                checks
        );
    }

    public VersionSnapshot captureVersion() {
        Storage storage =
                plugin.getStorageManager() != null
                        ? plugin.getStorageManager()
                                .getStorage()
                        : null;

        int databaseSchema = -1;

        if (storage instanceof SQLiteStorage) {
            databaseSchema =
                    ((SQLiteStorage) storage)
                            .getDatabaseSchemaVersionForDiagnostics();
        }

        Runtime runtime =
                Runtime.getRuntime();

        long usedMemory =
                runtime.totalMemory()
                        - runtime.freeMemory();

        KfactionApiV2 api =
                KfactionApis.get();

        return new VersionSnapshot(
                plugin.getDescription()
                        .getVersion(),
                api != null
                        ? api.getApiVersion()
                        : "unavailable",
                System.getProperty(
                        "java.version",
                        "unknown"
                ),
                Bukkit.getVersion(),
                storage != null
                        ? storage.getType()
                        : "none",
                storage != null
                        && storage.isConnected(),
                StorageSnapshot.CURRENT_SCHEMA_VERSION,
                databaseSchema,
                plugin.getDataFolder()
                        .getAbsolutePath(),
                ManagementFactory.getRuntimeMXBean()
                        .getUptime(),
                usedMemory,
                runtime.maxMemory()
        );
    }

    // ============================================================
    // Runtime
    // ============================================================

    private void checkRuntime(
            List<DiagnosticCheck> checks
    ) {
        checks.add(
                DiagnosticCheck.of(
                        "runtime.plugin",
                        plugin.isEnabled()
                                ? DiagnosticSeverity.OK
                                : DiagnosticSeverity.ERROR,
                        "Plugin",
                        "Kfaction "
                                + plugin.getDescription()
                                        .getVersion()
                                + " | Java "
                                + System.getProperty(
                                        "java.version",
                                        "unknown"
                                )
                )
        );

        KfactionApiV2 api =
                KfactionApis.get();

        if (api == null) {
            checks.add(
                    DiagnosticCheck.problem(
                            "runtime.api",
                            DiagnosticSeverity.ERROR,
                            "API V2",
                            "Aucun provider KfactionApiV2 dans ServicesManager.",
                            "Vérifier KfactionAPI / Lot20-21 et le bootstrap."
                    )
            );
        } else if (api.getApiMajor() != 2) {
            checks.add(
                    DiagnosticCheck.problem(
                            "runtime.api",
                            DiagnosticSeverity.ERROR,
                            "API V2",
                            "Provider incompatible: "
                                    + api.getApiVersion(),
                            "Utiliser une intégration API major 2."
                    )
            );
        } else {
            checks.add(
                    DiagnosticCheck.of(
                            "runtime.api",
                            DiagnosticSeverity.OK,
                            "API V2",
                            api.getApiVersion()
                    )
            );
        }

        File dataFolder =
                plugin.getDataFolder();

        boolean folderOk =
                dataFolder.exists()
                        && dataFolder.isDirectory()
                        && dataFolder.canRead()
                        && dataFolder.canWrite();

        checks.add(
                folderOk
                        ? DiagnosticCheck.of(
                                "runtime.data-folder",
                                DiagnosticSeverity.OK,
                                "Dossier de données",
                                dataFolder.getAbsolutePath()
                        )
                        : DiagnosticCheck.problem(
                                "runtime.data-folder",
                                DiagnosticSeverity.ERROR,
                                "Dossier de données",
                                dataFolder.getAbsolutePath()
                                        + " n'est pas lisible/écrivable.",
                                "Vérifier les permissions système du dossier plugins/Kfaction."
                        )
        );

        Runtime runtime =
                Runtime.getRuntime();

        long used =
                runtime.totalMemory()
                        - runtime.freeMemory();

        long max =
                runtime.maxMemory();

        int percent =
                max > 0L
                        ? (int) Math.min(
                                100L,
                                used * 100L / max
                        )
                        : 0;

        int warningPercent =
                clamp(
                        plugin.getConfigManager()
                                .getInt(
                                        "diagnostics.memory-warning-percent",
                                        90
                                ),
                        50,
                        99
                );

        checks.add(
                DiagnosticCheck.of(
                        "runtime.memory",
                        percent >= warningPercent
                                ? DiagnosticSeverity.WARNING
                                : DiagnosticSeverity.OK,
                        "Mémoire JVM",
                        formatBytes(used)
                                + " / "
                                + formatBytes(max)
                                + " ("
                                + percent
                                + "%)"
                )
        );

        checks.add(
                DiagnosticCheck.of(
                        "runtime.server",
                        DiagnosticSeverity.INFO,
                        "Serveur",
                        Bukkit.getVersion()
                                + " | Bukkit "
                                + Bukkit.getBukkitVersion()
                )
        );
    }

    // ============================================================
    // Storage
    // ============================================================

    private void checkStorage(
            List<DiagnosticCheck> checks
    ) {
        StorageManager manager =
                plugin.getStorageManager();

        if (manager == null) {
            checks.add(
                    DiagnosticCheck.problem(
                            "storage.manager",
                            DiagnosticSeverity.ERROR,
                            "StorageManager",
                            "StorageManager est null.",
                            "Le bootstrap storage n'a pas été initialisé."
                    )
            );
            return;
        }

        Storage storage =
                manager.getStorage();

        if (storage == null) {
            checks.add(
                    DiagnosticCheck.problem(
                            "storage.backend",
                            DiagnosticSeverity.ERROR,
                            "Backend storage",
                            "Aucun backend actif.",
                            "Vérifier storage.type dans config.yml et les logs de démarrage."
                    )
            );
            return;
        }

        checks.add(
                storage.isConnected()
                        ? DiagnosticCheck.of(
                                "storage.connection",
                                DiagnosticSeverity.OK,
                                "Storage",
                                storage.getType()
                                        + " connecté"
                        )
                        : DiagnosticCheck.problem(
                                "storage.connection",
                                DiagnosticSeverity.ERROR,
                                "Storage",
                                storage.getType()
                                        + " déconnecté",
                                "Consulter les erreurs SQLite/FlatFile avant toute mutation admin."
                        )
        );

        int dirty =
                manager.getDirtyFactionCount()
                        + manager.getDirtyPlayerCount()
                        + (manager.isGlobalZonesDirty()
                                ? 1
                                : 0)
                        + (manager.isGraceStateDirty()
                                ? 1
                                : 0);

        int dirtyWarning =
                Math.max(
                        10,
                        plugin.getConfigManager()
                                .getInt(
                                        "diagnostics.storage-dirty-warning",
                                        1000
                                )
                );

        checks.add(
                DiagnosticCheck.of(
                        "storage.dirty",
                        dirty >= dirtyWarning
                                ? DiagnosticSeverity.WARNING
                                : DiagnosticSeverity.OK,
                        "Snapshots dirty",
                        "factions="
                                + manager.getDirtyFactionCount()
                                + ", players="
                                + manager.getDirtyPlayerCount()
                                + ", zones="
                                + manager.isGlobalZonesDirty()
                                + ", grace="
                                + manager.isGraceStateDirty()
                )
        );

        StorageWriterStats writerStats =
                manager.getWriterStats();

        int queueWarningPercent =
                clamp(
                        plugin.getConfigManager()
                                .getInt(
                                        "diagnostics.storage-writer-queue-warning-percent",
                                        75
                                ),
                        25,
                        99
                );

        DiagnosticSeverity writerSeverity =
                writerStats.getRejectedTasks() > 0L
                        || writerStats.getQueuePercent()
                                >= queueWarningPercent
                        ? DiagnosticSeverity.WARNING
                        : DiagnosticSeverity.OK;

        checks.add(
                DiagnosticCheck.of(
                        "storage.writer-queue",
                        writerSeverity,
                        "File writer storage",
                        writerStats.getQueueSize()
                                + " / "
                                + writerStats.getQueueCapacity()
                                + " ("
                                + writerStats.getQueuePercent()
                                + "%)"
                                + " | accepted="
                                + writerStats.getAcceptedTasks()
                                + ", rejected="
                                + writerStats.getRejectedTasks()
                )
        );

        checks.add(
                writerStats.getPendingDeleteCount() > 0
                        ? DiagnosticCheck.problem(
                                "storage.pending-deletes",
                                DiagnosticSeverity.WARNING,
                                "Suppressions storage différées",
                                "factions="
                                        + writerStats.getPendingFactionDeletes()
                                        + ", players="
                                        + writerStats.getPendingPlayerDeletes(),
                                "Le writer les retentera automatiquement; vérifier la charge disque si elles persistent."
                        )
                        : DiagnosticCheck.of(
                                "storage.pending-deletes",
                                DiagnosticSeverity.OK,
                                "Suppressions storage différées",
                                "0"
                        )
        );

        if (storage instanceof SQLiteStorage) {
            SQLiteStorage sqlite =
                    (SQLiteStorage) storage;

            File database =
                    sqlite.getDatabaseFile();

            int schema =
                    sqlite.getDatabaseSchemaVersionForDiagnostics();

            boolean dbOk =
                    database != null
                            && database.isFile()
                            && database.canRead()
                            && database.canWrite();

            checks.add(
                    dbOk
                            ? DiagnosticCheck.of(
                                    "storage.sqlite-file",
                                    DiagnosticSeverity.OK,
                                    "SQLite",
                                    database.getAbsolutePath()
                                            + " | "
                                            + formatBytes(
                                                    database.length()
                                            )
                                            + " | schema="
                                            + schema
                            )
                            : DiagnosticCheck.problem(
                                    "storage.sqlite-file",
                                    DiagnosticSeverity.ERROR,
                                    "SQLite",
                                    database != null
                                            ? database.getAbsolutePath()
                                            : "databaseFile=null",
                                    "Vérifier kfaction.db et ses permissions."
                            )
            );

            if (schema < 0) {
                checks.add(
                        DiagnosticCheck.problem(
                                "storage.sqlite-schema",
                                DiagnosticSeverity.ERROR,
                                "Schema SQLite",
                                "Version de schema illisible.",
                                "Consulter les erreurs SQLite; ne pas forcer une migration manuelle."
                        )
                );
            }
        }

        checks.add(
                DiagnosticCheck.of(
                        "storage.payload-schema",
                        DiagnosticSeverity.INFO,
                        "Schema payload",
                        String.valueOf(
                                StorageSnapshot.CURRENT_SCHEMA_VERSION
                        )
                )
        );
    }

    // ============================================================
    // Audit
    // ============================================================

    private void checkAudit(
            List<DiagnosticCheck> checks
    ) {
        if (plugin.getLogManager() == null) {
            checks.add(
                    DiagnosticCheck.problem(
                            "audit.manager",
                            DiagnosticSeverity.ERROR,
                            "LogManager",
                            "LogManager est null.",
                            "Vérifier le bootstrap Audit V2."
                    )
            );
            return;
        }

        AuditService audit =
                plugin.getLogManager()
                        .getAuditService();

        if (audit == null) {
            checks.add(
                    DiagnosticCheck.problem(
                            "audit.service",
                            DiagnosticSeverity.ERROR,
                            "AuditService",
                            "AuditService est null.",
                            "Vérifier Lot17 et l'initialisation du LogManager."
                    )
            );
            return;
        }

        int queueSize =
                audit.getQueueSize();

        int capacity =
                Math.max(
                        1,
                        audit.getQueueCapacity()
                );

        int percent =
                Math.min(
                        100,
                        queueSize * 100 / capacity
                );

        int warningPercent =
                clamp(
                        plugin.getConfigManager()
                                .getInt(
                                        "diagnostics.audit-queue-warning-percent",
                                        75
                                ),
                        25,
                        99
                );

        checks.add(
                DiagnosticCheck.of(
                        "audit.queue",
                        percent >= warningPercent
                                ? DiagnosticSeverity.WARNING
                                : DiagnosticSeverity.OK,
                        "File Audit V2",
                        queueSize
                                + " / "
                                + capacity
                                + " ("
                                + percent
                                + "%)"
                )
        );

        long dropped =
                audit.getDroppedEntries();

        checks.add(
                dropped > 0L
                        ? DiagnosticCheck.problem(
                                "audit.dropped",
                                DiagnosticSeverity.WARNING,
                                "Audit perdu",
                                dropped
                                        + " entrée(s) rejetée(s) depuis le démarrage.",
                                "Vérifier charge disque, queue-capacity et flush-interval-ms."
                        )
                        : DiagnosticCheck.of(
                                "audit.dropped",
                                DiagnosticSeverity.OK,
                                "Audit perdu",
                                "0 entrée"
                        )
        );

        int queryQueueCapacity =
                Math.max(
                        1,
                        audit.getQueryQueueCapacity()
                );

        int queryQueuePercent =
                Math.min(
                        100,
                        audit.getQueryQueueSize()
                                * 100
                                / queryQueueCapacity
                );

        int queryWarningPercent =
                clamp(
                        plugin.getConfigManager()
                                .getInt(
                                        "diagnostics.audit-query-queue-warning-percent",
                                        75
                                ),
                        25,
                        99
                );

        DiagnosticSeverity querySeverity =
                audit.getRejectedQueries() > 0L
                        || queryQueuePercent
                                >= queryWarningPercent
                        ? DiagnosticSeverity.WARNING
                        : DiagnosticSeverity.OK;

        checks.add(
                DiagnosticCheck.of(
                        "audit.query-queue",
                        querySeverity,
                        "File requêtes Audit V2",
                        audit.getQueryQueueSize()
                                + " / "
                                + queryQueueCapacity
                                + " ("
                                + queryQueuePercent
                                + "%)"
                                + " | rejected="
                                + audit.getRejectedQueries()
                                + ", failed="
                                + audit.getFailedQueries()
                )
        );

        AuditStore store =
                audit.getStore();

        File database =
                store != null
                        ? store.getDatabaseFile()
                        : null;

        boolean dbOk =
                database != null
                        && database.isFile()
                        && database.canRead()
                        && database.canWrite();

        checks.add(
                dbOk
                        ? DiagnosticCheck.of(
                                "audit.database",
                                DiagnosticSeverity.OK,
                                "audit.db",
                                database.getAbsolutePath()
                                        + " | "
                                        + formatBytes(
                                                database.length()
                                        )
                        )
                        : DiagnosticCheck.problem(
                                "audit.database",
                                DiagnosticSeverity.ERROR,
                                "audit.db",
                                database != null
                                        ? database.getAbsolutePath()
                                        : "store/database null",
                                "Vérifier l'initialisation AuditStore et les droits filesystem."
                        )
        );
    }

    // ============================================================
    // Integrations
    // ============================================================

    private void checkIntegrations(
            List<DiagnosticCheck> checks
    ) {
        if (plugin.getHookManager() == null) {
            checks.add(
                    DiagnosticCheck.problem(
                            "integrations.manager",
                            DiagnosticSeverity.ERROR,
                            "HookManager",
                            "HookManager est null.",
                            "Vérifier le bootstrap des intégrations."
                    )
            );
            return;
        }

        Map<String, IntegrationState> states =
                plugin.getHookManager()
                        .getIntegrationStates();

        if (states.isEmpty()) {
            checks.add(
                    DiagnosticCheck.of(
                            "integrations.empty",
                            DiagnosticSeverity.INFO,
                            "Intégrations",
                            "Aucune intégration déclarée."
                    )
            );
            return;
        }

        for (Map.Entry<String, IntegrationState> entry
                : states.entrySet()) {
            IntegrationState state =
                    entry.getValue();

            if (state == null) {
                continue;
            }

            DiagnosticSeverity severity;

            switch (state.getStatus()) {
                case ACTIVE:
                    severity =
                            DiagnosticSeverity.OK;
                    break;

                case FAILED:
                    severity =
                            DiagnosticSeverity.WARNING;
                    break;

                case STARTING:
                    severity =
                            DiagnosticSeverity.WARNING;
                    break;

                case DISABLED:
                case MISSING:
                default:
                    severity =
                            DiagnosticSeverity.INFO;
                    break;
            }

            String detail =
                    state.getPluginName()
                            + " = "
                            + state.getStatus().name();

            if (state.getDetail() != null) {
                detail +=
                        " | "
                                + state.getDetail();
            }

            checks.add(
                    DiagnosticCheck.of(
                            "integration."
                                    + entry.getKey(),
                            severity,
                            "Intégration "
                                    + entry.getKey(),
                            detail
                    )
            );
        }
    }

    // ============================================================
    // Indexes / domain integrity
    // ============================================================

    private void checkIndexes(
            List<DiagnosticCheck> checks,
            boolean full
    ) {
        FactionManager factionManager =
                plugin.getFactionManager();

        FPlayerManager fPlayerManager =
                plugin.getFPlayerManager();

        if (factionManager == null
                || fPlayerManager == null
                || plugin.getClaimManager() == null) {
            checks.add(
                    DiagnosticCheck.problem(
                            "indexes.managers",
                            DiagnosticSeverity.ERROR,
                            "Managers domaine",
                            "FactionManager/FPlayerManager/ClaimManager indisponible.",
                            "Vérifier l'ordre d'initialisation du core."
                    )
            );
            return;
        }

        Collection<Faction> factionCollection =
                factionManager.getPlayerFactions();

        List<Faction> factions =
                new ArrayList<Faction>(
                        factionCollection
                );

        int managerCount =
                factionManager.getFactionCount();

        checks.add(
                managerCount == factions.size()
                        ? DiagnosticCheck.of(
                                "indexes.faction-count",
                                DiagnosticSeverity.OK,
                                "Index factions",
                                managerCount
                                        + " faction(s)"
                        )
                        : DiagnosticCheck.problem(
                                "indexes.faction-count",
                                DiagnosticSeverity.ERROR,
                                "Index factions",
                                "manager="
                                        + managerCount
                                        + ", collection="
                                        + factions.size(),
                                "Ne pas reconstruire manuellement: inspecter FactionManager avant correction."
                        )
        );

        Set<String> names =
                new HashSet<String>();

        Set<String> tags =
                new HashSet<String>();

        int duplicateNames = 0;
        int duplicateTags = 0;

        int leaderNull = 0;
        int leaderNotMember = 0;
        int leaderWrongRole = 0;
        int multipleLeaderRoles = 0;

        long claimsFromFactions = 0L;

        for (Faction faction : factions) {
            if (faction == null) {
                continue;
            }

            claimsFromFactions +=
                    Math.max(
                            0,
                            faction.getClaimCount()
                    );

            String name =
                    normalizeKey(
                            faction.getName()
                    );

            if (name != null
                    && !names.add(name)) {
                duplicateNames++;
            }

            String tag =
                    normalizeKey(
                            faction.getTag()
                    );

            if (tag != null
                    && !tags.add(tag)) {
                duplicateTags++;
            }

            UUID leader =
                    faction.getLeader();

            if (leader == null) {
                leaderNull++;
                continue;
            }

            if (!faction.isMember(leader)) {
                leaderNotMember++;
            }

            if (faction.getRole(leader)
                    != FactionRole.LEADER) {
                leaderWrongRole++;
            }

            if (full) {
                int leaderRoles = 0;

                for (UUID memberId
                        : faction.getMembers()) {
                    if (faction.getRole(memberId)
                            == FactionRole.LEADER) {
                        leaderRoles++;
                    }
                }

                if (leaderRoles != 1) {
                    multipleLeaderRoles++;
                }
            }
        }

        int indexedClaims =
                plugin.getClaimManager()
                        .getTotalClaims();

        checks.add(
                claimsFromFactions == indexedClaims
                        ? DiagnosticCheck.of(
                                "indexes.claims",
                                DiagnosticSeverity.OK,
                                "Index claims joueurs",
                                indexedClaims
                                        + " claim(s)"
                        )
                        : DiagnosticCheck.problem(
                                "indexes.claims",
                                DiagnosticSeverity.ERROR,
                                "Index claims joueurs",
                                "factions="
                                        + claimsFromFactions
                                        + ", index="
                                        + indexedClaims,
                                "Un rebuild peut masquer la cause: inspecter d'abord la persistance et les dernières mutations."
                        )
        );

        if (duplicateNames > 0
                || duplicateTags > 0) {
            checks.add(
                    DiagnosticCheck.problem(
                            "indexes.faction-keys",
                            DiagnosticSeverity.ERROR,
                            "Unicité factions",
                            "noms dupliqués="
                                    + duplicateNames
                                    + ", tags dupliqués="
                                    + duplicateTags,
                            "Corriger les doublons avant rename/tag."
                    )
            );
        } else {
            checks.add(
                    DiagnosticCheck.of(
                            "indexes.faction-keys",
                            DiagnosticSeverity.OK,
                            "Unicité factions",
                            "noms/tags uniques"
                    )
            );
        }

        int leaderProblems =
                leaderNull
                        + leaderNotMember
                        + leaderWrongRole
                        + multipleLeaderRoles;

        checks.add(
                leaderProblems == 0
                        ? DiagnosticCheck.of(
                                "indexes.leaders",
                                DiagnosticSeverity.OK,
                                "Invariants leader",
                                factions.size()
                                        + " faction(s) cohérente(s)"
                        )
                        : DiagnosticCheck.problem(
                                "indexes.leaders",
                                DiagnosticSeverity.ERROR,
                                "Invariants leader",
                                "null="
                                        + leaderNull
                                        + ", non-membre="
                                        + leaderNotMember
                                        + ", rôle incorrect="
                                        + leaderWrongRole
                                        + ", cardinalité leader="
                                        + multipleLeaderRoles,
                                "Utiliser les opérations RoleService/transferLeadership; ne pas modifier memberRoles à la main."
                        )
        );

        checkLoadedPlayerMirrors(
                checks,
                factions,
                fPlayerManager
        );

        if (full) {
            checkRelations(
                    checks,
                    factions,
                    factionManager
            );
        }
    }

    private void checkLoadedPlayerMirrors(
            List<DiagnosticCheck> checks,
            List<Faction> factions,
            FPlayerManager manager
    ) {
        int loaded =
                manager.getCacheSize();

        int missingFaction = 0;
        int missingMembership = 0;
        int roleMismatch = 0;

        for (FPlayer fPlayer
                : manager.getAllPlayers()) {
            if (fPlayer == null
                    || !fPlayer.hasFaction()) {
                continue;
            }

            Faction faction =
                    plugin.getFactionManager()
                            .getFaction(
                                    fPlayer.getFactionId()
                            );

            if (faction == null) {
                missingFaction++;
                continue;
            }

            UUID uuid =
                    fPlayer.getUuid();

            if (!faction.isMember(uuid)) {
                missingMembership++;
                continue;
            }

            if (fPlayer.getRole()
                    != faction.getRole(uuid)) {
                roleMismatch++;
            }
        }

        /*
         * Deuxième sens du miroir, uniquement pour les profils déjà chargés.
         * findLoaded() ne déclenche aucune I/O et ne crée rien.
         */
        for (Faction faction : factions) {
            if (faction == null) {
                continue;
            }

            for (UUID memberId
                    : faction.getMembers()) {
                FPlayer fPlayer =
                        manager.findLoaded(
                                memberId
                        );

                if (fPlayer == null) {
                    continue;
                }

                if (!fPlayer.hasFaction()
                        || !faction.getId()
                                .equals(
                                        fPlayer.getFactionId()
                                )) {
                    missingMembership++;
                    continue;
                }

                if (fPlayer.getRole()
                        != faction.getRole(
                                memberId
                        )) {
                    roleMismatch++;
                }
            }
        }

        int totalProblems =
                missingFaction
                        + missingMembership
                        + roleMismatch;

        checks.add(
                totalProblems == 0
                        ? DiagnosticCheck.of(
                                "indexes.loaded-player-mirror",
                                DiagnosticSeverity.OK,
                                "Miroir FPlayer chargé",
                                loaded
                                        + " profil(s) chargé(s), aucune incohérence"
                        )
                        : DiagnosticCheck.problem(
                                "indexes.loaded-player-mirror",
                                DiagnosticSeverity.ERROR,
                                "Miroir FPlayer chargé",
                                "faction absente="
                                        + missingFaction
                                        + ", membership="
                                        + missingMembership
                                        + ", rôle="
                                        + roleMismatch
                                        + " | cache="
                                        + loaded,
                                "Inspecter les opérations membership/role ayant précédé l'incohérence."
                        )
        );
    }

    private void checkRelations(
            List<DiagnosticCheck> checks,
            List<Faction> factions,
            FactionManager manager
    ) {
        int missingTargets = 0;
        int asymmetric = 0;

        Set<String> visited =
                new LinkedHashSet<String>();

        for (Faction faction : factions) {
            if (faction == null) {
                continue;
            }

            for (Map.Entry<String, Relation> entry
                    : faction.getAllRelations()
                            .entrySet()) {
                String targetId =
                        entry.getKey();

                Relation relation =
                        entry.getValue();

                if (targetId == null
                        || relation == null
                        || relation == Relation.NEUTRAL) {
                    continue;
                }

                String pair =
                        faction.getId()
                                .compareTo(targetId) <= 0
                                ? faction.getId()
                                        + "|"
                                        + targetId
                                : targetId
                                        + "|"
                                        + faction.getId();

                if (!visited.add(pair)) {
                    continue;
                }

                Faction target =
                        manager.getFaction(
                                targetId
                        );

                if (target == null
                        || target.isSystemFaction()) {
                    missingTargets++;
                    continue;
                }

                Relation reverse =
                        target.getRelationTo(
                                faction
                        );

                if (reverse != relation) {
                    asymmetric++;
                }
            }
        }

        if (missingTargets == 0
                && asymmetric == 0) {
            checks.add(
                    DiagnosticCheck.of(
                            "indexes.relations",
                            DiagnosticSeverity.OK,
                            "Relations",
                            visited.size()
                                    + " paire(s) vérifiée(s)"
                    )
            );
        } else {
            checks.add(
                    DiagnosticCheck.problem(
                            "indexes.relations",
                            DiagnosticSeverity.ERROR,
                            "Relations",
                            "cible absente="
                                    + missingTargets
                                    + ", asymétrique="
                                    + asymmetric,
                            "Corriger via RelationService/commandes relationnelles, jamais par édition JSON directe."
                    )
            );
        }
    }

    // ============================================================
    // Dynamic Global Zones
    // ============================================================

    private void checkZones(
            List<DiagnosticCheck> checks,
            boolean full
    ) {
        if (plugin.getClaimManager() == null
                || plugin.getClaimManager()
                        .getZoneService() == null) {
            checks.add(
                    DiagnosticCheck.problem(
                            "zones.service",
                            DiagnosticSeverity.ERROR,
                            "Global Zones",
                            "ZoneService est indisponible.",
                            "Vérifier l'initialisation de ClaimManager/ZoneService."
                    )
            );
            return;
        }

        me.krunsh.kfaction.services.ZoneService service =
                plugin.getClaimManager()
                        .getZoneService();

        int definitions =
                service.getDefinitions()
                        .size();

        int chunks =
                service.getTotalZoneChunks();

        checks.add(
                DiagnosticCheck.of(
                        "zones.summary",
                        definitions >= 2
                                ? DiagnosticSeverity.OK
                                : DiagnosticSeverity.WARNING,
                        "Global Zones",
                        "definitions="
                                + definitions
                                + ", chunks="
                                + chunks
                                + ", payloadSchema=2"
                )
        );

        List<String> configIssues =
                service.getConfigurationIssues();

        checks.add(
                DiagnosticCheck.of(
                        "zones.configuration",
                        configIssues.isEmpty()
                                ? DiagnosticSeverity.OK
                                : DiagnosticSeverity.WARNING,
                        "Configuration zones",
                        configIssues.isEmpty()
                                ? "Aucune incohérence détectée."
                                : configIssues.size()
                                        + " problème(s): "
                                        + configIssues
                )
        );

        java.util.Set<String> orphans =
                service.getOrphanZoneIds();

        checks.add(
                orphans.isEmpty()
                        ? DiagnosticCheck.of(
                                "zones.orphans",
                                DiagnosticSeverity.OK,
                                "Zones orphelines",
                                "Aucune assignation sans définition."
                        )
                        : DiagnosticCheck.problem(
                                "zones.orphans",
                                DiagnosticSeverity.ERROR,
                                "Zones orphelines",
                                orphans.toString(),
                                "Restaurer zones.<id> dans config.yml ou retirer explicitement ces chunks."
                        )
        );

        if (!full) {
            return;
        }

        for (me.krunsh.kfaction.zones.ZoneDefinition definition
                : service.getDefinitionList()) {
            checks.add(
                    DiagnosticCheck.of(
                            "zones.definition."
                                    + definition.getId(),
                            definition.isConfigured()
                                    ? DiagnosticSeverity.OK
                                    : DiagnosticSeverity.INFO,
                            "Zone "
                                    + definition.getId(),
                            "display="
                                    + definition.getDisplayName()
                                    + ", chunks="
                                    + service.count(
                                            definition.getId()
                                    )
                                    + ", pvp="
                                    + definition.isPvpAllowed()
                                    + ", policy="
                                    + definition.getDefaultPolicy()
                                            .name()
                    )
            );
        }
    }

    // ============================================================
    // Progression
    // ============================================================

    private void checkProgression(
            List<DiagnosticCheck> checks,
            boolean full
    ) {
        QuestManager manager =
                plugin.getQuestManager();

        if (manager == null) {
            checks.add(
                    DiagnosticCheck.problem(
                            "progression.manager",
                            DiagnosticSeverity.ERROR,
                            "Progression",
                            "QuestManager est null.",
                            "Vérifier le bootstrap Progression V2."
                    )
            );
            return;
        }

        if (!manager.isEnabled()) {
            checks.add(
                    DiagnosticCheck.of(
                            "progression.enabled",
                            DiagnosticSeverity.WARNING,
                            "Progression",
                            "progression.yml est désactivé ou aucun snapshot valide n'est actif."
                    )
            );
            return;
        }

        checks.add(
                DiagnosticCheck.of(
                        "progression.enabled",
                        DiagnosticSeverity.OK,
                        "Progression",
                        manager.getActiveQuestsCount()
                                + " quête(s) fixe(s) chargée(s)"
                )
        );

        List<ValidationIssue> issues =
                manager.getLastValidationIssues();

        int validationErrors = 0;
        int validationWarnings = 0;

        if (issues != null) {
            for (ValidationIssue issue : issues) {
                if (issue == null) {
                    continue;
                }

                if (issue.getSeverity()
                        == ValidationIssue.Severity.ERROR) {
                    validationErrors++;
                } else {
                    validationWarnings++;
                }
            }
        }

        DiagnosticSeverity validationSeverity =
                validationErrors > 0
                        ? DiagnosticSeverity.ERROR
                        : validationWarnings > 0
                                ? DiagnosticSeverity.WARNING
                                : DiagnosticSeverity.OK;

        checks.add(
                DiagnosticCheck.of(
                        "progression.validation",
                        validationSeverity,
                        "Validation progression",
                        "errors="
                                + validationErrors
                                + ", warnings="
                                + validationWarnings
                )
        );

        int schemaOutdated = 0;
        int levelMismatch = 0;
        int pendingRewards = 0;
        int pendingTransitions = 0;

        int factions =
                0;

        for (Faction faction
                : plugin.getFactionManager()
                        .getPlayerFactions()) {
            if (faction == null) {
                continue;
            }

            factions++;

            FactionProgressState state =
                    faction.getProgressionState();

            if (state == null) {
                levelMismatch++;
                continue;
            }

            if (state.getSchemaVersion()
                    < FactionProgressState
                            .CURRENT_SCHEMA_VERSION) {
                schemaOutdated++;
            }

            if (state.getLevelStarted()
                    != faction.getLevel()) {
                levelMismatch++;
            }

            if (!state.getPendingRewards()
                    .isEmpty()) {
                pendingRewards++;
            }

            if (state.getPendingTransition()
                    != null) {
                pendingTransitions++;
            }
        }

        if (schemaOutdated > 0
                || levelMismatch > 0) {
            checks.add(
                    DiagnosticCheck.problem(
                            "progression.state-integrity",
                            DiagnosticSeverity.ERROR,
                            "États progression",
                            "factions="
                                    + factions
                                    + ", schema ancien="
                                    + schemaOutdated
                                    + ", niveau mismatch="
                                    + levelMismatch,
                            "Ne pas appeler de replay reward; inspecter la faction et les sauvegardes avant réparation."
                    )
            );
        } else {
            checks.add(
                    DiagnosticCheck.of(
                            "progression.state-integrity",
                            DiagnosticSeverity.OK,
                            "États progression",
                            factions
                                    + " faction(s), schema="
                                    + FactionProgressState
                                            .CURRENT_SCHEMA_VERSION
                    )
            );
        }

        if (pendingRewards > 0
                || pendingTransitions > 0) {
            checks.add(
                    DiagnosticCheck.problem(
                            "progression.pending",
                            DiagnosticSeverity.WARNING,
                            "Progression suspendue",
                            "pending rewards="
                                    + pendingRewards
                                    + ", pending transitions="
                                    + pendingTransitions,
                            "Inspecter les reward keys; aucune récompense ne doit être rejouée automatiquement."
                    )
            );
        } else {
            checks.add(
                    DiagnosticCheck.of(
                            "progression.pending",
                            DiagnosticSeverity.OK,
                            "Progression suspendue",
                            "aucune faction bloquée"
                    )
            );
        }

        if (full) {
            List<ValidationIssue> candidate =
                    manager.validateCandidate();

            int errors = 0;
            int warnings = 0;

            if (candidate != null) {
                for (ValidationIssue issue
                        : candidate) {
                    if (issue == null) {
                        continue;
                    }

                    if (issue.getSeverity()
                            == ValidationIssue.Severity.ERROR) {
                        errors++;
                    } else {
                        warnings++;
                    }
                }
            }

            checks.add(
                    DiagnosticCheck.of(
                            "progression.candidate",
                            errors > 0
                                    ? DiagnosticSeverity.ERROR
                                    : warnings > 0
                                            ? DiagnosticSeverity.WARNING
                                            : DiagnosticSeverity.OK,
                            "progression.yml sur disque",
                            "errors="
                                    + errors
                                    + ", warnings="
                                    + warnings
                                    + " | lecture seule"
                    )
            );
        }
    }

    // ============================================================
    // Utils
    // ============================================================

    private static String normalizeKey(
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

    private static String formatBytes(
            long bytes
    ) {
        if (bytes < 1024L) {
            return bytes + " B";
        }

        double kib =
                bytes / 1024.0D;

        if (kib < 1024.0D) {
            return String.format(
                    Locale.US,
                    "%.1f KiB",
                    kib
            );
        }

        double mib =
                kib / 1024.0D;

        if (mib < 1024.0D) {
            return String.format(
                    Locale.US,
                    "%.1f MiB",
                    mib
            );
        }

        return String.format(
                Locale.US,
                "%.2f GiB",
                mib / 1024.0D
        );
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

    private static String safe(
            String value
    ) {
        return value != null
                ? value
                : "";
    }
}
