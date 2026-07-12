package fr.mathilde.easybans.config;

import fr.mathilde.easybans.sync.SyncMode;

public record SyncConfig(
        SyncMode mode,
        int pollIntervalSeconds,
        String redisHost,
        int redisPort,
        String redisPassword,
        String redisChannel
) {
    static SyncConfig fromYaml(YamlSection section) {
        YamlSection redis = section.section("redis");
        return new SyncConfig(
                SyncMode.fromConfig(section.getString("mode", "database")),
                Math.max(1, section.getInt("poll-interval-seconds", 3)),
                redis.getString("host", "localhost"),
                redis.getInt("port", 6379),
                redis.getString("password", ""),
                redis.getString("channel", "easybans:sync")
        );
    }
}
