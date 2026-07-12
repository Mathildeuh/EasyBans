package fr.mathilde.easybans.rollback;

import fr.mathilde.easybans.database.dao.PunishmentDao;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bulk-deactivates every active punishment issued by a given staff member. Requires an
 * explicit two-step confirmation ({@code /easybans rollback <staff>} then
 * {@code /easybans rollback confirm} within {@link #CONFIRM_WINDOW}) since this action can
 * affect a large number of rows and cannot be undone.
 */
public final class RollbackService {

    private static final Duration CONFIRM_WINDOW = Duration.ofSeconds(30);

    private final PunishmentDao dao;
    private final Map<UUID, PendingRollback> pending = new ConcurrentHashMap<>();

    public RollbackService(PunishmentDao dao) {
        this.dao = dao;
    }

    private record PendingRollback(UUID targetStaff, String targetStaffName, Instant requestedAt) {
    }

    public void requestRollback(UUID executor, UUID targetStaff, String targetStaffName) {
        pending.put(executor, new PendingRollback(targetStaff, targetStaffName, Instant.now()));
    }

    public Optional<String> pendingTargetName(UUID executor) {
        PendingRollback request = pending.get(executor);
        if (request == null) {
            return Optional.empty();
        }
        if (Duration.between(request.requestedAt(), Instant.now()).compareTo(CONFIRM_WINDOW) > 0) {
            pending.remove(executor);
            return Optional.empty();
        }
        return Optional.of(request.targetStaffName());
    }

    /** Returns empty if there was no pending (or the confirmation window expired), otherwise the number of rows rolled back. */
    public CompletableFuture<Optional<Integer>> confirmRollback(UUID executor, String executorName) {
        PendingRollback request = pending.remove(executor);
        if (request == null || Duration.between(request.requestedAt(), Instant.now()).compareTo(CONFIRM_WINDOW) > 0) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return dao.rollbackStaffPunishments(request.targetStaff(), executor, executorName).thenApply(Optional::of);
    }
}
