package fr.mathilde.easybans.database.migration;

import fr.mathilde.easybans.database.DatabaseProvider;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public final class MigrationRunner {

    private final DatabaseProvider db;
    private final Logger logger;
    private final List<Migration> migrations = List.of(new V1InitialSchema());

    public MigrationRunner(DatabaseProvider db, Logger logger) {
        this.db = db;
        this.logger = logger;
    }

    public void run() throws SQLException {
        try (Connection connection = db.getConnection()) {
            ensureVersionTable(connection);
            int current = currentVersion(connection);

            List<Migration> pending = migrations.stream()
                    .filter(m -> m.version() > current)
                    .sorted(Comparator.comparingInt(Migration::version))
                    .toList();

            for (Migration migration : pending) {
                logger.info("Applying database migration v{}: {}", migration.version(), migration.description());
                connection.setAutoCommit(false);
                try {
                    migration.apply(connection, db.type(), db);
                    recordVersion(connection, migration.version(), migration.description());
                    connection.commit();
                } catch (SQLException e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(true);
                }
            }

            if (pending.isEmpty()) {
                logger.info("Database schema up to date (v{})", current);
            } else {
                logger.info("Database schema migrated to v{}", pending.getLast().version());
            }
        }
    }

    private void ensureVersionTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                        version INT PRIMARY KEY,
                        description VARCHAR(255),
                        applied_at BIGINT NOT NULL
                    )
                    """.formatted(db.table("schema_version")));
        }
    }

    private int currentVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT MAX(version) AS v FROM " + db.table("schema_version"))) {
            if (rs.next()) {
                int value = rs.getInt("v");
                return rs.wasNull() ? 0 : value;
            }
            return 0;
        }
    }

    private void recordVersion(Connection connection, int version, String description) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO " + db.table("schema_version") + " (version, description, applied_at) VALUES (?, ?, ?)")) {
            ps.setInt(1, version);
            ps.setString(2, description);
            ps.setLong(3, Instant.now().toEpochMilli());
            ps.executeUpdate();
        }
    }
}
