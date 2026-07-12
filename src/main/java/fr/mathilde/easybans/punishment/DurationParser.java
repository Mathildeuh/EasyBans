package fr.mathilde.easybans.punishment;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses compact duration strings such as {@code "1d12h"}, {@code "30m"} or {@code "2w"} into
 * a {@link Duration}. {@code "perm"}, {@code "permanent"} and {@code "-1"} all mean "no
 * expiry" and are represented as an empty {@link Optional}, matching how expiry is stored
 * (a null {@code expires_at} column means permanent).
 *
 * <p>Supported units: s(econd), m(inute), h(our), d(ay), w(eek), mo(nth, treated as 30 days),
 * y(ear, treated as 365 days). A duration is one or more {@code <amount><unit>} tokens
 * concatenated with no separator, e.g. {@code "1mo2w3d"}.
 */
public final class DurationParser {

    private static final Pattern TOKEN = Pattern.compile("(\\d+)(mo|[smhdwy])", Pattern.CASE_INSENSITIVE);

    private DurationParser() {
    }

    public static Optional<Duration> parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Duration must not be empty");
        }
        String trimmed = input.trim().toLowerCase(Locale.ROOT);
        if (trimmed.equals("perm") || trimmed.equals("permanent") || trimmed.equals("-1")) {
            return Optional.empty();
        }

        Matcher matcher = TOKEN.matcher(trimmed);
        long totalSeconds = 0;
        int matchedChars = 0;
        boolean matchedAny = false;
        while (matcher.find()) {
            matchedAny = true;
            matchedChars += matcher.group().length();
            long amount = Long.parseLong(matcher.group(1));
            totalSeconds = Math.addExact(totalSeconds, Math.multiplyExact(amount, unitSeconds(matcher.group(2))));
        }

        if (!matchedAny || matchedChars != trimmed.length()) {
            throw new IllegalArgumentException("Invalid duration: '" + input
                    + "' (expected e.g. '1d', '2h30m', '1w', or 'permanent')");
        }
        if (totalSeconds <= 0) {
            throw new IllegalArgumentException("Duration must be positive: '" + input + "'");
        }
        return Optional.of(Duration.ofSeconds(totalSeconds));
    }

    private static long unitSeconds(String unit) {
        return switch (unit) {
            case "s" -> 1L;
            case "m" -> 60L;
            case "h" -> 3600L;
            case "d" -> 86_400L;
            case "w" -> 604_800L;
            case "mo" -> 2_592_000L;
            case "y" -> 31_536_000L;
            default -> throw new IllegalArgumentException("Unknown duration unit: " + unit);
        };
    }
}
