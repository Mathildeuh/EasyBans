package fr.mathilde.easybans.config;

import fr.mathilde.easybans.database.DatabaseType;
import org.slf4j.Logger;

public record StorageConfig(
        DatabaseType type,
        String host,
        int port,
        String database,
        String username,
        String password,
        boolean useSsl,
        String tablePrefix,
        int poolSize,
        long connectionTimeoutMs,
        String h2FileName
) {
    static StorageConfig fromYaml(YamlSection section, Logger logger) {
        String rawType = section.getString("type", "h2");
        if (!DatabaseType.isRecognized(rawType)) {
            logger.warn("Unrecognized storage.type '{}' in config.yml - falling back to H2. "
                    + "Valid values: h2, mysql, mariadb, postgresql.", rawType);
        }
        return new StorageConfig(
                DatabaseType.fromConfig(rawType),
                section.getString("host", "localhost"),
                section.getInt("port", 3306),
                section.getString("database", "easybans"),
                section.getString("username", "easybans"),
                section.getString("password", ""),
                section.getBoolean("use-ssl", false),
                section.getString("table-prefix", "easybans_"),
                section.getInt("pool-size", 10),
                section.getLong("connection-timeout-ms", 5000L),
                section.getString("h2-file-name", "easybans")
        );
    }
}
