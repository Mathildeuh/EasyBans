package fr.mathilde.easybans.config;

public record CacheConfig(int offlinePlayerCacheSize, int uuidCacheSize) {
    static CacheConfig fromYaml(YamlSection section) {
        return new CacheConfig(
                section.getInt("offline-player-cache-size", 5000),
                section.getInt("uuid-cache-size", 5000)
        );
    }
}
