package fr.mathilde.easybans.punishment;

import java.util.Optional;

public record PunishmentActionResult<T extends Punishment>(PunishmentOutcome outcome, Optional<T> punishment) {

    public static <T extends Punishment> PunishmentActionResult<T> success(T value) {
        return new PunishmentActionResult<>(PunishmentOutcome.SUCCESS, Optional.of(value));
    }

    public static <T extends Punishment> PunishmentActionResult<T> failure(PunishmentOutcome outcome) {
        return new PunishmentActionResult<>(outcome, Optional.empty());
    }

    public boolean isSuccess() {
        return outcome == PunishmentOutcome.SUCCESS;
    }
}
