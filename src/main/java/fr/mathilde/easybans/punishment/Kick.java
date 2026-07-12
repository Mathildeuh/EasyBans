package fr.mathilde.easybans.punishment;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record Kick(
        long id,
        UUID targetUuid,
        String reason,
        UUID staffUuid,
        String staffName,
        Optional<String> serverScope,
        Optional<String> kickscreenKey,
        boolean silent,
        Instant createdAt
) implements Punishment {

    @Override
    public PunishmentType type() {
        return PunishmentType.KICK;
    }

    @Override
    public String templateId() {
        return null;
    }

    @Override
    public Optional<Instant> expiresAt() {
        return Optional.empty();
    }

    @Override
    public boolean active() {
        return false;
    }

    @Override
    public Optional<RemovalInfo> removal() {
        return Optional.empty();
    }
}
