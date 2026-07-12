package fr.mathilde.easybans.message;

import fr.mathilde.easybans.locale.SupportedLocale;
import fr.mathilde.easybans.punishment.DurationFormatter;
import fr.mathilde.easybans.punishment.Punishment;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Builds the standard placeholder set available to every punishment-related message
 * ({@code <reason>}, {@code <staff>}, {@code <server>}, {@code <duration>}, {@code <id>},
 * {@code <time_since>}, {@code <original_date>}, {@code <original_duration>}) - the MiniMessage
 * tag equivalents of the spec's {REASON}/{STAFF}/... placeholders.
 */
public final class PunishmentFormatter {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private PunishmentFormatter() {
    }

    public static PlaceholderContext of(Punishment punishment, String serverName, MessageService messages,
                                         SupportedLocale locale) {
        String permanent = messages.getPlain(locale, "common.permanent");

        PlaceholderContext ctx = PlaceholderContext.create()
                .put("reason", punishment.reason())
                .put("staff", punishment.staffName())
                .put("server", serverName == null ? "-" : serverName)
                .put("id", String.valueOf(punishment.id()))
                .put("original_date", DATE_FORMAT.format(punishment.createdAt()))
                .put("time_since", DurationFormatter.format(Duration.between(punishment.createdAt(), Instant.now())));

        if (punishment.expiresAt().isPresent()) {
            Duration remaining = Duration.between(Instant.now(), punishment.expiresAt().get());
            ctx.put("duration", remaining.isNegative() ? "0s" : DurationFormatter.format(remaining));
            ctx.put("original_duration",
                    DurationFormatter.format(Duration.between(punishment.createdAt(), punishment.expiresAt().get())));
        } else {
            ctx.put("duration", permanent);
            ctx.put("original_duration", permanent);
        }
        return ctx;
    }
}
