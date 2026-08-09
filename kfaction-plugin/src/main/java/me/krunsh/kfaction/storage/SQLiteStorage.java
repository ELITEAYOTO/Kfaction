package me.krunsh.kfaction.storage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;

/**
 * Backend SQLite V2.
 *
 * Architecture actuelle :
 * - SQLite devient la source de vérité persistante ;
 * - les payloads de domaine restent au format JSON V2 dans les tables ;
 * - les écritures de batch sont transactionnelles ;
 * - WAL + busy_timeout ;
 * - migration automatique et atomique des anciens fichiers JSON ;
 * - sauvegarde du fichier DB au démarrage.
 *
 * Le schéma reste volontairement petit pour ce premier lot SQLite. Les futures
 * tables spécialisées (audit, bans, claim groups, zones...) pourront évoluer
 * sans réécrire le codec domaine.
 */
public final class SQLiteStorage implements Storage {

    private static final int CURRENT_DB_SCHEMA_VERSION = 3;

    private static final String META_LEGACY_MIGRATION_STATUS =
            "legacy_flatfile_migration_status";

    private static final String TABLE_META = "kf_meta";
    private static final String TABLE_FACTIONS = "kf_factions";
    private static final String TABLE_PLAYERS = "kf_players";
    private static final String TABLE_GLOBAL_ZONES = "kf_global_zones";
    private static final String TABLE_GRACE_STATE = "kf_grace_state";

    private final Kfaction plugin;
    private final JsonStorageCodec codec;
    private final Object dbLock;

    private Connection connection;
    private File databaseFile;
    private File backupFolder;

    private volatile boolean connected;

    private int busyTimeoutMs;
    private boolean migrateFlatfile;
    private boolean backupOnStartup;
    private int backupKeep;
    private String synchronousMode;

    public SQLiteStorage(Kfaction plugin) {
        this.plugin = plugin;
        this.codec = new JsonStorageCodec();
        this.dbLock = new Object();
        this.connected = false;
    }

    // ============================================================
    // Lifecycle
    // ============================================================

    @Override
    public void initialize() {
        synchronized (dbLock) {
            if (connected) {
                return;
            }

            loadSettings();
            prepareFolders();

            boolean databaseAlreadyExisted =
                    databaseFile.isFile()
                            && databaseFile.length() > 0L;

            try {
                /*
                 * On référence volontairement le driver par String :
                 * aucune classe org.sqlite n'entre dans notre API publique.
                 */
                Class.forName(
                        "org.sqlite.JDBC",
                        true,
                        plugin.getClass().getClassLoader()
                );

                connection =
                        DriverManager.getConnection(
                                "jdbc:sqlite:"
                                        + databaseFile.getAbsolutePath()
                        );

                configureConnection();

                /*
                 * Avant toute évolution future de schéma, garder une copie
                 * cohérente de la DB précédente.
                 */
                if (databaseAlreadyExisted
                        && backupOnStartup) {
                    checkpointWal("FULL");
                    createStartupBackup();
                }

                migrateDatabaseSchema();

                if (migrateFlatfile) {
                    migrateLegacyFlatFilesIfNeeded();
                }

                connected = true;

                plugin.getLogger().info(
                        "SQLiteStorage initialisé: "
                                + databaseFile.getAbsolutePath()
                                + " (schema="
                                + CURRENT_DB_SCHEMA_VERSION
                                + ", WAL)"
                );
            } catch (Exception exception) {
                connected = false;
                closeQuietly();

                throw new IllegalStateException(
                        "Impossible d'initialiser SQLite: "
                                + exception.getMessage(),
                        exception
                );
            }
        }
    }

    @Override
    public void shutdown() {
        synchronized (dbLock) {
            if (connection == null) {
                connected = false;
                return;
            }

            try {
                checkpointWal("TRUNCATE");
            } catch (Exception exception) {
                plugin.getLogger().warning(
                        "Checkpoint SQLite au shutdown échoué: "
                                + exception.getMessage()
                );
            }

            closeQuietly();
            connected = false;
        }
    }

