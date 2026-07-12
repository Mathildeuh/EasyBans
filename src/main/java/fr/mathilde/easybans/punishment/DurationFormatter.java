package fr.mathilde.easybans.punishment;

import java.time.Duration;

/**
 * Compact numeric duration formatting ("3d 2h 15m") shared by every locale rather than
 * spelled-out words - keeps kickscreen/history output consistent across languages without
 * needing a full set of pluralization rules per locale for "day(s)/jour(s)/tag(e)/...".
 */
public final class DurationFormatter {

    private DurationFormatter() {
    }

    public static String format(Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            return "0s";
        }
        long totalSeconds = duration.getSeconds();
        long days = totalSeconds / 86_400;
        totalSeconds %= 86_400;
        long hours = totalSeconds / 3_600;
        totalSeconds %= 3_600;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0) {
            sb.append(minutes).append("m ");
        }
        if (seconds > 0 || sb.isEmpty()) {
            sb.append(seconds).append("s");
        }
        return sb.toString().trim();
    }
}
