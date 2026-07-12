package fr.mathilde.easybans.message;

import fr.mathilde.easybans.locale.SupportedLocale;
import fr.mathilde.easybans.punishment.Punishment;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class HistoryEntryFormatter {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private HistoryEntryFormatter() {
    }

    public static PlaceholderContext forEntry(Punishment punishment, String targetName, MessageService messages,
                                               SupportedLocale locale) {
        String statusKey = punishment.isCurrentlyActive() ? "common.status-active" : "common.status-inactive";
        return PlaceholderContext.create()
                .put("type", punishment.type().name())
                .put("id", String.valueOf(punishment.id()))
                .put("target", targetName)
                .put("staff", punishment.staffName())
                .put("reason", punishment.reason())
                .put("date", DATE_FORMAT.format(punishment.createdAt()))
                .put("status", messages.getPlain(locale, statusKey));
    }
}
