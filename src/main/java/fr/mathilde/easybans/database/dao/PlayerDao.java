package fr.mathilde.easybans.database.dao;

import fr.mathilde.easybans.database.DatabaseProvider;
import fr.mathilde.easybans.player.PlayerRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PlayerDao {

    private final DatabaseProvider db;

    public PlayerDao(DatabaseProvider db) {
        this.db = db;
    }

    /** Insert-or-update the player's name/IP/last-seen. Preserves the existing locale. */
    public CompletableFuture<Void> upsertOnJoin(UUID uuid, String name, String ip) {
        return db.runAsync(connection -> {
            long now = Instant.now().toEpochMilli();
            if (findByUuidSync(connection, uuid).isPresent()) {
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE " + db.table("players") + " SET name = ?, last_ip = ?, last_seen = ? WHERE uuid = ?")) {
                    ps.setString(1, name);
                    ps.setString(2, ip);
                    ps.setLong(3, now);
                    ps.setString(4, uuid.toString());
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO " + db.table("players")
                                + " (uuid, name, last_ip, first_seen, last_seen, locale) VALUES (?, ?, ?, ?, ?, NULL)")) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, name);
                    ps.setString(3, ip);
                    ps.setLong(4, now);
                    ps.setLong(5, now);
                    ps.executeUpdate();
                }
            }
        });
    }

    public CompletableFuture<Optional<PlayerRecord>> findByUuid(UUID uuid) {
        return db.supplyAsync(connection -> findByUuidSync(connection, uuid));
    }

    public CompletableFuture<Optional<PlayerRecord>> findByName(String name) {
        return db.supplyAsync(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM " + db.table("players") + " WHERE LOWER(name) = LOWER(?) ORDER BY last_seen DESC")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(map(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    public CompletableFuture<Void> setLocale(UUID uuid, String locale) {
        return db.runAsync(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE " + db.table("players") + " SET locale = ? WHERE uuid = ?")) {
                ps.setString(1, locale);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            }
        });
    }

    private Optional<PlayerRecord> findByUuidSync(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM " + db.table("players") + " WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
                return Optional.empty();
            }
        }
    }

    private PlayerRecord map(ResultSet rs) throws SQLException {
        return new PlayerRecord(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("name"),
                Optional.ofNullable(rs.getString("last_ip")),
                Instant.ofEpochMilli(rs.getLong("first_seen")),
                Instant.ofEpochMilli(rs.getLong("last_seen")),
                Optional.ofNullable(rs.getString("locale"))
        );
    }
}