    private void loadSettings() {
        String fileName =
                plugin.getConfigManager().getString(
                        "storage.sqlite.file",
                        "kfaction.db"
                );

        if (!isSafeDatabaseFileName(fileName)) {
            throw new IllegalArgumentException(
                    "storage.sqlite.file invalide: "
                            + fileName
            );
        }

        busyTimeoutMs =
                clamp(
                        plugin.getConfigManager().getInt(
                                "storage.sqlite.busy-timeout-ms",
                                5000
                        ),
                        1000,
                        60000
                );

        migrateFlatfile =
                plugin.getConfigManager().getBoolean(
                        "storage.sqlite.migrate-flatfile",
                        true
                );

        backupOnStartup =
                plugin.getConfigManager().getBoolean(
                        "storage.sqlite.backup-on-startup",
                        true
                );

        backupKeep =
                clamp(
                        plugin.getConfigManager().getInt(
                                "storage.sqlite.backup-keep",
                                5
                        ),
                        1,
                        50
                );

        String configuredSynchronous =
                plugin.getConfigManager().getString(
                        "storage.sqlite.synchronous",
                        "NORMAL"
                );

        synchronousMode =
                normalizeSynchronous(
                        configuredSynchronous
                );
    }

    private void prepareFolders() {
        File pluginFolder =
                plugin.getDataFolder();

        ensureDirectory(pluginFolder);

        databaseFile =
                new File(
                        pluginFolder,
                        plugin.getConfigManager().getString(
                                "storage.sqlite.file",
                                "kfaction.db"
                        )
                );

        backupFolder =
                new File(
                        pluginFolder,
                        "backups/storage"
                );

        if (backupOnStartup) {
            ensureDirectory(backupFolder);
        }
    }

    private void configureConnection()
            throws SQLException {
        executePragma(
                "PRAGMA foreign_keys = ON"
        );

        executePragma(
                "PRAGMA busy_timeout = "
                        + busyTimeoutMs
        );

        String journalMode =
                querySingleString(
                        "PRAGMA journal_mode = WAL"
                );

        if (journalMode == null
                || !"wal".equalsIgnoreCase(
                        journalMode.trim()
                )) {
            throw new SQLException(
                    "SQLite n'a pas activé WAL "
                            + "(retour="
                            + journalMode
                            + ")"
            );
        }

        executePragma(
                "PRAGMA synchronous = "
                        + synchronousMode
        );

        executePragma(
                "PRAGMA temp_store = MEMORY"
        );
    }

    // ============================================================
    // Schéma
    // ============================================================

    private void migrateDatabaseSchema()
            throws SQLException {
        int current =
                getDatabaseSchemaVersion();

        if (current > CURRENT_DB_SCHEMA_VERSION) {
            throw new SQLException(
                    "DB créée par une version plus récente "
                            + "(schema="
                            + current
                            + ", supporté="
                            + CURRENT_DB_SCHEMA_VERSION
                            + ")"
            );
        }

        while (current < CURRENT_DB_SCHEMA_VERSION) {
            if (current == 0) {
                migrateSchema0To1();
                current = 1;
                continue;
            }

            if (current == 1) {
                migrateSchema1To2();
                current = 2;
                continue;
            }

            if (current == 2) {
                migrateSchema2To3();
                current = 3;
                continue;
            }

            throw new SQLException(
                    "Migration SQLite inconnue depuis schema "
                            + current
            );
        }
    }

