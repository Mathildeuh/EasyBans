package fr.mathilde.easybans.punishment;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record Mute(
        long id,
        UUID targetUuid,
        String reason,
        UUID staffUuid,
        String staffName,
        Optional<String> serverScope,
        String templateId,
        boolean silent,
        Instant createdAt,
        Optional<Instant> expiresAt,
        boolean active,
        Optional<RemovalInfo> removal
) implements Punishment {

    @Override
    public PunishmentType type() {
        return PunishmentType.MUTE;
    }
}
