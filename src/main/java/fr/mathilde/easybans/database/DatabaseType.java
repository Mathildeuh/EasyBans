package fr.mathilde.easybans.database;

/**
 * Supported storage backends. MYSQL and MARIADB both use the MariaDB Connector/J driver
 * (it speaks the MySQL wire protocol and avoids pulling in the GPL-licensed MySQL
 * Connector/J) - see ARCHITECTURE.md for the rationale.
 */
public enum DatabaseType {
    H2,
    MYSQL,
    MARIADB,
    POSTGRESQL;

    public static DatabaseType fromConfig(String value) {
        if (value == null) {
            return H2;
        }
        return switch (value.trim().toLowerCase()) {
            case "h2" -> H2;
            case "mysql" -> MYSQL;
            case "mariadb" -> MARIADB;
            case "postgres", "postgresql" -> POSTGRESQL;
            default -> H2;
        };
    }

    /** Whether {@code value} is one of the strings {@link #fromConfig} maps to something other than its H2 fallback. */
    public static boolean isRecognized(String value) {
        if (value == null) {
            return true; // absent is a legitimate way to ask for the default
        }
        return switch (value.trim().toLowerCase()) {
            case "h2", "mysql", "mariadb", "postgres", "postgresql" -> true;
            default -> false;
        };
    }

    public boolean isMysqlProtocol() {
        return this == MYSQL || this == MARIADB;
    }
}