    private void migrateSchema0To1()
            throws SQLException {
        boolean oldAutoCommit =
                connection.getAutoCommit();

        connection.setAutoCommit(false);

        try (Statement statement =
                     connection.createStatement()) {

            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS "
                            + TABLE_META
                            + " ("
                            + "meta_key TEXT PRIMARY KEY NOT NULL,"
                            + "meta_value TEXT NOT NULL"
                            + ")"
            );

            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS "
                            + TABLE_FACTIONS
                            + " ("
                            + "id TEXT PRIMARY KEY NOT NULL,"
                            + "payload_json TEXT NOT NULL,"
                            + "payload_schema INTEGER NOT NULL,"
                            + "updated_at INTEGER NOT NULL"
                            + ")"
            );

            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS "
                            + TABLE_PLAYERS
                            + " ("
                            + "uuid TEXT PRIMARY KEY NOT NULL,"
                            + "payload_json TEXT NOT NULL,"
                            + "payload_schema INTEGER NOT NULL,"
                            + "updated_at INTEGER NOT NULL"
                            + ")"
            );

            statement.execute(
                    "PRAGMA user_version = 1"
            );

            putMeta(
                    META_LEGACY_MIGRATION_STATUS,
                    "pending"
            );

            connection.commit();
        } catch (SQLException exception) {
            rollbackQuietly();
            throw exception;
        } finally {
            connection.setAutoCommit(oldAutoCommit);
        }

        plugin.getLogger().info(
                "Migration SQLite schema 0 -> 1 terminée"
        );
    }

    private void migrateSchema1To2()
            throws SQLException {
        boolean oldAutoCommit =
                connection.getAutoCommit();

        connection.setAutoCommit(false);

        try (Statement statement =
                     connection.createStatement()) {

            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS "
                            + TABLE_GLOBAL_ZONES
                            + " ("
                            + "id TEXT PRIMARY KEY NOT NULL,"
                            + "payload_json TEXT NOT NULL,"
                            + "payload_schema INTEGER NOT NULL,"
                            + "updated_at INTEGER NOT NULL"
                            + ")"
            );

            statement.execute(
                    "PRAGMA user_version = 2"
            );

            connection.commit();
        } catch (SQLException exception) {
            rollbackQuietly();
            throw exception;
        } finally {
            connection.setAutoCommit(oldAutoCommit);
        }

        plugin.getLogger().info(
                "Migration SQLite schema 1 -> 2 terminée "
                        + "(Global Zones V2)"
        );
    }

    private void migrateSchema2To3()
            throws SQLException {
        boolean oldAutoCommit =
                connection.getAutoCommit();

        connection.setAutoCommit(false);

        try (Statement statement =
                     connection.createStatement()) {

            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS "
                            + TABLE_GRACE_STATE
                            + " ("
                            + "id TEXT PRIMARY KEY NOT NULL,"
                            + "payload_json TEXT NOT NULL,"
                            + "payload_schema INTEGER NOT NULL,"
                            + "updated_at INTEGER NOT NULL"
                            + ")"
            );

            statement.execute(
                    "PRAGMA user_version = 3"
            );

            connection.commit();
        } catch (SQLException exception) {
            rollbackQuietly();
            throw exception;
        } finally {
            connection.setAutoCommit(oldAutoCommit);
        }

        plugin.getLogger().info(
                "Migration SQLite schema 2 -> 3 terminée "
                        + "(Grace Period V2)"
        );
    }

    private int getDatabaseSchemaVersion()
            throws SQLException {
        try (Statement statement =
                     connection.createStatement();
             ResultSet result =
                     statement.executeQuery(
                             "PRAGMA user_version"
                     )) {

            return result.next()
                    ? result.getInt(1)
                    : 0;
        }
    }

    // ============================================================
    // Migration FlatFile -> SQLite
    // ============================================================

    private void migrateLegacyFlatFilesIfNeeded()
            throws Exception {
        String status =
                getMeta(
                        META_LEGACY_MIGRATION_STATUS
                );

        if (status != null
                && status.startsWith("completed")) {
            return;
        }

        long sqliteRows =
                countRows(TABLE_FACTIONS)
                        + countRows(TABLE_PLAYERS);

        if (sqliteRows > 0L) {
            putMeta(
                    META_LEGACY_MIGRATION_STATUS,
                    "completed:skipped-non-empty:"
                            + System.currentTimeMillis()
            );

            plugin.getLogger().info(
                    "Migration JSON -> SQLite ignorée: "
                            + "la DB contient déjà "
                            + sqliteRows
                            + " enregistrements."
            );
            return;
        }

        File legacyFactionFolder =
                new File(
                        plugin.getDataFolder(),
                        "data/factions"
                );

        File legacyPlayerFolder =
                new File(
                        plugin.getDataFolder(),
                        "data/players"
                );

        File[] factionFiles =
                listJsonFiles(
                        legacyFactionFolder
                );

        File[] playerFiles =
                listJsonFiles(
                        legacyPlayerFolder
                );

        if (factionFiles.length == 0
                && playerFiles.length == 0) {
            putMeta(
                    META_LEGACY_MIGRATION_STATUS,
                    "completed:no-legacy-data:"
                            + System.currentTimeMillis()
            );
            return;
        }

        List<LegacyRecord> factions =
                validateLegacyFactions(
                        factionFiles
                );

        List<LegacyRecord> players =
                validateLegacyPlayers(
                        playerFiles
                );

        boolean oldAutoCommit =
                connection.getAutoCommit();

        connection.setAutoCommit(false);

        try {
            long now =
                    System.currentTimeMillis();

            try (PreparedStatement factionStatement =
                         connection.prepareStatement(
                                 "INSERT OR REPLACE INTO "
                                         + TABLE_FACTIONS
                                         + " (id, payload_json, "
                                         + "payload_schema, updated_at) "
                                         + "VALUES (?, ?, ?, ?)"
                         );
                 PreparedStatement playerStatement =
                         connection.prepareStatement(
                                 "INSERT OR REPLACE INTO "
                                         + TABLE_PLAYERS
                                         + " (uuid, payload_json, "
                                         + "payload_schema, updated_at) "
                                         + "VALUES (?, ?, ?, ?)"
                         )) {

                for (LegacyRecord record : factions) {
                    bindRecord(
                            factionStatement,
                            record,
                            now
                    );
                    factionStatement.addBatch();
                }

                for (LegacyRecord record : players) {
                    bindRecord(
                            playerStatement,
                            record,
                            now
                    );
                    playerStatement.addBatch();
                }

                if (!factions.isEmpty()) {
                    factionStatement.executeBatch();
                }

                if (!players.isEmpty()) {
                    playerStatement.executeBatch();
                }
            }

            putMeta(
                    META_LEGACY_MIGRATION_STATUS,
                    "completed:"
                            + System.currentTimeMillis()
                            + ":factions="
                            + factions.size()
                            + ":players="
                            + players.size()
            );

            connection.commit();

            plugin.getLogger().info(
                    "Migration JSON -> SQLite terminée "
                            + "(factions="
                            + factions.size()
                            + ", joueurs="
                            + players.size()
                            + "). "
                            + "Les JSON source sont conservés."
            );
        } catch (Exception exception) {
            rollbackQuietly();

            throw new IllegalStateException(
                    "Migration JSON -> SQLite annulée "
                            + "sans écriture partielle: "
                            + exception.getMessage(),
                    exception
            );
        } finally {
            connection.setAutoCommit(oldAutoCommit);
        }
    }

    private List<LegacyRecord> validateLegacyFactions(
            File[] files
    ) throws Exception {
        List<LegacyRecord> records =
                new ArrayList<LegacyRecord>();

        for (File file : files) {
            String payload =
                    readUtf8Required(file);

            Faction faction =
                    codec.decodeFaction(payload);

            if (faction == null) {
                throw new IllegalStateException(
                        "Faction JSON invalide: "
                                + file.getName()
                );
            }

            String fileId =
                    stripJsonSuffix(
                            file.getName()
                    );

            if (!fileId.equals(
                    faction.getId()
            )) {
                throw new IllegalStateException(
                        "ID faction incohérent dans "
                                + file.getName()
                                + " (JSON="
                                + faction.getId()
                                + ")"
                );
            }

            records.add(
                    new LegacyRecord(
                            faction.getId(),
                            payload,
                            detectPayloadSchema(payload)
                    )
            );
        }

        return records;
    }

    private List<LegacyRecord> validateLegacyPlayers(
            File[] files
    ) throws Exception {
        List<LegacyRecord> records =
                new ArrayList<LegacyRecord>();

        for (File file : files) {
            String payload =
                    readUtf8Required(file);

            FPlayer fPlayer =
                    codec.decodeFPlayer(payload);

            if (fPlayer == null
                    || fPlayer.getUuid() == null) {
                throw new IllegalStateException(
                        "FPlayer JSON invalide: "
                                + file.getName()
                );
            }

            String fileId =
                    stripJsonSuffix(
                            file.getName()
                    );

            UUID fileUuid;

            try {
                fileUuid =
                        UUID.fromString(fileId);
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException(
                        "Nom de fichier FPlayer invalide: "
                                + file.getName()
                );
            }

            if (!fileUuid.equals(
                    fPlayer.getUuid()
            )) {
                throw new IllegalStateException(
                        "UUID joueur incohérent dans "
                                + file.getName()
                                + " (JSON="
                                + fPlayer.getUuid()
                                + ")"
                );
            }

            records.add(
                    new LegacyRecord(
                            fPlayer.getUuid().toString(),
                            payload,
                            detectPayloadSchema(payload)
                    )
            );
        }

        return records;
    }

    // ============================================================
    // Lecture
    // ============================================================

    @Override
    public void loadFactions(
            Consumer<Faction> consumer
    ) {
        if (consumer == null) {
            return;
        }

        List<String> payloads =
                new ArrayList<String>();

        synchronized (dbLock) {
            ensureConnected();

            try (PreparedStatement statement =
                         connection.prepareStatement(
                                 "SELECT payload_json FROM "
                                         + TABLE_FACTIONS
                                         + " ORDER BY id"
                         );
                 ResultSet result =
                         statement.executeQuery()) {

                while (result.next()) {
                    payloads.add(
                            result.getString(1)
                    );
                }
            } catch (SQLException exception) {
                throw new IllegalStateException(
                        "Lecture factions SQLite impossible",
                        exception
                );
            }
        }

        int loaded = 0;

        for (String payload : payloads) {
            try {
                Faction faction =
                        codec.decodeFaction(payload);

                if (faction != null) {
                    consumer.accept(faction);
                    loaded++;
                }
            } catch (Exception exception) {
                plugin.getLogger().severe(
                        "Faction SQLite corrompue/non lisible: "
                                + exception.getMessage()
                );
            }
        }

        plugin.getLogger().info(
                "Chargé "
                        + loaded
                        + " factions depuis SQLite"
        );
    }

    @Override
    public Faction loadFaction(String factionId) {
        if (factionId == null) {
            return null;
        }

        String payload =
                loadPayloadById(
                        TABLE_FACTIONS,
                        "id",
                        factionId
                );

        if (payload == null) {
            return null;
        }

        try {
            return codec.decodeFaction(payload);
        } catch (Exception exception) {
            plugin.getLogger().severe(
                    "Faction SQLite "
                            + factionId
                            + " non lisible: "
                            + exception.getMessage()
            );
            return null;
        }
    }

    @Override
    public void loadFPlayers(
            Consumer<FPlayer> consumer
    ) {
        if (consumer == null) {
            return;
        }

        List<String> payloads =
                new ArrayList<String>();

        synchronized (dbLock) {
            ensureConnected();

            try (PreparedStatement statement =
                         connection.prepareStatement(
                                 "SELECT payload_json FROM "
                                         + TABLE_PLAYERS
                                         + " ORDER BY uuid"
                         );
                 ResultSet result =
                         statement.executeQuery()) {

                while (result.next()) {
                    payloads.add(
                            result.getString(1)
                    );
                }
            } catch (SQLException exception) {
                throw new IllegalStateException(
                        "Lecture joueurs SQLite impossible",
                        exception
                );
            }
        }

        for (String payload : payloads) {
            try {
                FPlayer fPlayer =
                        codec.decodeFPlayer(payload);

                if (fPlayer != null) {
                    consumer.accept(fPlayer);
                }
            } catch (Exception exception) {
                plugin.getLogger().severe(
                        "FPlayer SQLite corrompu/non lisible: "
                                + exception.getMessage()
                );
            }
        }
    }

    @Override
    public FPlayer loadFPlayer(String uuid) {
        if (uuid == null) {
            return null;
        }

        String payload =
                loadPayloadById(
                        TABLE_PLAYERS,
                        "uuid",
                        uuid
                );

        if (payload == null) {
            return null;
        }

        try {
            return codec.decodeFPlayer(payload);
        } catch (Exception exception) {
            plugin.getLogger().severe(
                    "FPlayer SQLite "
                            + uuid
                            + " non lisible: "
                            + exception.getMessage()
            );
            return null;
        }
    }

    private String loadPayloadById(
            String table,
            String idColumn,
            String id
    ) {
        synchronized (dbLock) {
            ensureConnected();

            String sql =
                    "SELECT payload_json FROM "
                            + table
                            + " WHERE "
                            + idColumn
                            + " = ?";

            try (PreparedStatement statement =
                         connection.prepareStatement(sql)) {

                statement.setString(1, id);

                try (ResultSet result =
                             statement.executeQuery()) {
                    return result.next()
                            ? result.getString(1)
                            : null;
                }
            } catch (SQLException exception) {
                throw new IllegalStateException(
                        "Lecture SQLite impossible pour "
                                + id,
                        exception
                );
            }
        }
    }

    @Override
    public String loadGlobalZonesPayload() {
        return loadPayloadById(
                TABLE_GLOBAL_ZONES,
                "id",
                "global"
        );
    }

    @Override
    public String loadGraceStatePayload() {
        return loadPayloadById(
                TABLE_GRACE_STATE,
                "id",
                "global"
        );
    }

    // ============================================================
    // Écriture transactionnelle
    // ============================================================

    @Override
    public boolean writeSnapshot(
            StorageSnapshot snapshot
    ) {
        if (snapshot == null) {
            return false;
        }

        return writeSnapshots(
                Collections.singletonList(
                        snapshot
                )
        );
    }

    @Override
    public boolean writeSnapshots(
            Collection<StorageSnapshot> snapshots
    ) {
        if (snapshots == null
                || snapshots.isEmpty()) {
            return true;
        }

        synchronized (dbLock) {
            ensureConnected();

            boolean oldAutoCommit;

            try {
                oldAutoCommit =
                        connection.getAutoCommit();
            } catch (SQLException exception) {
                return false;
            }

            try {
                connection.setAutoCommit(false);

                try (PreparedStatement factionStatement =
                             connection.prepareStatement(
                                     "INSERT OR REPLACE INTO "
                                             + TABLE_FACTIONS
                                             + " (id, payload_json, "
                                             + "payload_schema, updated_at) "
                                             + "VALUES (?, ?, ?, ?)"
                             );
                     PreparedStatement playerStatement =
                             connection.prepareStatement(
                                     "INSERT OR REPLACE INTO "
                                             + TABLE_PLAYERS
                                             + " (uuid, payload_json, "
                                             + "payload_schema, updated_at) "
                                             + "VALUES (?, ?, ?, ?)"
                             );
                     PreparedStatement zoneStatement =
                             connection.prepareStatement(
                                     "INSERT OR REPLACE INTO "
                                             + TABLE_GLOBAL_ZONES
                                             + " (id, payload_json, "
                                             + "payload_schema, updated_at) "
                                             + "VALUES (?, ?, ?, ?)"
                             );
                     PreparedStatement graceStatement =
                             connection.prepareStatement(
                                     "INSERT OR REPLACE INTO "
                                             + TABLE_GRACE_STATE
                                             + " (id, payload_json, "
                                             + "payload_schema, updated_at) "
                                             + "VALUES (?, ?, ?, ?)"
                             )) {

                    boolean hasFactionBatch = false;
                    boolean hasPlayerBatch = false;
                    boolean hasZoneBatch = false;
                    boolean hasGraceBatch = false;

                    for (StorageSnapshot snapshot : snapshots) {
                        if (snapshot == null) {
                            continue;
                        }

                        PreparedStatement target;

                        switch (snapshot.getEntityType()) {
                            case FACTION:
                                target = factionStatement;
                                hasFactionBatch = true;
                                break;

                            case FPLAYER:
                                target = playerStatement;
                                hasPlayerBatch = true;
                                break;

                            case GLOBAL_ZONES:
                                target = zoneStatement;
                                hasZoneBatch = true;
                                break;

                            case GRACE_STATE:
                                target = graceStatement;
                                hasGraceBatch = true;
                                break;

                            default:
                                continue;
                        }

                        target.setString(
                                1,
                                snapshot.getEntityId()
                        );
                        target.setString(
                                2,
                                snapshot.getPayloadJson()
                        );
                        target.setInt(
                                3,
                                snapshot.getSchemaVersion()
                        );
                        target.setLong(
                                4,
                                snapshot.getCapturedAt()
                        );
                        target.addBatch();
                    }

                    if (hasFactionBatch) {
                        factionStatement.executeBatch();
                    }

                    if (hasPlayerBatch) {
                        playerStatement.executeBatch();
                    }

                    if (hasZoneBatch) {
                        zoneStatement.executeBatch();
                    }

                    if (hasGraceBatch) {
                        graceStatement.executeBatch();
                    }
                }

                connection.commit();
                return true;
            } catch (Exception exception) {
                rollbackQuietly();

                plugin.getLogger().severe(
                        "Transaction SQLite annulée: "
                                + exception.getMessage()
                );

                return false;
            } finally {
                try {
                    connection.setAutoCommit(
                            oldAutoCommit
                    );
                } catch (SQLException ignored) {
                    // La prochaine opération détectera la connexion cassée.
                }
            }
        }
    }

    @Override
    @Deprecated
    public void saveFaction(Faction faction) {
        saveFactionChecked(faction);
    }

    @Override
    @Deprecated
    public boolean saveFactionChecked(
            Faction faction
    ) {
        if (faction == null
                || faction.isSystemFaction()) {
            return false;
        }

        try {
            return writeSnapshot(
                    codec.captureFaction(faction)
            );
        } catch (Exception exception) {
            plugin.getLogger().severe(
                    "Snapshot faction SQLite impossible "
                            + faction.getId()
                            + ": "
                            + exception.getMessage()
            );
            return false;
        }
    }

    @Override
    @Deprecated
    public void saveFPlayer(FPlayer fPlayer) {
        if (fPlayer == null) {
            return;
        }

        try {
            writeSnapshot(
                    codec.captureFPlayer(fPlayer)
            );
        } catch (Exception exception) {
            plugin.getLogger().severe(
                    "Snapshot FPlayer SQLite impossible "
                            + fPlayer.getUuid()
                            + ": "
                            + exception.getMessage()
            );
        }
    }

    // ============================================================
    // Suppressions
    // ============================================================

    @Override
    public void deleteFaction(String factionId) {
        deleteById(
                TABLE_FACTIONS,
                "id",
                factionId
        );
    }

    @Override
    public void deleteFPlayer(String uuid) {
        deleteById(
                TABLE_PLAYERS,
                "uuid",
                uuid
        );
    }

    private void deleteById(
            String table,
            String idColumn,
            String id
    ) {
        if (id == null) {
            return;
        }

        synchronized (dbLock) {
            ensureConnected();

            String sql =
                    "DELETE FROM "
                            + table
                            + " WHERE "
                            + idColumn
                            + " = ?";

            try (PreparedStatement statement =
                         connection.prepareStatement(sql)) {
                statement.setString(1, id);
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new IllegalStateException(
                        "Suppression SQLite impossible pour "
                                + id,
                        exception
                );
            }
        }
    }

    // ============================================================
    // Metadata / backups
    // ============================================================

    private String getMeta(String key)
            throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement(
                             "SELECT meta_value FROM "
                                     + TABLE_META
                                     + " WHERE meta_key = ?"
                     )) {
            statement.setString(1, key);

            try (ResultSet result =
                         statement.executeQuery()) {
                return result.next()
                        ? result.getString(1)
                        : null;
            }
        }
    }

    private void putMeta(
            String key,
            String value
    ) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement(
                             "INSERT OR REPLACE INTO "
                                     + TABLE_META
                                     + " (meta_key, meta_value) "
                                     + "VALUES (?, ?)"
                     )) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        }
    }

    private long countRows(String table)
            throws SQLException {
        try (Statement statement =
                     connection.createStatement();
             ResultSet result =
                     statement.executeQuery(
                             "SELECT COUNT(*) FROM "
                                     + table
                     )) {
            return result.next()
                    ? result.getLong(1)
                    : 0L;
        }
    }

    private void createStartupBackup()
            throws IOException {
        if (databaseFile == null
                || !databaseFile.isFile()
                || databaseFile.length() <= 0L) {
            return;
        }

        ensureDirectory(backupFolder);

        String timestamp =
                new SimpleDateFormat(
                        "yyyyMMdd-HHmmss",
                        Locale.ROOT
                ).format(new Date());

        File backup =
                new File(
                        backupFolder,
                        "kfaction-"
                                + timestamp
                                + ".db"
                );

        Files.copy(
                databaseFile.toPath(),
                backup.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES
        );

        pruneBackups();

        plugin.getLogger().info(
                "Backup SQLite créé: "
                        + backup.getName()
        );
    }

    private void pruneBackups() {
        File[] backups =
                backupFolder.listFiles(
                        (directory, name) ->
                                name.startsWith("kfaction-")
                                        && name.endsWith(".db")
                );

        if (backups == null
                || backups.length <= backupKeep) {
            return;
        }

        Arrays.sort(
                backups,
                new Comparator<File>() {
                    @Override
                    public int compare(
                            File left,
                            File right
                    ) {
                        return Long.compare(
                                right.lastModified(),
                                left.lastModified()
                        );
                    }
                }
        );

        for (int i = backupKeep;
                i < backups.length;
                i++) {
            try {
                Files.deleteIfExists(
                        backups[i].toPath()
                );
            } catch (IOException exception) {
                plugin.getLogger().warning(
                        "Impossible de supprimer ancien backup "
                                + backups[i].getName()
                );
            }
        }
    }

    private void checkpointWal(String mode)
            throws SQLException {
        String safeMode =
                "TRUNCATE".equalsIgnoreCase(mode)
                        ? "TRUNCATE"
                        : "FULL";

        try (Statement statement =
                     connection.createStatement()) {
            statement.execute(
                    "PRAGMA wal_checkpoint("
                            + safeMode
                            + ")"
            );
        }
    }

    // ============================================================
    // Helpers migration / SQL
    // ============================================================

    private void executePragma(String sql)
            throws SQLException {
        try (Statement statement =
                     connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String querySingleString(String sql)
            throws SQLException {
        try (Statement statement =
                     connection.createStatement();
             ResultSet result =
                     statement.executeQuery(sql)) {
            return result.next()
                    ? result.getString(1)
                    : null;
        }
    }

    private void rollbackQuietly() {
        try {
            if (connection != null) {
                connection.rollback();
            }
        } catch (SQLException ignored) {
            // On conserve l'exception originale.
        }
    }

    private void closeQuietly() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // Fermeture best-effort.
            }
        }

        connection = null;
    }

    private void ensureConnected() {
        if (connection == null) {
            throw new IllegalStateException(
                    "SQLite non initialisé"
            );
        }

        try {
            if (connection.isClosed()) {
                throw new IllegalStateException(
                        "Connexion SQLite fermée"
                );
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "État connexion SQLite inconnu",
                    exception
            );
        }
    }

    private static File[] listJsonFiles(
            File folder
    ) {
        if (folder == null
                || !folder.isDirectory()) {
            return new File[0];
        }

        File[] files =
                folder.listFiles(
                        (directory, name) ->
                                name.endsWith(".json")
                );

        if (files == null) {
            return new File[0];
        }

        Arrays.sort(
                files,
                new Comparator<File>() {
                    @Override
                    public int compare(
                            File left,
                            File right
                    ) {
                        return left.getName()
                                .compareTo(
                                        right.getName()
                                );
                    }
                }
        );

        return files;
    }

    private static String readUtf8Required(
            File file
    ) throws IOException {
        byte[] bytes =
                Files.readAllBytes(
                        file.toPath()
                );

        String payload =
                new String(
                        bytes,
                        StandardCharsets.UTF_8
                );

        if (payload.trim().isEmpty()) {
            throw new IOException(
                    "Fichier vide: "
                            + file.getName()
            );
        }

        return payload;
    }

    private static int detectPayloadSchema(
            String payload
    ) {
        try {
            @SuppressWarnings("deprecation")
            JsonObject json =
                    new JsonParser().parse(payload)
                            .getAsJsonObject();

            if (json.has("storageSchemaVersion")
                    && !json.get(
                            "storageSchemaVersion"
                    ).isJsonNull()) {
                return Math.max(
                        1,
                        json.get(
                                "storageSchemaVersion"
                        ).getAsInt()
                );
            }
        } catch (Exception ignored) {
            // La validation principale donnera une erreur plus claire.
        }

        return 1;
    }

    private static void bindRecord(
            PreparedStatement statement,
            LegacyRecord record,
            long now
    ) throws SQLException {
        statement.setString(
                1,
                record.id
        );
        statement.setString(
                2,
                record.payloadJson
        );
        statement.setInt(
                3,
                record.payloadSchema
        );
        statement.setLong(
                4,
                now
        );
    }

    private static String stripJsonSuffix(
            String fileName
    ) {
        return fileName.substring(
                0,
                fileName.length() - 5
        );
    }

    private static boolean isSafeDatabaseFileName(
            String fileName
    ) {
        if (fileName == null) {
            return false;
        }

        String trimmed =
                fileName.trim();

        if (trimmed.isEmpty()
                || !trimmed.endsWith(".db")
                || trimmed.contains("/")
                || trimmed.contains("\\")
                || trimmed.contains("..")) {
            return false;
        }

        return true;
    }

    private static String normalizeSynchronous(
            String value
    ) {
        if (value == null) {
            return "NORMAL";
        }

        String upper =
                value.trim()
                        .toUpperCase(Locale.ROOT);

        if ("FULL".equals(upper)
                || "NORMAL".equals(upper)
                || "OFF".equals(upper)) {
            return upper;
        }

        return "NORMAL";
    }

    private static void ensureDirectory(
            File folder
    ) {
        if (folder.isDirectory()) {
            return;
        }

        if (folder.exists()) {
            throw new IllegalStateException(
                    "Chemin non dossier: "
                            + folder.getAbsolutePath()
            );
        }

        if (!folder.mkdirs()
                && !folder.isDirectory()) {
            throw new IllegalStateException(
                    "Impossible de créer "
                            + folder.getAbsolutePath()
            );
        }
    }

    private static int clamp(
            int value,
            int min,
            int max
    ) {
        return Math.max(
                min,
                Math.min(max, value)
        );
    }

    private static final class LegacyRecord {
        private final String id;
        private final String payloadJson;
        private final int payloadSchema;

        private LegacyRecord(
                String id,
                String payloadJson,
                int payloadSchema
        ) {
            this.id = id;
            this.payloadJson = payloadJson;
            this.payloadSchema = payloadSchema;
        }
    }

    // ============================================================
    // Diagnostics
    // ============================================================

    @Override
    public String getType() {
        return "sqlite";
    }

    @Override
    public boolean isConnected() {
        if (!connected) {
            return false;
        }

        synchronized (dbLock) {
            if (connection == null) {
                return false;
            }

            try {
                return !connection.isClosed();
            } catch (SQLException exception) {
                return false;
            }
        }
    }

    public File getDatabaseFile() {
        return databaseFile;
    }

    public int getDatabaseSchemaVersionForDiagnostics() {
        synchronized (dbLock) {
            ensureConnected();

            try {
                return getDatabaseSchemaVersion();
            } catch (SQLException exception) {
                return -1;
            }
        }
    }
}
