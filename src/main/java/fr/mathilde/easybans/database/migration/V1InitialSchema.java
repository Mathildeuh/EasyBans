package fr.mathilde.easybans.database.migration;

import fr.mathilde.easybans.database.DatabaseProvider;
import fr.mathilde.easybans.database.DatabaseType;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates the full initial schema. Punishments intentionally live in a single
 * {@code punishments} table with a {@code type} discriminator column rather than one table
 * per punishment kind (bans/mutes/warnings/kicks/notes) - see ARCHITECTURE.md. This keeps
 * cross-type queries (player history, staff history) a single indexed query instead of a
 * UNION across five tables, at the cost of a handful of columns that are only meaningful for
 * some types (e.g. {@code target_ip} only applies to bans).
 */
public final class V1InitialSchema implements Migration {

    @Override
    public int version() {
        return 1;
    }

    @Override
    public String description() {
        return "Initial schema (players, punishments, ip exemptions/history, sync events)";
    }

    @Override
    public void apply(Connection connection, DatabaseType type, DatabaseProvider db) throws SQLException {
        String pk = SchemaSupport.autoIncrementPrimaryKey(type);

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                        uuid CHAR(36) PRIMARY KEY,
                        name VARCHAR(16) NOT NULL,
                        last_ip VARCHAR(45),
                        first_seen BIGINT NOT NULL,
                        last_seen BIGINT NOT NULL,
                        locale VARCHAR(8)
                    )
                    """.formatted(db.table("players")));

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                        id %s,
                        type VARCHAR(10) NOT NULL,
                        target_uuid CHAR(36) NOT NULL,
                        target_ip VARCHAR(45),
                        ip_banned BOOLEAN NOT NULL DEFAULT FALSE,
                        category VARCHAR(64),
                        reason VARCHAR(500) NOT NULL,
                        staff_uuid CHAR(36) NOT NULL,
                        staff_name VARCHAR(16) NOT NULL,
                        server_scope VARCHAR(64),
                        template_id VARCHAR(64),
                        kickscreen_key VARCHAR(128),
                        silent BOOLEAN NOT NULL DEFAULT FALSE,
                        active BOOLEAN NOT NULL DEFAULT TRUE,
                        acknowledged BOOLEAN NOT NULL DEFAULT TRUE,
                        created_at BIGINT NOT NULL,
                        expires_at BIGINT,
                        removed_by_uuid CHAR(36),
                        removed_by_name VARCHAR(16),
                        removed_at BIGINT,
                        removed_reason VARCHAR(500)
                    )
                    """.formatted(db.table("punishments"), pk));

            createIndex(statement, "idx_" + db.table("punishments") + "_target",
                    db.table("punishments"), "target_uuid, type");
            createIndex(statement, "idx_" + db.table("punishments") + "_ip",
                    db.table("punishments"), "target_ip");
            createIndex(statement, "idx_" + db.table("punishments") + "_staff",
                    db.table("punishments"), "staff_uuid");
            createIndex(statement, "idx_" + db.table("punishments") + "_active",
                    db.table("punishments"), "type, active, expires_at");

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                        id %s,
                        uuid CHAR(36) NOT NULL,
                        ip VARCHAR(45) NOT NULL,
                        staff_uuid CHAR(36) NOT NULL,
                        staff_name VARCHAR(16) NOT NULL,
                        reason VARCHAR(500),
                        created_at BIGINT NOT NULL,
                        CONSTRAINT uq_%s UNIQUE (uuid, ip)
                    )
                    """.formatted(db.table("ip_exemptions"), pk, db.table("ip_exemptions")));

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                        uuid CHAR(36) NOT NULL,
                        ip VARCHAR(45) NOT NULL,
                        first_seen BIGINT NOT NULL,
                        last_seen BIGINT NOT NULL,
                        PRIMARY KEY (uuid, ip)
                    )
                    """.formatted(db.table("ip_history")));

            createIndex(statement, "idx_" + db.table("ip_history") + "_ip", db.table("ip_history"), "ip");

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                        id %s,
                        event_type VARCHAR(32) NOT NULL,
                        payload VARCHAR(1000),
                        origin_node CHAR(36) NOT NULL,
                        created_at BIGINT NOT NULL
                    )
                    """.formatted(db.table("sync_events"), pk));

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                        version INT PRIMARY KEY,
                        description VARCHAR(255),
                        applied_at BIGINT NOT NULL
                    )
                    """.formatted(db.table("schema_version")));
        }
    }

    private void createIndex(Statement statement, String indexName, String table, String columns) throws SQLException {
        try {
            statement.executeUpdate("CREATE INDEX " + indexName + " ON " + table + " (" + columns + ")");
        } catch (SQLException e) {
            // Index already exists (no portable "IF NOT EXISTS" across all four dialects for indexes on first run
            // re-attempts) - safe to ignore duplicate-index errors specifically.
            if (!isDuplicateIndex(e)) {
                throw e;
            }
        }
    }

    private boolean isDuplicateIndex(SQLException e) {
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        return message.contains("already exists") || message.contains("duplicate");
    }
}
