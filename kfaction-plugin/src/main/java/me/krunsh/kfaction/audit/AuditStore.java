package me.krunsh.kfaction.audit;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.core.operation.OperationSource;

/**
 * Backend SQLite séparé pour l'audit.
 *
 * Le writer possède une connexion dédiée.
 * Les recherches ouvrent une connexion read-only indépendante afin de ne pas
 * bloquer le writer WAL.
 */
public final class AuditStore {

    private static final int SCHEMA_VERSION = 1;

    private final Kfaction plugin;
    private final File databaseFile;

    private Connection writerConnection;

    public AuditStore(
            Kfaction plugin
    ) {
        if (plugin == null) {
            throw new IllegalArgumentException(
                    "plugin cannot be null"
            );
        }

        this.plugin = plugin;
        this.databaseFile =
                new File(
                        plugin.getDataFolder(),
                        "audit.db"
                );
    }

    public synchronized void initialize()
            throws SQLException {
        if (!plugin.getDataFolder().exists()
                && !plugin.getDataFolder().mkdirs()) {
            throw new SQLException(
                    "Impossible de créer le dossier plugin"
            );
        }

        writerConnection =
                DriverManager.getConnection(
                        jdbcUrl()
                );

        configureConnection(
                writerConnection,
                false
        );

        migrateSchema(
                writerConnection
        );
    }

    public synchronized void close() {
        if (writerConnection == null) {
            return;
        }

        try {
            writerConnection.close();
        } catch (SQLException exception) {
            plugin.getLogger().warning(
                    "Erreur fermeture audit.db: "
                            + exception.getMessage()
            );
        } finally {
            writerConnection = null;
        }
    }

