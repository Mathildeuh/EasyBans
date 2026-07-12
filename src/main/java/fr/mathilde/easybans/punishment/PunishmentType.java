package fr.mathilde.easybans.punishment;

public enum PunishmentType {
    BAN,
    MUTE,
    WARN,
    KICK,
    NOTE;

    /** Whether this punishment type has a concept of "currently active" / expiry. */
    public boolean isTimed() {
        return this == BAN || this == MUTE || this == WARN;
    }
}
