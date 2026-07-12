package fr.mathilde.easybans.database.dao;

import fr.mathilde.easybans.database.DatabaseProvider;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class IpHistoryDao {

    private final DatabaseProvider db;

    public IpHistoryDao(DatabaseProvider db) {
        this.db = db;
    }

    public CompletableFuture<Void> recordLogin(UUID uuid, String ip) {
        return db.runAsync(connection -> {
            long now = Instant.now().toEpochMilli();
            String update = "UPDATE " + db.table("ip_history") + " SET last_seen = ? WHERE uuid = ? AND ip = ?";
            try (PreparedStatement ps = connection.prepareStatement(update)) {
                ps.setLong(1, now);
                ps.setString(2, uuid.toString());
                ps.setString(3, ip);
                if (ps.executeUpdate() > 0) {
                    return;
                }
            }
            String insert = "INSERT INTO " + db.table("ip_history")
                    + " (uuid, ip, first_seen, last_seen) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(insert)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, ip);
                ps.setLong(3, now);
                ps.setLong(4, now);
                ps.executeUpdate();
            }
        });
    }

    /** Other accounts (excluding {@code uuid} itself) that have ever connected from this IP. */
    public CompletableFuture<List<UUID>> findAccountsSharingIp(UUID uuid, String ip) {
        return db.supplyAsync(connection -> {
            String sql = "SELECT uuid FROM " + db.table("ip_history") + " WHERE ip = ? AND uuid <> ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, ip);
                ps.setString(2, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    List<UUID> result = new ArrayList<>();
                    while (rs.next()) {
                        result.add(UUID.fromString(rs.getString("uuid")));
                    }
                    return result;
                }
            }
        });
    }
}
