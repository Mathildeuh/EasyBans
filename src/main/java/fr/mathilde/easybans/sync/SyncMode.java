package fr.mathilde.easybans.sync;

/**
 * Chosen sync strategy for propagating punishments across proxy instances sharing one
 * database. DATABASE (a lightweight poll of a {@code easybans_sync_events} table) is the
 * default because it needs zero extra infrastructure - every deployment already has the
 * database. REDIS trades a small extra dependency for near-instant propagation and no
 * polling overhead, useful for large networks. NONE disables cross-instance invalidation
 * entirely (fine for single-proxy setups).
 */
public enum SyncMode {
    NONE,
    DATABASE,
    REDIS;

    public static SyncMode fromConfig(String value) {
        if (value == null) {
            return DATABASE;
        }
        return switch (value.trim().toLowerCase()) {
            case "redis" -> REDIS;
            case "none", "off", "disabled" -> NONE;
            case "database", "db" -> DATABASE;
            default -> DATABASE;
        };
    }

    /** Whether {@code value} is one of the strings {@link #fromConfig} maps to something other than its default fallback. */
    public static boolean isRecognized(String value) {
        if (value == null) {
            return true; // absent is a legitimate way to ask for the default
        }
        return switch (value.trim().toLowerCase()) {
            case "redis", "none", "off", "disabled", "database", "db" -> true;
            default -> false;
        };
    }
}
