package fr.mathilde.easybans.punishment;

import java.time.Instant;
import java.util.UUID;

public record RemovalInfo(UUID removedByUuid, String removedByName, Instant removedAt, String removedReason) {
}
