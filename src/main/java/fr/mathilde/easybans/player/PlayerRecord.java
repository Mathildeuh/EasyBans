package fr.mathilde.easybans.player;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record PlayerRecord(
        UUID uuid,
        String name,
        Optional<String> lastIp,
        Instant firstSeen,
        Instant lastSeen,
        Optional<String> locale
) {
}
