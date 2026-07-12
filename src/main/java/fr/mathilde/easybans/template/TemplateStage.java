package fr.mathilde.easybans.template;

import java.time.Duration;
import java.util.Optional;

/**
 * One escalation step of a {@link PunishmentTemplate}. {@code after} is the infraction number
 * (1-based) that first triggers this stage; a player's Nth infraction on this template uses
 * the stage with the highest {@code after} value that is {@code <= N} - see
 * {@link TemplateEngine#resolveStage}.
 */
public record TemplateStage(int after, Optional<Duration> duration, String reason, Optional<String> kickscreenKey) {
}
