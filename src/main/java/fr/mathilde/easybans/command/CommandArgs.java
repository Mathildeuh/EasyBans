package fr.mathilde.easybans.command;

import fr.mathilde.easybans.punishment.DurationParser;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Parses the {@code -s} (silent), {@code -server:<name>} (scope) and {@code -t:<templateId>} flags shared by punishment commands. */
public final class CommandArgs {

    private CommandArgs() {
    }

    public static ParsedFlags parseFlags(String[] args, int fromIndex) {
        boolean silent = false;
        String server = null;
        String template = null;
        List<String> remaining = new ArrayList<>();

        for (int i = fromIndex; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("-s")) {
                silent = true;
            } else if (arg.regionMatches(true, 0, "-server:", 0, "-server:".length())) {
                server = arg.substring("-server:".length());
            } else if (arg.regionMatches(true, 0, "-t:", 0, "-t:".length())) {
                template = arg.substring("-t:".length());
            } else {
                remaining.add(arg);
            }
        }
        return new ParsedFlags(silent, Optional.ofNullable(server), Optional.ofNullable(template), remaining);
    }

    public static String joinFrom(List<String> tokens, int fromIndex) {
        return String.join(" ", tokens.subList(fromIndex, tokens.size()));
    }

    public record DurationAndReason(Optional<Duration> duration, String reason) {
    }

    /**
     * If the first token is a parseable duration, it's consumed as such and the rest becomes
     * the reason; otherwise the whole token list is treated as a permanent-punishment reason.
     */
    public static DurationAndReason parseDurationAndReason(List<String> tokens, String defaultReason) {
        if (tokens.isEmpty()) {
            return new DurationAndReason(Optional.empty(), defaultReason);
        }
        try {
            Optional<Duration> duration = DurationParser.parse(tokens.get(0));
            String reason = tokens.size() > 1 ? joinFrom(tokens, 1) : defaultReason;
            return new DurationAndReason(duration, reason);
        } catch (IllegalArgumentException e) {
            return new DurationAndReason(Optional.empty(), joinFrom(tokens, 0));
        }
    }
}
