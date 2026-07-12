package fr.mathilde.easybans.database.dao;

import fr.mathilde.easybans.config.StorageConfig;
import fr.mathilde.easybans.database.DatabaseProvider;
import fr.mathilde.easybans.database.DatabaseType;
import fr.mathilde.easybans.database.migration.MigrationRunner;
import fr.mathilde.easybans.punishment.Ban;
import fr.mathilde.easybans.punishment.Mute;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link PunishmentDao} against a real embedded H2 database (migrations included),
 * not mocks - in particular the atomic {@code INSERT ... WHERE NOT EXISTS} conditional inserts
 * backing the anti-overwrite fix, which is the most complex SQL in the codebase and needs to
 * actually run against a database engine to be trusted, not just compile.
 */
class PunishmentDaoIntegrationTest {

    private DatabaseProvider db;
    private PunishmentDao dao;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws SQLException {
        StorageConfig config = new StorageConfig(DatabaseType.H2, "localhost", 0, "test", "test", "",
                false, "test_", 1, 5000L, "test-db");
        db = new DatabaseProvider(config, tempDir, NOPLogger.NOP_LOGGER);
        new MigrationRunner(db, NOPLogger.NOP_LOGGER).run();
        dao = new PunishmentDao(db);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void insertBanIfNoneActiveSucceedsWhenNoActiveBanExists() throws Exception {
        UUID target = UUID.randomUUID();
        Optional<Ban> result = dao.insertBanIfNoneActive(target, Optional.empty(), false, "test reason",
                UUID.randomUUID(), "Staff", Optional.empty(), null, Optional.empty(), false, Instant.now(),
                Optional.empty()).get();

        assertTrue(result.isPresent());
        assertEquals(target, result.get().targetUuid());
        assertTrue(result.get().id() > 0);
        assertTrue(result.get().active());
    }

    @Test
    void insertBanIfNoneActiveReturnsEmptyWhenAlreadyBanned() throws Exception {
        UUID target = UUID.randomUUID();
        UUID staff = UUID.randomUUID();
        Optional<Ban> first = dao.insertBanIfNoneActive(target, Optional.empty(), false, "first ban", staff,
                "Staff", Optional.empty(), null, Optional.empty(), false, Instant.now(), Optional.empty()).get();
        assertTrue(first.isPresent());

        Optional<Ban> second = dao.insertBanIfNoneActive(target, Optional.empty(), false, "second ban", staff,
                "Staff", Optional.empty(), null, Optional.empty(), false, Instant.now(), Optional.empty()).get();
        assertTrue(second.isEmpty(), "a second ban attempt while one is already active must be rejected, not create a duplicate row");

        Optional<Ban> active = dao.findAnyActiveBan(target).get();
        assertTrue(active.isPresent());
        assertEquals("first ban", active.get().reason());
    }

    @Test
    void insertBanSucceedsAfterDeactivatingExisting() throws Exception {
        UUID target = UUID.randomUUID();
        UUID staff = UUID.randomUUID();
        Ban first = dao.insertBanIfNoneActive(target, Optional.empty(), false, "first ban", staff, "Staff",
                Optional.empty(), null, Optional.empty(), false, Instant.now(), Optional.empty()).get().orElseThrow();

        int deactivated = dao.deactivateAllActive(target, "BAN", staff, "Staff", "override").get();
        assertEquals(1, deactivated);

        Optional<Ban> second = dao.insertBanIfNoneActive(target, Optional.empty(), false, "override ban", staff,
                "Staff", Optional.empty(), null, Optional.empty(), false, Instant.now(), Optional.empty()).get();
        assertTrue(second.isPresent(), "insert should succeed once the previous ban has been deactivated");
        assertNotEquals(first.id(), second.get().id());
    }

    @Test
    void insertMuteIfNoneActiveReturnsEmptyWhenAlreadyMuted() throws Exception {
        UUID target = UUID.randomUUID();
        UUID staff = UUID.randomUUID();
        Optional<Mute> first = dao.insertMuteIfNoneActive(target, "first mute", staff, "Staff", Optional.empty(),
                null, false, Instant.now(), Optional.empty()).get();
        assertTrue(first.isPresent());

        Optional<Mute> second = dao.insertMuteIfNoneActive(target, "second mute", staff, "Staff", Optional.empty(),
                null, false, Instant.now(), Optional.empty()).get();
        assertTrue(second.isEmpty());
    }

    @Test
    void expiredBanDoesNotBlockANewOne() throws Exception {
        UUID target = UUID.randomUUID();
        UUID staff = UUID.randomUUID();
        Optional<Ban> expired = dao.insertBanIfNoneActive(target, Optional.empty(), false, "expired ban", staff,
                "Staff", Optional.empty(), null, Optional.empty(), false, Instant.now().minusSeconds(120),
                Optional.of(Instant.now().minusSeconds(60))).get();
        assertTrue(expired.isPresent());

        Optional<Ban> fresh = dao.insertBanIfNoneActive(target, Optional.empty(), false, "fresh ban", staff,
                "Staff", Optional.empty(), null, Optional.empty(), false, Instant.now(), Optional.empty()).get();
        assertTrue(fresh.isPresent(), "an already-expired ban must not block a new one");
    }

    @Test
    void bansAndMutesDoNotBlockEachOther() throws Exception {
        UUID target = UUID.randomUUID();
        UUID staff = UUID.randomUUID();
        Optional<Ban> ban = dao.insertBanIfNoneActive(target, Optional.empty(), false, "ban", staff, "Staff",
                Optional.empty(), null, Optional.empty(), false, Instant.now(), Optional.empty()).get();
        Optional<Mute> mute = dao.insertMuteIfNoneActive(target, "mute", staff, "Staff", Optional.empty(), null,
                false, Instant.now(), Optional.empty()).get();

        assertTrue(ban.isPresent());
        assertTrue(mute.isPresent(), "an active ban must not block an unrelated mute for the same player");
    }
}
