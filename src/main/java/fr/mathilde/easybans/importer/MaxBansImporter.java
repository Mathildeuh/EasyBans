package fr.mathilde.easybans.importer;

import fr.mathilde.easybans.cache.UuidResolver;
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
 * Imports from a legacy MaxBans MySQL database. {@code location} is a full JDBC URL with
 * credentials.
 *
 * <p><b>Schema caveat:</b> MaxBans predates Mojang UUIDs in some releases and may only have a
 * player name column. This importer assumes a {@code bans} table with {@code name},
 * {@code reason}, {@code banner} (staff name), {@code time} (seconds), {@code expire}
 * (seconds, 0/-1 = permanent); when a {@code uuid} column isn't present, the target is
 * resolved by name via {@link UuidResolver} (cache, then Mojang API), which is slower and
 * requires each name to still resolve to a real account.
 */
public final class MaxBansImporter implements Importer {

    private final PunishmentDao punishmentDao;
    private final UuidResolver uuidResolver;
    private final Executor executor;

    public MaxBansImporter(PunishmentDao punishmentDao, UuidResolver uuidResolver, Executor executor) {
        this.punishmentDao = punishmentDao;
        this.uuidResolver = uuidResolver;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<ImportReport> importFrom(String location, UUID staffUuid, String staffName) {
        return CompletableFuture.supplyAsync(() -> {
            ImportReport.Builder builder = new ImportReport.Builder();
            String sql = "SELECT name, reason, banner, time, expire FROM bans";
            try (Connection connection = DriverManager.getConnection(location);
                 Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(sql)) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    String reason = rs.getString("reason");
                    String bannerName = Optional.ofNullable(rs.getString("banner")).orElse("Imported");
                    long timeSeconds = rs.getLong("time");
                    long expireSeconds = rs.getLong("expire");
                    Instant created = timeSeconds > 0 ? Instant.ofEpochSecond(timeSeconds) : Instant.now();
                    Optional<Instant> expires = expireSeconds <= 0
                            ? Optional.empty() : Optional.of(Instant.ofEpochSecond(expireSeconds));

                    try {
                        UUID uuid = uuidResolver.resolveUuid(name).get()
                                .orElseThrow(() -> new IllegalStateException("could not resolve uuid for " + name));
                        if (punishmentDao.findAnyActiveBan(uuid).get().isPresent()) {
                            builder.skipped();
                            continue;
                        }
                        punishmentDao.insertBan(uuid, Optional.empty(), false, reason,
                                PunishmentConstants.CONSOLE_UUID, bannerName, Optional.empty(), null, Optional.empty(),
                                false, created, expires).get();
                        builder.imported();
                    } catch (Exception e) {
                        builder.error("bans row for '" + name + "': " + e.getMessage());
                    }
                }
            } catch (SQLException e) {
                builder.error("Could not connect to MaxBans database: " + e.getMessage());
            }
            return builder.toReport();
        }, executor);
    }
}
