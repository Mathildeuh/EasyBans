package fr.mathilde.easybans.importer;

import fr.mathilde.easybans.database.dao.PunishmentDao;
import fr.mathilde.easybans.punishment.PunishmentConstants;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Imports from a BanManager MySQL database. {@code location} is a full JDBC URL with
 * credentials.
 *
 * <p><b>Schema caveat:</b> BanManager normalizes players/actors into their own tables and the
 * exact join varies by installed version. This importer assumes the common layout: a
 * {@code bm_bans} table with {@code uuid}, {@code reason}, {@code banned_by_name},
 * {@code created} (seconds), {@code expires} (seconds, 0 = permanent), {@code active}. If your
 * BanManager install uses a different layout, adjust the SQL in {@link #importBans} before
 * running the import.
 */
public final class BanManagerImporter implements Importer {

    private final PunishmentDao punishmentDao;
    private final Executor executor;

    public BanManagerImporter(PunishmentDao punishmentDao, Executor executor) {
        this.punishmentDao = punishmentDao;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<ImportReport> importFrom(String location, UUID staffUuid, String staffName) {
        return CompletableFuture.supplyAsync(() -> {
            ImportReport.Builder builder = new ImportReport.Builder();
            try (Connection connection = DriverManager.getConnection(location)) {
                importBans(connection, builder);
            } catch (SQLException e) {
                builder.error("Could not connect to BanManager database: " + e.getMessage());
            }
            return builder.toReport();
        }, executor);
    }

    private void importBans(Connection connection, ImportReport.Builder builder) {
        String sql = "SELECT uuid, reason, banned_by_name, created, expires FROM bm_bans WHERE active = 1";
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                try {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    String reason = rs.getString("reason");
                    String staffName = Optional.ofNullable(rs.getString("banned_by_name")).orElse("Imported");
                    Instant created = Instant.ofEpochSecond(rs.getLong("created"));
                    long expiresSeconds = rs.getLong("expires");
                    Optional<Instant> expires = expiresSeconds <= 0
                            ? Optional.empty() : Optional.of(Instant.ofEpochSecond(expiresSeconds));

                    if (punishmentDao.findAnyActiveBan(uuid).get().isPresent()) {
                        builder.skipped();
                        continue;
                    }
                    punishmentDao.insertBan(uuid, Optional.empty(), false, reason, PunishmentConstants.CONSOLE_UUID,
                            staffName, Optional.empty(), null, Optional.empty(), false, created, expires).get();
                    builder.imported();
                } catch (Exception e) {
                    builder.error("bm_bans row: " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            builder.error("bm_bans: " + e.getMessage());
        }
    }
}
