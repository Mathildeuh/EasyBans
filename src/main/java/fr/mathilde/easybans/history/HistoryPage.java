package fr.mathilde.easybans.history;

import fr.mathilde.easybans.punishment.Punishment;

import java.util.List;

public record HistoryPage(List<Punishment> entries, int page, int pageSize, int totalCount) {

    public int totalPages() {
        return Math.max(1, (int) Math.ceil(totalCount / (double) pageSize));
    }
}
