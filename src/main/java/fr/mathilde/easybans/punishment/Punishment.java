package fr.mathilde.easybans.punishment;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Common surface shared by every punishment kind. Modeled as a sealed interface over records
 * (rather than one row-shaped class with a bunch of always-null fields) so each punishment
 * kind only carries the fields that actually make sense for it - {@link Ban#targetIp()} has
 * no equivalent on {@link Warning}, for instance - while callers that only care about the
 * shared behaviour (is it active? when does it expire?) can work against this interface.
 */
public sealed interface Punishment permits Ban, Mute, Warning, Kick, Note {

    long id();

    PunishmentType type();

    UUID targetUuid();

    String reason();

    UUID staffUuid();

    String staffName();

    /** Empty means the punishment applies network-wide. */
    Optional<String> serverScope();

    /** Template id that produced this punishment, if it was auto-escalated. Null if manual. */
    String templateId();

    boolean silent();

    Instant createdAt();

    /** Empty means permanent (or not applicable, e.g. for kicks/notes). */
    Optional<Instant> expiresAt();

    boolean active();

    Optional<RemovalInfo> removal();

    default boolean isPermanent() {
        return expiresAt().isEmpty();
    }

    default boolean isExpired() {
        return expiresAt().map(e -> e.isBefore(Instant.now())).orElse(false);
    }

    /** True if this punishment is still in effect right now. */
    default boolean isCurrentlyActive() {
        return active() && !isExpired();
    }

    default boolean appliesToServer(String serverName) {
        return serverScope().isEmpty() || serverScope().get().equalsIgnoreCase(serverName);
    }
}
