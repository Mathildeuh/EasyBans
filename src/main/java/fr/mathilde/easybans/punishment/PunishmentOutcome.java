package fr.mathilde.easybans.punishment;

public enum PunishmentOutcome {
    SUCCESS,
    /** An active punishment of the same kind already exists and the staff lacks the override permission. */
    ALREADY_PUNISHED,
    TEMPLATE_NOT_FOUND,
    TEMPLATE_WRONG_TYPE,
    NOT_FOUND
}
