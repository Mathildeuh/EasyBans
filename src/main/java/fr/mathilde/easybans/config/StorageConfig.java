package fr.mathilde.easybans.config;

import fr.mathilde.easybans.database.DatabaseType;

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
    static StorageConfig fromYaml(YamlSection section) {
        return new StorageConfig(
                DatabaseType.fromConfig(section.getString("type", "h2")),
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
