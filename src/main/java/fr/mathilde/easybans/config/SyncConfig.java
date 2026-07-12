package fr.mathilde.easybans.config;

import fr.mathilde.easybans.sync.SyncMode;
import org.slf4j.Logger;

public record SyncConfig(
        SyncMode mode,
        int pollIntervalSeconds,
        String redisHost,
        int redisPort,
        String redisPassword,
        String redisChannel
) {
    static SyncConfig fromYaml(YamlSection section, Logger logger) {
        String rawMode = section.getString("mode", "database");
        if (!SyncMode.isRecognized(rawMode)) {
            logger.warn("Unrecognized sync.mode '{}' in config.yml - falling back to database polling. "
                    + "Valid values: none, database, redis.", rawMode);
        }
        YamlSection redis = section.section("redis");
        return new SyncConfig(
                SyncMode.fromConfig(rawMode),
                Math.max(1, section.getInt("poll-interval-seconds", 3)),
                redis.getString("host", "localhost"),
                redis.getInt("port", 6379),
                redis.getString("password", ""),
                redis.getString("channel", "easybans:sync")
        );
    }
}
