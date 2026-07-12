package fr.mathilde.easybans.history;

import fr.mathilde.easybans.database.dao.PunishmentDao;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class HistoryService {

    private final PunishmentDao dao;

    public HistoryService(PunishmentDao dao) {
        this.dao = dao;
    }

    public CompletableFuture<HistoryPage> playerHistory(UUID target, int page, int pageSize) {
        return dao.playerHistory(target, page, pageSize)
                .thenCombine(dao.countPlayerHistory(target), (entries, total) -> new HistoryPage(entries, page, pageSize, total));
    }

    public CompletableFuture<HistoryPage> staffHistory(UUID staff, int page, int pageSize) {
        return dao.staffHistory(staff, page, pageSize)
                .thenCombine(dao.countStaffHistory(staff), (entries, total) -> new HistoryPage(entries, page, pageSize, total));
    }
}
