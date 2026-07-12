package fr.mathilde.easybans.database.migration;

import fr.mathilde.easybans.database.DatabaseType;

/** Small dialect-specific SQL fragments shared by migrations. */
public final class SchemaSupport {

    private SchemaSupport() {
    }

    /** Column definition for an auto-incrementing BIGINT primary key, per dialect. */
    public static String autoIncrementPrimaryKey(DatabaseType type) {
        return switch (type) {
            case POSTGRESQL -> "BIGSERIAL PRIMARY KEY";
            case H2, MYSQL, MARIADB -> "BIGINT AUTO_INCREMENT PRIMARY KEY";
        };
    }
}
