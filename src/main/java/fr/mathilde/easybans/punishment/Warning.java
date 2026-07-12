package fr.mathilde.easybans.punishment;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record Warning(
        long id,
        UUID targetUuid,
        String category,
        String reason,
        UUID staffUuid,
        String staffName,
        Optional<String> serverScope,
        boolean silent,
        Instant createdAt,
        Optional<Instant> expiresAt,
        boolean active,
        Optional<RemovalInfo> removal,
        boolean acknowledged
) implements Punishment {

    @Override
    public PunishmentType type() {
        return PunishmentType.WARN;
    }

    @Override
    public String templateId() {
        return null;
    }
}
