package fr.mathilde.easybans.database.dao;

import fr.mathilde.easybans.database.DatabaseProvider;
import fr.mathilde.easybans.sync.SyncEvent;
import fr.mathilde.easybans.sync.SyncEventType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class SyncEventDao {

    private final DatabaseProvider db;

    public SyncEventDao(DatabaseProvider db) {
        this.db = db;
    }

    public CompletableFuture<Void> publish(SyncEventType type, String payload, String originNode) {
        return db.runAsync(connection -> {
            String sql = "INSERT INTO " + db.table("sync_events")
                    + " (event_type, payload, origin_node, created_at) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, type.name());
                ps.setString(2, payload);
                ps.setString(3, originNode);
                ps.setLong(4, Instant.now().toEpochMilli());
                ps.executeUpdate();
            }
        });
    }

    public CompletableFuture<List<SyncEvent>> findSince(long lastSeenId) {
        return db.supplyAsync(connection -> {
            String sql = "SELECT * FROM " + db.table("sync_events") + " WHERE id > ? ORDER BY id ASC";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, lastSeenId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<SyncEvent> events = new ArrayList<>();
                    while (rs.next()) {
                        events.add(new SyncEvent(
                                rs.getLong("id"),
                                SyncEventType.valueOf(rs.getString("event_type")),
                                rs.getString("payload"),
                                rs.getString("origin_node"),
                                rs.getLong("created_at")
                        ));
                    }
                    return events;
                }
            }
        });
    }

    public CompletableFuture<Long> latestId() {
        return db.supplyAsync(connection -> {
            String sql = "SELECT MAX(id) AS v FROM " + db.table("sync_events");
            try (PreparedStatement ps = connection.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long value = rs.getLong("v");
                    return rs.wasNull() ? 0L : value;
                }
                return 0L;
            }
        });
    }
}
