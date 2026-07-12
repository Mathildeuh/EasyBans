package fr.mathilde.easybans.punishment;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record Note(
        long id,
        UUID targetUuid,
        String reason,
        UUID staffUuid,
        String staffName,
        Instant createdAt
) implements Punishment {

    @Override
    public PunishmentType type() {
        return PunishmentType.NOTE;
    }

    @Override
    public String templateId() {
        return null;
    }

    @Override
    public Optional<String> serverScope() {
        return Optional.empty();
    }

    @Override
    public boolean silent() {
        return true;
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
