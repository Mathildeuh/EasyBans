package fr.mathilde.easybans.database.dao;

import fr.mathilde.easybans.database.DatabaseProvider;
import fr.mathilde.easybans.punishment.Ban;
import fr.mathilde.easybans.punishment.Kick;
import fr.mathilde.easybans.punishment.Mute;
import fr.mathilde.easybans.punishment.Note;
import fr.mathilde.easybans.punishment.Punishment;
import fr.mathilde.easybans.punishment.PunishmentType;
import fr.mathilde.easybans.punishment.RemovalInfo;
import fr.mathilde.easybans.punishment.Warning;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Data access for the unified {@code punishments} table (see V1InitialSchema for the
 * rationale behind one table with a {@code type} discriminator instead of five).
 */
public final class PunishmentDao {

    private final DatabaseProvider db;

    public PunishmentDao(DatabaseProvider db) {
        this.db = db;
    }

    // ---------------------------------------------------------------- inserts

    public CompletableFuture<Ban> insertBan(UUID target, Optional<String> targetIp, boolean ipBanned, String reason,
                                             UUID staffUuid, String staffName, Optional<String> serverScope,
                                             String templateId, Optional<String> kickscreenKey, boolean silent,
                                             Instant createdAt, Optional<Instant> expiresAt) {
        return db.supplyAsync(connection -> {
            long id = insertRow(connection, "BAN", target, targetIp.orElse(null), ipBanned, null, reason,
                    staffUuid, staffName, serverScope.orElse(null), templateId, kickscreenKey.orElse(null), silent,
                    true, createdAt.toEpochMilli(), expiresAt.map(Instant::toEpochMilli).orElse(null));
            return new Ban(id, target, targetIp, ipBanned, reason, staffUuid, staffName, serverScope, templateId,
                    kickscreenKey, silent, createdAt, expiresAt, true, Optional.empty());
        });
    }

    public CompletableFuture<Mute> insertMute(UUID target, String reason, UUID staffUuid, String staffName,
                                               Optional<String> serverScope, String templateId, boolean silent,
                                               Instant createdAt, Optional<Instant> expiresAt) {
        return db.supplyAsync(connection -> {
            long id = insertRow(connection, "MUTE", target, null, false, null, reason,
                    staffUuid, staffName, serverScope.orElse(null), templateId, null, silent, true,
                    createdAt.toEpochMilli(), expiresAt.map(Instant::toEpochMilli).orElse(null));
            return new Mute(id, target, reason, staffUuid, staffName, serverScope, templateId, silent, createdAt,
                    expiresAt, true, Optional.empty());
        });
    }

    public CompletableFuture<Warning> insertWarning(UUID target, String category, String reason, UUID staffUuid,
                                                      String staffName, Optional<String> serverScope, boolean silent,
                                                      Instant createdAt, Optional<Instant> expiresAt,
                                                      boolean targetOnline) {
        return db.supplyAsync(connection -> {
            long id = insertRow(connection, "WARN", target, null, false, category, reason,
                    staffUuid, staffName, serverScope.orElse(null), null, null, silent, targetOnline,
                    createdAt.toEpochMilli(), expiresAt.map(Instant::toEpochMilli).orElse(null));
            return new Warning(id, target, category, reason, staffUuid, staffName, serverScope, silent, createdAt,
                    expiresAt, true, Optional.empty(), targetOnline);
        });
    }

    public CompletableFuture<Kick> insertKick(UUID target, String reason, UUID staffUuid, String staffName,
                                               Optional<String> serverScope, Optional<String> kickscreenKey,
                                               boolean silent, Instant createdAt) {
        return db.supplyAsync(connection -> {
            long id = insertRow(connection, "KICK", target, null, false, null, reason,
                    staffUuid, staffName, serverScope.orElse(null), null, kickscreenKey.orElse(null), silent, true,
                    createdAt.toEpochMilli(), null);
            return new Kick(id, target, reason, staffUuid, staffName, serverScope, kickscreenKey, silent, createdAt);
        });
    }

