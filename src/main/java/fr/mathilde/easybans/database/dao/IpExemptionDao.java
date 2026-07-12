package fr.mathilde.easybans.database.dao;

import fr.mathilde.easybans.database.DatabaseProvider;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Backs {@code /easybans allow} - exempts a specific uuid+ip pair from IP bans without lifting the ban itself. */
public final class IpExemptionDao {

    private final DatabaseProvider db;

    public IpExemptionDao(DatabaseProvider db) {
        this.db = db;
    }

    public CompletableFuture<Boolean> isExempt(UUID uuid, String ip) {
        return db.supplyAsync(connection -> {
            String sql = "SELECT 1 FROM " + db.table("ip_exemptions") + " WHERE uuid = ? AND ip = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, ip);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    public CompletableFuture<Boolean> allow(UUID uuid, String ip, UUID staffUuid, String staffName, String reason) {
        return db.supplyAsync(connection -> {
            String sql = "INSERT INTO " + db.table("ip_exemptions")
                    + " (uuid, ip, staff_uuid, staff_name, reason, created_at) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, ip);
                ps.setString(3, staffUuid.toString());
                ps.setString(4, staffName);
                ps.setString(5, reason);
                ps.setLong(6, Instant.now().toEpochMilli());
                ps.executeUpdate();
                return true;
            } catch (java.sql.SQLException e) {
                String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
                if (message.contains("uniq") || message.contains("duplicate")) {
                    return false; // already exempted
                }
                throw e;
            }
        });
    }

    public CompletableFuture<Boolean> revoke(UUID uuid, String ip) {
        return db.supplyAsync(connection -> {
            String sql = "DELETE FROM " + db.table("ip_exemptions") + " WHERE uuid = ? AND ip = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, ip);
                return ps.executeUpdate() > 0;
            }
        });
    }
}
