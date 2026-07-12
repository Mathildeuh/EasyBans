package fr.mathilde.easybans.sync;

public enum SyncEventType {
    /** A punishment was created, removed, or a warning acknowledged - payload is the affected player's UUID. */
    PUNISHMENT_CHANGED,
    /** A player changed their locale - payload is the affected player's UUID. */
    LOCALE_CHANGED
}
