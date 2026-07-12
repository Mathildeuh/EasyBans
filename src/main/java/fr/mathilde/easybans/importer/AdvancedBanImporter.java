package fr.mathilde.easybans.importer;

import fr.mathilde.easybans.database.dao.PunishmentDao;
import fr.mathilde.easybans.punishment.PunishmentConstants;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Imports from an AdvancedBan MySQL/MariaDB database. {@code location} is a full JDBC URL
 * with credentials. Reads the {@code Punishments} table using AdvancedBan's common column
 * names ({@code uuid}, {@code punishmentType}, {@code reason}, {@code operator}, {@code start},
 * {@code end}, {@code punishedAddress}). AdvancedBan predates per-staff UUID tracking in some
 * versions and only stores the operator's name, so imported punishments are attributed to
 * CONSOLE with the original operator name kept in the reason-adjacent staff name field.
 */
public final class AdvancedBanImporter implements Importer {

    private final PunishmentDao punishmentDao;
    private final Executor executor;

    public AdvancedBanImporter(PunishmentDao punishmentDao, Executor executor) {
        this.punishmentDao = punishmentDao;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<ImportReport> importFrom(String location, UUID staffUuid, String staffName) {
        return CompletableFuture.supplyAsync(() -> {
            ImportReport.Builder builder = new ImportReport.Builder();
            String sql = "SELECT uuid, punishmentType, reason, operator, start, end, punishedAddress FROM Punishments";
            try (Connection connection = DriverManager.getConnection(location);
                 Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(sql)) {
                while (rs.next()) {
                    try {
                        processRow(rs, builder);
                    } catch (Exception e) {
                        builder.error("Punishments row: " + e.getMessage());
                    }
                }
            } catch (SQLException e) {
                builder.error("Could not connect to AdvancedBan database: " + e.getMessage());
            }
            return builder.toReport();
        }, executor);
    }

    private void processRow(ResultSet rs, ImportReport.Builder builder) throws Exception {
        String rawUuid = rs.getString("uuid");
        if (rawUuid == null) {
            return; // console/IP-only entries with no player uuid are out of scope for this import
        }
        UUID uuid = UUID.fromString(rawUuid);
        String type = String.valueOf(rs.getString("punishmentType")).toUpperCase(Locale.ROOT);
        String reason = rs.getString("reason");
        String operator = Optional.ofNullable(rs.getString("operator")).orElse("Imported");
        long start = rs.getLong("start");
        long end = rs.getLong("end");
        Optional<Instant> expires = end <= 0 ? Optional.empty() : Optional.of(Instant.ofEpochMilli(end));
        Instant created = start > 0 ? Instant.ofEpochMilli(start) : Instant.now();

        switch (type) {
            case "BAN", "IP_BAN" -> {
                if (punishmentDao.findAnyActiveBan(uuid).get().isPresent()) {
                    builder.skipped();
                    return;
                }
                Optional<String> ip = Optional.ofNullable(rs.getString("punishedAddress"));
                punishmentDao.insertBan(uuid, ip, type.equals("IP_BAN"), reason, PunishmentConstants.CONSOLE_UUID,
                        operator, Optional.empty(), null, Optional.empty(), false, created, expires).get();
                builder.imported();
            }
            case "MUTE" -> {
                if (punishmentDao.findAnyActiveMute(uuid).get().isPresent()) {
                    builder.skipped();
                    return;
                }
                punishmentDao.insertMute(uuid, reason, PunishmentConstants.CONSOLE_UUID, operator, Optional.empty(),
                        null, false, created, expires).get();
                builder.imported();
            }
            case "WARNING" -> {
                punishmentDao.insertWarning(uuid, "imported", reason, PunishmentConstants.CONSOLE_UUID, operator,
                        Optional.empty(), false, created, Optional.empty(), true).get();
                builder.imported();
            }
            case "KICK" -> {
                punishmentDao.insertKick(uuid, reason, PunishmentConstants.CONSOLE_UUID, operator, Optional.empty(),
                        Optional.empty(), false, created).get();
                builder.imported();
            }
            default -> builder.error("Unknown AdvancedBan punishmentType: " + type);
        }
    }
}