    public CompletableFuture<Note> insertNote(UUID target, String reason, UUID staffUuid, String staffName,
                                               Instant createdAt) {
        return db.supplyAsync(connection -> {
            long id = insertRow(connection, "NOTE", target, null, false, null, reason,
                    staffUuid, staffName, null, null, null, true, true, createdAt.toEpochMilli(), null);
            return new Note(id, target, reason, staffUuid, staffName, createdAt);
        });
    }

    private long insertRow(Connection connection, String type, UUID target, String targetIp, boolean ipBanned,
                            String category, String reason, UUID staffUuid, String staffName, String serverScope,
                            String templateId, String kickscreenKey, boolean silent, boolean acknowledged,
                            long createdAt, Long expiresAt) throws SQLException {
        String sql = "INSERT INTO " + db.table("punishments") + " (type, target_uuid, target_ip, ip_banned, "
                + "category, reason, staff_uuid, staff_name, server_scope, template_id, kickscreen_key, silent, "
                + "active, acknowledged, created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, "
                + "?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, type);
            ps.setString(2, target.toString());
            ps.setString(3, targetIp);
            ps.setBoolean(4, ipBanned);
            ps.setString(5, category);
            ps.setString(6, reason);
            ps.setString(7, staffUuid.toString());
            ps.setString(8, staffName);
            ps.setString(9, serverScope);
            ps.setString(10, templateId);
            ps.setString(11, kickscreenKey);
            ps.setBoolean(12, silent);
            ps.setBoolean(13, acknowledged);
            ps.setLong(14, createdAt);
            if (expiresAt != null) {
                ps.setLong(15, expiresAt);
            } else {
                ps.setNull(15, java.sql.Types.BIGINT);
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
                throw new SQLException("Insert into punishments did not return a generated id");
            }
        }
    }

    /**
     * Atomically inserts a ban only if the target has no currently-active ban, in a single SQL
     * statement (INSERT ... SELECT ... WHERE NOT EXISTS) - this closes the check-then-insert
     * race that a separate findAnyActiveBan()-then-insertBan() pair has under concurrent calls
     * for the same target (two staff banning at once, or an auto-ban racing a manual one).
     * Returns empty if a currently-active ban already existed (no row was inserted).
     */
    public CompletableFuture<Optional<Ban>> insertBanIfNoneActive(UUID target, Optional<String> targetIp,
                                                                    boolean ipBanned, String reason, UUID staffUuid,
                                                                    String staffName, Optional<String> serverScope,
                                                                    String templateId, Optional<String> kickscreenKey,
                                                                    boolean silent, Instant createdAt,
                                                                    Optional<Instant> expiresAt) {
        return db.supplyAsync(connection -> {
            Long id = insertRowIfNoneActive(connection, "BAN", target, targetIp.orElse(null), ipBanned, reason,
                    staffUuid, staffName, serverScope.orElse(null), templateId, kickscreenKey.orElse(null), silent,
                    createdAt.toEpochMilli(), expiresAt.map(Instant::toEpochMilli).orElse(null));
            if (id == null) {
                return Optional.empty();
            }
            return Optional.of(new Ban(id, target, targetIp, ipBanned, reason, staffUuid, staffName, serverScope,
                    templateId, kickscreenKey, silent, createdAt, expiresAt, true, Optional.empty()));
        });
    }

    /** Same guarantee as {@link #insertBanIfNoneActive} but for mutes. */
    public CompletableFuture<Optional<Mute>> insertMuteIfNoneActive(UUID target, String reason, UUID staffUuid,
                                                                      String staffName, Optional<String> serverScope,
                                                                      String templateId, boolean silent,
                                                                      Instant createdAt, Optional<Instant> expiresAt) {
        return db.supplyAsync(connection -> {
            Long id = insertRowIfNoneActive(connection, "MUTE", target, null, false, reason, staffUuid, staffName,
                    serverScope.orElse(null), templateId, null, silent, createdAt.toEpochMilli(),
                    expiresAt.map(Instant::toEpochMilli).orElse(null));
            if (id == null) {
                return Optional.empty();
            }
            return Optional.of(new Mute(id, target, reason, staffUuid, staffName, serverScope, templateId, silent,
                    createdAt, expiresAt, true, Optional.empty()));
        });
    }

    private Long insertRowIfNoneActive(Connection connection, String type, UUID target, String targetIp,
                                        boolean ipBanned, String reason, UUID staffUuid, String staffName,
                                        String serverScope, String templateId, String kickscreenKey, boolean silent,
                                        long createdAt, Long expiresAt) throws SQLException {
        String table = db.table("punishments");
        String sql = "INSERT INTO " + table + " (type, target_uuid, target_ip, ip_banned, category, reason, "
                + "staff_uuid, staff_name, server_scope, template_id, kickscreen_key, silent, active, acknowledged, "
                + "created_at, expires_at) "
                + "SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, TRUE, ?, ? "
                + "WHERE NOT EXISTS (SELECT 1 FROM " + table + " WHERE type = ? AND target_uuid = ? "
                + "AND active = TRUE AND (expires_at IS NULL OR expires_at > ?))";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int i = 1;
            ps.setString(i++, type);
            ps.setString(i++, target.toString());
            ps.setString(i++, targetIp);
            ps.setBoolean(i++, ipBanned);
            ps.setString(i++, null); // category: only meaningful for WARN, never used by ban/mute
            ps.setString(i++, reason);
            ps.setString(i++, staffUuid.toString());
            ps.setString(i++, staffName);
            ps.setString(i++, serverScope);
            ps.setString(i++, templateId);
            ps.setString(i++, kickscreenKey);
            ps.setBoolean(i++, silent);
            ps.setLong(i++, createdAt);
            if (expiresAt != null) {
                ps.setLong(i++, expiresAt);
            } else {
                ps.setNull(i++, java.sql.Types.BIGINT);
            }
            ps.setString(i++, type);
            ps.setString(i++, target.toString());
            ps.setLong(i, Instant.now().toEpochMilli());

            int affected = ps.executeUpdate();
            if (affected == 0) {
                return null;
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
                throw new SQLException("Conditional insert into punishments affected a row but returned no generated id");
            }
        }
    }

    /** Deactivates every currently-active punishment of the given type for a target - used by the override path. */
    public CompletableFuture<Integer> deactivateAllActive(UUID target, String type, UUID removedBy,
                                                           String removedByName, String reason) {
        return db.supplyAsync(connection -> {
            String sql = "UPDATE " + db.table("punishments") + " SET active = FALSE, removed_by_uuid = ?, "
                    + "removed_by_name = ?, removed_at = ?, removed_reason = ? "
                    + "WHERE target_uuid = ? AND type = ? AND active = TRUE";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, removedBy.toString());
                ps.setString(2, removedByName);
                ps.setLong(3, Instant.now().toEpochMilli());
                ps.setString(4, reason);
                ps.setString(5, target.toString());
                ps.setString(6, type);
                return ps.executeUpdate();
            }
        });
    }

    // ---------------------------------------------------------------- active lookups

    public CompletableFuture<Optional<Ban>> findActiveBan(UUID target, String server) {
        return findActiveOfType(target, server, "BAN").thenApply(p -> p.map(x -> (Ban) x));
    }

    public CompletableFuture<Optional<Ban>> findActiveIpBan(String ip, String server) {
        return db.supplyAsync(connection -> {
            String sql = "SELECT * FROM " + db.table("punishments") + " WHERE type = 'BAN' AND ip_banned = TRUE "
                    + "AND target_ip = ? AND active = TRUE AND (expires_at IS NULL OR expires_at > ?) "
                    + "AND (server_scope IS NULL OR server_scope = ?) ORDER BY created_at DESC";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, ip);
                ps.setLong(2, Instant.now().toEpochMilli());
                ps.setString(3, server);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of((Ban) map(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    public CompletableFuture<Optional<Mute>> findActiveMute(UUID target, String server) {
        return findActiveOfType(target, server, "MUTE").thenApply(p -> p.map(x -> (Mute) x));
    }

    /** Any currently-active ban regardless of server scope - used for the anti-overwrite check. */
    public CompletableFuture<Optional<Ban>> findAnyActiveBan(UUID target) {
        return findAnyActiveOfType(target, "BAN").thenApply(p -> p.map(x -> (Ban) x));
    }

    /** Any currently-active mute regardless of server scope - used for the anti-overwrite check. */
    public CompletableFuture<Optional<Mute>> findAnyActiveMute(UUID target) {
        return findAnyActiveOfType(target, "MUTE").thenApply(p -> p.map(x -> (Mute) x));
    }

    /** Any currently-active IP ban on this address, regardless of server scope - backs {@code /pardon-ip}. */
    public CompletableFuture<Optional<Ban>> findAnyActiveIpBan(String ip) {
        return db.supplyAsync(connection -> {
            String sql = "SELECT * FROM " + db.table("punishments") + " WHERE type = 'BAN' AND ip_banned = TRUE "
                    + "AND target_ip = ? AND active = TRUE AND (expires_at IS NULL OR expires_at > ?) "
                    + "ORDER BY created_at DESC";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, ip);
                ps.setLong(2, Instant.now().toEpochMilli());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of((Ban) map(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    /** Currently-active bans, newest first - backs {@code /banlist} (vanilla's own ban listing command). */
    public CompletableFuture<List<Ban>> activeBans(boolean ipOnly, int page, int pageSize) {
        return db.supplyAsync(connection -> {
            String sql = "SELECT * FROM " + db.table("punishments") + " WHERE type = 'BAN' AND ip_banned = ? "
                    + "AND active = TRUE AND (expires_at IS NULL OR expires_at > ?) "
                    + "ORDER BY created_at DESC LIMIT ? OFFSET ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setBoolean(1, ipOnly);
                ps.setLong(2, Instant.now().toEpochMilli());
                ps.setInt(3, pageSize);
                ps.setInt(4, Math.max(0, page) * pageSize);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Ban> result = new ArrayList<>();
                    while (rs.next()) {
                        result.add((Ban) map(rs));
                    }
                    return result;
                }
            }
        });
    }

    public CompletableFuture<Integer> countActiveBans(boolean ipOnly) {
        return db.supplyAsync(connection -> {
            String sql = "SELECT COUNT(*) FROM " + db.table("punishments") + " WHERE type = 'BAN' AND ip_banned = ? "
                    + "AND active = TRUE AND (expires_at IS NULL OR expires_at > ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setBoolean(1, ipOnly);
                ps.setLong(2, Instant.now().toEpochMilli());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        });
    }

    private CompletableFuture<Optional<Punishment>> findAnyActiveOfType(UUID target, String type) {
        return db.supplyAsync(connection -> {
            String sql = "SELECT * FROM " + db.table("punishments") + " WHERE type = ? AND target_uuid = ? "
                    + "AND active = TRUE AND (expires_at IS NULL OR expires_at > ?) ORDER BY created_at DESC";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, type);
                ps.setString(2, target.toString());
                ps.setLong(3, Instant.now().toEpochMilli());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(map(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    private CompletableFuture<Optional<Punishment>> findActiveOfType(UUID target, String server, String type) {
        return db.supplyAsync(connection -> {
            String sql = "SELECT * FROM " + db.table("punishments") + " WHERE type = ? AND target_uuid = ? "
                    + "AND active = TRUE AND (expires_at IS NULL OR expires_at > ?) "
                    + "AND (server_scope IS NULL OR server_scope = ?) ORDER BY created_at DESC";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, type);
                ps.setString(2, target.toString());
                ps.setLong(3, Instant.now().toEpochMilli());
                ps.setString(4, server);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(map(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    // ---------------------------------------------------------------- mutation

    public CompletableFuture<Optional<Punishment>> findById(long id) {
        return db.supplyAsync(connection -> {
            String sql = "SELECT * FROM " + db.table("punishments") + " WHERE id = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(map(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    public CompletableFuture<Boolean> deactivate(long id, UUID removedBy, String removedByName, String reason) {
        return db.supplyAsync(connection -> {
            String sql = "UPDATE " + db.table("punishments") + " SET active = FALSE, removed_by_uuid = ?, "
                    + "removed_by_name = ?, removed_at = ?, removed_reason = ? WHERE id = ? AND active = TRUE";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, removedBy.toString());
                ps.setString(2, removedByName);
                ps.setLong(3, Instant.now().toEpochMilli());
                ps.setString(4, reason);
                ps.setLong(5, id);
                return ps.executeUpdate() > 0;
            }
        });
    }

    public CompletableFuture<Integer> rollbackStaffPunishments(UUID staffUuid, UUID executedBy, String executedByName) {
        return db.supplyAsync(connection -> {
            String sql = "UPDATE " + db.table("punishments") + " SET active = FALSE, removed_by_uuid = ?, "
                    + "removed_by_name = ?, removed_at = ?, removed_reason = ? "
                    + "WHERE staff_uuid = ? AND active = TRUE AND type IN ('BAN', 'MUTE', 'WARN')";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, executedBy.toString());
                ps.setString(2, executedByName);
                ps.setLong(3, Instant.now().toEpochMilli());
                ps.setString(4, "Rollback of all punishments issued by staff, executed by " + executedByName);
                ps.setString(5, staffUuid.toString());
                return ps.executeUpdate();
            }
        });
    }

    public CompletableFuture<List<Warning>> unacknowledgedWarnings(UUID target) {
        return db.supplyAsync(connection -> {
            String sql = "SELECT * FROM " + db.table("punishments")
                    + " WHERE type = 'WARN' AND target_uuid = ? AND acknowledged = FALSE ORDER BY created_at ASC";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, target.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    List<Warning> result = new ArrayList<>();
                    while (rs.next()) {
                        result.add((Warning) map(rs));
                    }
                    return result;
                }
            }
        });
    }

    public CompletableFuture<Void> acknowledgeWarnings(UUID target) {
        return db.runAsync(connection -> {
            String sql = "UPDATE " + db.table("punishments") + " SET acknowledged = TRUE "
                    + "WHERE type = 'WARN' AND target_uuid = ? AND acknowledged = FALSE";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, target.toString());
                ps.executeUpdate();
            }
        });
    }

    // ---------------------------------------------------------------- escalation counters

    /** Total number of BAN/MUTE punishments ever issued to this player via the given template. */
    public CompletableFuture<Integer> countByTemplate(UUID target, String templateId) {
        return db.supplyAsync(connection -> {
            String sql = "SELECT COUNT(*) FROM " + db.table("punishments")
                    + " WHERE target_uuid = ? AND template_id = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, target.toString());
                ps.setString(2, templateId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        });
    }

    /** Total number of warnings ever issued to this player in the given category. */
    public CompletableFuture<Integer> countByCategory(UUID target, String category) {
        return db.supplyAsync(connection -> {
            String sql = "SELECT COUNT(*) FROM " + db.table("punishments")
                    + " WHERE type = 'WARN' AND target_uuid = ? AND category = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, target.toString());
                ps.setString(2, category);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        });
    }

    // ---------------------------------------------------------------- history

    public CompletableFuture<List<Punishment>> playerHistory(UUID target, int page, int pageSize) {
        return pagedQuery("target_uuid = ?", target.toString(), page, pageSize);
    }

    public CompletableFuture<Integer> countPlayerHistory(UUID target) {
        return countQuery("target_uuid = ?", target.toString());
    }

    public CompletableFuture<List<Punishment>> staffHistory(UUID staff, int page, int pageSize) {
        return pagedQuery("staff_uuid = ?", staff.toString(), page, pageSize);
    }

    public CompletableFuture<Integer> countStaffHistory(UUID staff) {
        return countQuery("staff_uuid = ?", staff.toString());
    }

    private CompletableFuture<List<Punishment>> pagedQuery(String whereClause, String param, int page, int pageSize) {
        return db.supplyAsync(connection -> {
            String sql = "SELECT * FROM " + db.table("punishments") + " WHERE " + whereClause
                    + " ORDER BY created_at DESC LIMIT ? OFFSET ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, param);
                ps.setInt(2, pageSize);
                ps.setInt(3, Math.max(0, page) * pageSize);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Punishment> result = new ArrayList<>();
                    while (rs.next()) {
                        result.add(map(rs));
                    }
                    return result;
                }
            }
        });
    }

    private CompletableFuture<Integer> countQuery(String whereClause, String param) {
        return db.supplyAsync(connection -> {
            String sql = "SELECT COUNT(*) FROM " + db.table("punishments") + " WHERE " + whereClause;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, param);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        });
    }

    // ---------------------------------------------------------------- row mapping

    private Punishment map(ResultSet rs) throws SQLException {
        PunishmentType type = PunishmentType.valueOf(rs.getString("type"));
        long id = rs.getLong("id");
        UUID target = UUID.fromString(rs.getString("target_uuid"));
        String reason = rs.getString("reason");
        UUID staffUuid = UUID.fromString(rs.getString("staff_uuid"));
        String staffName = rs.getString("staff_name");
        Optional<String> serverScope = Optional.ofNullable(rs.getString("server_scope"));
        boolean silent = rs.getBoolean("silent");
        Instant createdAt = Instant.ofEpochMilli(rs.getLong("created_at"));
        long expiresAtRaw = rs.getLong("expires_at");
        Optional<Instant> expiresAt = rs.wasNull() ? Optional.empty() : Optional.of(Instant.ofEpochMilli(expiresAtRaw));
        boolean active = rs.getBoolean("active");
        Optional<RemovalInfo> removal = mapRemoval(rs);
        String templateId = rs.getString("template_id");
        Optional<String> kickscreenKey = Optional.ofNullable(rs.getString("kickscreen_key"));

        return switch (type) {
            case BAN -> new Ban(id, target, Optional.ofNullable(rs.getString("target_ip")),
                    rs.getBoolean("ip_banned"), reason, staffUuid, staffName, serverScope, templateId,
                    kickscreenKey, silent, createdAt, expiresAt, active, removal);
            case MUTE -> new Mute(id, target, reason, staffUuid, staffName, serverScope, templateId, silent,
                    createdAt, expiresAt, active, removal);
            case WARN -> new Warning(id, target, rs.getString("category"), reason, staffUuid, staffName,
                    serverScope, silent, createdAt, expiresAt, active, removal, rs.getBoolean("acknowledged"));
            case KICK -> new Kick(id, target, reason, staffUuid, staffName, serverScope, kickscreenKey, silent,
                    createdAt);
            case NOTE -> new Note(id, target, reason, staffUuid, staffName, createdAt);
        };
    }

    private Optional<RemovalInfo> mapRemoval(ResultSet rs) throws SQLException {
        String removedByUuid = rs.getString("removed_by_uuid");
        if (removedByUuid == null) {
            return Optional.empty();
        }
        return Optional.of(new RemovalInfo(
                UUID.fromString(removedByUuid),
                rs.getString("removed_by_name"),
                Instant.ofEpochMilli(rs.getLong("removed_at")),
                rs.getString("removed_reason")
        ));
    }
}