    public synchronized void writeBatch(
            Collection<AuditEntry> entries
    ) throws SQLException {
        if (entries == null
                || entries.isEmpty()) {
            return;
        }

        ensureWriter();

        boolean oldAutoCommit =
                writerConnection.getAutoCommit();

        writerConnection.setAutoCommit(false);

        final String sql =
                "INSERT INTO audit_events ("
                        + "id, ts, category, action, outcome, "
                        + "faction_id, actor_uuid, actor_name, "
                        + "target_uuid, target_name, source, "
                        + "correlation_id, details"
                        + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement statement =
                     writerConnection.prepareStatement(
                             sql
                     )) {

            for (AuditEntry entry : entries) {
                if (entry == null) {
                    continue;
                }

                statement.setString(
                        1,
                        entry.getId()
                );

                statement.setLong(
                        2,
                        entry.getTimestamp()
                );

                statement.setString(
                        3,
                        entry.getCategory().name()
                );

                statement.setString(
                        4,
                        entry.getAction()
                );

                statement.setString(
                        5,
                        entry.getOutcome().name()
                );

                setNullableString(
                        statement,
                        6,
                        entry.getFactionId()
                );

                setNullableString(
                        statement,
                        7,
                        entry.getActorId() != null
                                ? entry.getActorId()
                                        .toString()
                                : null
                );

                setNullableString(
                        statement,
                        8,
                        entry.getActorName()
                );

                setNullableString(
                        statement,
                        9,
                        entry.getTargetId() != null
                                ? entry.getTargetId()
                                        .toString()
                                : null
                );

                setNullableString(
                        statement,
                        10,
                        entry.getTargetName()
                );

                statement.setString(
                        11,
                        entry.getSource().name()
                );

                statement.setString(
                        12,
                        entry.getCorrelationId()
                );

                setNullableString(
                        statement,
                        13,
                        entry.getDetails()
                );

                statement.addBatch();
            }

            statement.executeBatch();
            writerConnection.commit();

        } catch (SQLException exception) {
            rollbackQuietly(
                    writerConnection
            );

            throw exception;

        } finally {
            writerConnection.setAutoCommit(
                    oldAutoCommit
            );
        }
    }

    public List<AuditEntry> query(
            AuditQuery query
    ) throws SQLException {
        if (query == null) {
            throw new IllegalArgumentException(
                    "query cannot be null"
            );
        }

        StringBuilder sql =
                new StringBuilder(
                        "SELECT id, ts, category, action, outcome, "
                                + "faction_id, actor_uuid, actor_name, "
                                + "target_uuid, target_name, source, "
                                + "correlation_id, details "
                                + "FROM audit_events WHERE 1=1"
                );

        List<Object> parameters =
                new ArrayList<Object>();

        if (query.getFactionId() != null) {
            sql.append(
                    " AND faction_id = ?"
            );

            parameters.add(
                    query.getFactionId()
            );
        }

        if (query.getPlayerId() != null) {
            sql.append(
                    " AND (actor_uuid = ? OR target_uuid = ?)"
            );

            String uuid =
                    query.getPlayerId()
                            .toString();

            parameters.add(uuid);
            parameters.add(uuid);
        }

        if (query.getCategory() != null) {
            sql.append(
                    " AND category = ?"
            );

            parameters.add(
                    query.getCategory()
                            .name()
            );
        }

        if (query.getAction() != null) {
            sql.append(
                    " AND action = ?"
            );

            parameters.add(
                    query.getAction()
            );
        }

        if (query.getCorrelationId() != null) {
            sql.append(
                    " AND correlation_id = ?"
            );

            parameters.add(
                    query.getCorrelationId()
            );
        }

        if (query.getSinceTimestamp() > 0L) {
            sql.append(
                    " AND ts >= ?"
            );

            parameters.add(
                    Long.valueOf(
                            query.getSinceTimestamp()
                    )
            );
        }

        sql.append(
                " ORDER BY ts DESC LIMIT ?"
        );

        parameters.add(
                Integer.valueOf(
                        query.getLimit()
                )
        );

        Connection connection = null;

        try {
            connection =
                    DriverManager.getConnection(
                            jdbcUrl()
                    );

            configureConnection(
                    connection,
                    true
            );

            try (PreparedStatement statement =
                         connection.prepareStatement(
                                 sql.toString()
                         )) {

                bindParameters(
                        statement,
                        parameters
                );

                try (ResultSet resultSet =
                             statement.executeQuery()) {

                    List<AuditEntry> result =
                            new ArrayList<AuditEntry>();

                    while (resultSet.next()) {
                        AuditEntry entry =
                                readEntry(
                                        resultSet
                                );

                        if (entry != null) {
                            result.add(entry);
                        }
                    }

                    return result;
                }
            }

        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                    // Rien à faire.
                }
            }
        }
    }

    public synchronized int cleanupBefore(
            long timestamp
    ) throws SQLException {
        ensureWriter();

        try (PreparedStatement statement =
                     writerConnection.prepareStatement(
                             "DELETE FROM audit_events WHERE ts < ?"
                     )) {

            statement.setLong(
                    1,
                    timestamp
            );

            return statement.executeUpdate();
        }
    }

    public synchronized long count()
            throws SQLException {
        ensureWriter();

        try (Statement statement =
                     writerConnection.createStatement();
             ResultSet resultSet =
                     statement.executeQuery(
                             "SELECT COUNT(*) FROM audit_events"
                     )) {

            return resultSet.next()
                    ? resultSet.getLong(1)
                    : 0L;
        }
    }

    public File getDatabaseFile() {
        return databaseFile;
    }

    // ============================================================
    // Schema
    // ============================================================

    private void migrateSchema(
            Connection connection
    ) throws SQLException {
        int current =
                readUserVersion(
                        connection
                );

        if (current > SCHEMA_VERSION) {
            throw new SQLException(
                    "audit.db schema "
                            + current
                            + " plus récent que supporté "
                            + SCHEMA_VERSION
            );
        }

        if (current == 0) {
            boolean oldAutoCommit =
                    connection.getAutoCommit();

            connection.setAutoCommit(false);

            try (Statement statement =
                         connection.createStatement()) {

                statement.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS audit_events ("
                                + "id TEXT PRIMARY KEY NOT NULL,"
                                + "ts INTEGER NOT NULL,"
                                + "category TEXT NOT NULL,"
                                + "action TEXT NOT NULL,"
                                + "outcome TEXT NOT NULL,"
                                + "faction_id TEXT,"
                                + "actor_uuid TEXT,"
                                + "actor_name TEXT,"
                                + "target_uuid TEXT,"
                                + "target_name TEXT,"
                                + "source TEXT NOT NULL,"
                                + "correlation_id TEXT NOT NULL,"
                                + "details TEXT"
                                + ")"
                );

                statement.executeUpdate(
                        "CREATE INDEX IF NOT EXISTS "
                                + "idx_audit_ts "
                                + "ON audit_events(ts DESC)"
                );

                statement.executeUpdate(
                        "CREATE INDEX IF NOT EXISTS "
                                + "idx_audit_faction_ts "
                                + "ON audit_events(faction_id, ts DESC)"
                );

                statement.executeUpdate(
                        "CREATE INDEX IF NOT EXISTS "
                                + "idx_audit_actor_ts "
                                + "ON audit_events(actor_uuid, ts DESC)"
                );

                statement.executeUpdate(
                        "CREATE INDEX IF NOT EXISTS "
                                + "idx_audit_target_ts "
                                + "ON audit_events(target_uuid, ts DESC)"
                );

                statement.executeUpdate(
                        "CREATE INDEX IF NOT EXISTS "
                                + "idx_audit_category_ts "
                                + "ON audit_events(category, ts DESC)"
                );

                statement.executeUpdate(
                        "CREATE INDEX IF NOT EXISTS "
                                + "idx_audit_action_ts "
                                + "ON audit_events(action, ts DESC)"
                );

                statement.executeUpdate(
                        "CREATE INDEX IF NOT EXISTS "
                                + "idx_audit_correlation "
                                + "ON audit_events(correlation_id)"
                );

                statement.execute(
                        "PRAGMA user_version = 1"
                );

                connection.commit();

            } catch (SQLException exception) {
                rollbackQuietly(
                        connection
                );

                throw exception;

            } finally {
                connection.setAutoCommit(
                        oldAutoCommit
                );
            }
        }
    }

    private static int readUserVersion(
            Connection connection
    ) throws SQLException {
        try (Statement statement =
                     connection.createStatement();
             ResultSet resultSet =
                     statement.executeQuery(
                             "PRAGMA user_version"
                     )) {

            return resultSet.next()
                    ? resultSet.getInt(1)
                    : 0;
        }
    }

    private void configureConnection(
            Connection connection,
            boolean queryOnly
    ) throws SQLException {
        try (Statement statement =
                     connection.createStatement()) {

            statement.execute(
                    "PRAGMA busy_timeout = 5000"
            );

            statement.execute(
                    "PRAGMA foreign_keys = ON"
            );

            if (!queryOnly) {
                statement.execute(
                        "PRAGMA journal_mode = WAL"
                );

                statement.execute(
                        "PRAGMA synchronous = NORMAL"
                );
            } else {
                statement.execute(
                        "PRAGMA query_only = ON"
                );
            }
        }
    }

    // ============================================================
    // Read helpers
    // ============================================================

    private static AuditEntry readEntry(
            ResultSet resultSet
    ) {
        try {
            return new AuditEntry(
                    resultSet.getString("id"),
                    resultSet.getLong("ts"),
                    AuditCategory.valueOf(
                            resultSet.getString(
                                    "category"
                            )
                    ),
                    resultSet.getString(
                            "action"
                    ),
                    AuditOutcome.valueOf(
                            resultSet.getString(
                                    "outcome"
                            )
                    ),
                    resultSet.getString(
                            "faction_id"
                    ),
                    parseUuid(
                            resultSet.getString(
                                    "actor_uuid"
                            )
                    ),
                    resultSet.getString(
                            "actor_name"
                    ),
                    parseUuid(
                            resultSet.getString(
                                    "target_uuid"
                            )
                    ),
                    resultSet.getString(
                            "target_name"
                    ),
                    OperationSource.valueOf(
                            resultSet.getString(
                                    "source"
                            )
                    ),
                    resultSet.getString(
                            "correlation_id"
                    ),
                    resultSet.getString(
                            "details"
                    )
            );
        } catch (RuntimeException exception) {
            return null;
        } catch (SQLException exception) {
            return null;
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

    private static void bindParameters(
            PreparedStatement statement,
            List<Object> values
    ) throws SQLException {
        for (int i = 0;
                i < values.size();
                i++) {
            Object value =
                    values.get(i);

            int index =
                    i + 1;

            if (value instanceof Long) {
                statement.setLong(
                        index,
                        ((Long) value)
                                .longValue()
                );
            } else if (value instanceof Integer) {
                statement.setInt(
                        index,
                        ((Integer) value)
                                .intValue()
                );
            } else {
                statement.setString(
                        index,
                        String.valueOf(value)
                );
            }
        }
    }

    private static void setNullableString(
            PreparedStatement statement,
            int index,
            String value
    ) throws SQLException {
        if (value == null) {
            statement.setNull(
                    index,
                    java.sql.Types.VARCHAR
            );
        } else {
            statement.setString(
                    index,
                    value
            );
        }
    }

    private static void rollbackQuietly(
            Connection connection
    ) {
        if (connection == null) {
            return;
        }

        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Rien à faire.
        }
    }

    private void ensureWriter()
            throws SQLException {
        if (writerConnection == null
                || writerConnection.isClosed()) {
            throw new SQLException(
                    "AuditStore non initialisé"
            );
        }
    }

    private String jdbcUrl() {
        return "jdbc:sqlite:"
                + databaseFile.getAbsolutePath();
    }
}
