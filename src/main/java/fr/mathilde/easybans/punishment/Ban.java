package fr.mathilde.easybans.punishment;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record Ban(
        long id,
        UUID targetUuid,
        Optional<String> targetIp,
        boolean ipBanned,
        String reason,
        UUID staffUuid,
        String staffName,
        Optional<String> serverScope,
        String templateId,
        Optional<String> kickscreenKey,
        boolean silent,
        Instant createdAt,
        Optional<Instant> expiresAt,
        boolean active,
        Optional<RemovalInfo> removal
) implements Punishment {

    @Override
    public PunishmentType type() {
        return PunishmentType.BAN;
    }
}
