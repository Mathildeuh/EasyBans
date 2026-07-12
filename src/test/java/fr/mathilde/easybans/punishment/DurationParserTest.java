package fr.mathilde.easybans.punishment;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DurationParserTest {

    @Test
    void parsesSingleUnit() {
        assertEquals(Optional.of(Duration.ofDays(1)), DurationParser.parse("1d"));
        assertEquals(Optional.of(Duration.ofHours(3)), DurationParser.parse("3h"));
        assertEquals(Optional.of(Duration.ofMinutes(45)), DurationParser.parse("45m"));
        assertEquals(Optional.of(Duration.ofSeconds(10)), DurationParser.parse("10s"));
        assertEquals(Optional.of(Duration.ofDays(7)), DurationParser.parse("1w"));
    }

    @Test
    void parsesCompoundDurations() {
        Duration expected = Duration.ofDays(1).plusHours(2).plusMinutes(30);
        assertEquals(Optional.of(expected), DurationParser.parse("1d2h30m"));
    }

    @Test
    void monthAndYearUseApproximateLengths() {
        assertEquals(Optional.of(Duration.ofDays(30)), DurationParser.parse("1mo"));
        assertEquals(Optional.of(Duration.ofDays(365)), DurationParser.parse("1y"));
    }

    @Test
    void permanentKeywordsYieldEmptyOptional() {
        assertTrue(DurationParser.parse("perm").isEmpty());
        assertTrue(DurationParser.parse("permanent").isEmpty());
        assertTrue(DurationParser.parse("-1").isEmpty());
        assertTrue(DurationParser.parse("PERMANENT").isEmpty());
    }

    @Test
    void rejectsGarbageInput() {
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("banned for being bad"));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("10"));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("1d 2h"));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse(""));
    }

    @Test
    void rejectsZeroOrNegativeDuration() {
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("0s"));
    }

    @Test
    void rejectsOverflowingDurationAsIllegalArgumentNotArithmeticException() {
        // Long.MAX_VALUE seconds is ~9.22e18; these amounts overflow long-seconds arithmetic once
        // multiplied by their unit, and must surface as the same IllegalArgumentException every
        // other malformed duration does, not an uncaught ArithmeticException.
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("999999999999999d"));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("999999999999y"));
    }
}
