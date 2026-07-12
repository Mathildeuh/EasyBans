package fr.mathilde.easybans.importer;

import fr.mathilde.easybans.database.dao.PunishmentDao;

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
 * Imports from a LiteBans-compatible database. {@code location} is a full JDBC URL with
 * credentials, e.g. {@code jdbc:mysql://host:3306/litebans_db?user=root&password=secret}.
 * Uses LiteBans' well-documented default table/column names ({@code litebans_bans},
 * {@code litebans_mutes}, {@code litebans_warnings}, {@code litebans_kicks}); if the source
 * install customized its table prefix, adjust {@link #TABLE_PREFIX} before importing.
 */
public final class LiteBansImporter implements Importer {

    private static final String TABLE_PREFIX = "litebans_";

    private final PunishmentDao punishmentDao;
    private final Executor executor;

    public LiteBansImporter(PunishmentDao punishmentDao, Executor executor) {
        this.punishmentDao = punishmentDao;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<ImportReport> importFrom(String location, UUID staffUuid, String staffName) {
        return CompletableFuture.supplyAsync(() -> {
            ImportReport.Builder builder = new ImportReport.Builder();
            try (Connection connection = DriverManager.getConnection(location)) {
                importBans(connection, staffName, builder);
                importMutes(connection, staffName, builder);
                importWarnings(connection, staffName, builder);
                importKicks(connection, staffName, builder);
            } catch (SQLException e) {
                builder.error("Could not connect to LiteBans database: " + e.getMessage());
            }
            return builder.toReport();
        }, executor);
    }

    private void importBans(Connection connection, String fallbackStaffName, ImportReport.Builder builder) {
        String sql = "SELECT uuid, ip, ipban, reason, banned_by_uuid, banned_by_name, time, until "
                + "FROM " + TABLE_PREFIX + "bans WHERE active = 1";
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                try {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    boolean ipBanned = rs.getBoolean("ipban");
                    String ip = rs.getString("ip");
                    String reason = rs.getString("reason");
                    UUID staffUuid = parseUuidOrConsole(rs.getString("banned_by_uuid"));
                    String staffName = Optional.ofNullable(rs.getString("banned_by_name")).orElse(fallbackStaffName);
                    Instant created = Instant.ofEpochMilli(rs.getLong("time"));
                    long until = rs.getLong("until");
                    Optional<Instant> expires = until <= 0 ? Optional.empty() : Optional.of(Instant.ofEpochMilli(until));

                    if (punishmentDao.findAnyActiveBan(uuid).get().isPresent()) {
                        builder.skipped();
                        continue;
                    }
                    punishmentDao.insertBan(uuid, Optional.ofNullable(ip), ipBanned, reason, staffUuid, staffName,
                            Optional.empty(), null, Optional.empty(), false, created, expires).get();
                    builder.imported();
                } catch (Exception e) {
                    builder.error("litebans_bans row: " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            builder.error("litebans_bans: " + e.getMessage());
        }
    }

    private void importMutes(Connection connection, String fallbackStaffName, ImportReport.Builder builder) {
        String sql = "SELECT uuid, reason, banned_by_uuid, banned_by_name, time, until "
                + "FROM " + TABLE_PREFIX + "mutes WHERE active = 1";
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                try {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    String reason = rs.getString("reason");
                    UUID staffUuid = parseUuidOrConsole(rs.getString("banned_by_uuid"));
                    String staffName = Optional.ofNullable(rs.getString("banned_by_name")).orElse(fallbackStaffName);
                    Instant created = Instant.ofEpochMilli(rs.getLong("time"));
                    long until = rs.getLong("until");
                    Optional<Instant> expires = until <= 0 ? Optional.empty() : Optional.of(Instant.ofEpochMilli(until));

                    if (punishmentDao.findAnyActiveMute(uuid).get().isPresent()) {
                        builder.skipped();
                        continue;
                    }
                    punishmentDao.insertMute(uuid, reason, staffUuid, staffName, Optional.empty(), null, false,
                            created, expires).get();
                    builder.imported();
                } catch (Exception e) {
                    builder.error("litebans_mutes row: " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            builder.error("litebans_mutes: " + e.getMessage());
        }
    }

    private void importWarnings(Connection connection, String fallbackStaffName, ImportReport.Builder builder) {
        String sql = "SELECT uuid, reason, banned_by_uuid, banned_by_name, time "
                + "FROM " + TABLE_PREFIX + "warnings";
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                try {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    String reason = rs.getString("reason");
                    UUID staffUuid = parseUuidOrConsole(rs.getString("banned_by_uuid"));
                    String staffName = Optional.ofNullable(rs.getString("banned_by_name")).orElse(fallbackStaffName);
                    Instant created = Instant.ofEpochMilli(rs.getLong("time"));

                    punishmentDao.insertWarning(uuid, "imported", reason, staffUuid, staffName, Optional.empty(),
                            false, created, Optional.empty(), true).get();
                    builder.imported();
                } catch (Exception e) {
                    builder.error("litebans_warnings row: " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            builder.error("litebans_warnings: " + e.getMessage());
        }
    }

    private void importKicks(Connection connection, String fallbackStaffName, ImportReport.Builder builder) {
        String sql = "SELECT uuid, reason, banned_by_uuid, banned_by_name, time FROM " + TABLE_PREFIX + "kicks";
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                try {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    String reason = rs.getString("reason");
                    UUID staffUuid = parseUuidOrConsole(rs.getString("banned_by_uuid"));
                    String staffName = Optional.ofNullable(rs.getString("banned_by_name")).orElse(fallbackStaffName);
                    Instant created = Instant.ofEpochMilli(rs.getLong("time"));

                    punishmentDao.insertKick(uuid, reason, staffUuid, staffName, Optional.empty(), Optional.empty(),
                            false, created).get();
                    builder.imported();
                } catch (Exception e) {
                    builder.error("litebans_kicks row: " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            builder.error("litebans_kicks: " + e.getMessage());
        }
    }

    private UUID parseUuidOrConsole(String raw) {
        if (raw == null || raw.isBlank()) {
            return fr.mathilde.easybans.punishment.PunishmentConstants.CONSOLE_UUID;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return fr.mathilde.easybans.punishment.PunishmentConstants.CONSOLE_UUID;
        }
    }
}
