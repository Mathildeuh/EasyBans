package fr.mathilde.easybans.config;

import org.slf4j.Logger;

public record EasyBansConfig(
        StorageConfig storage,
        GeneralConfig general,
        CacheConfig cache,
        LocaleConfig locale,
        LinkedAccountsConfig linkedAccounts,
        SyncConfig sync,
        DiscordConfig discord
) {
    static EasyBansConfig fromYaml(YamlSection root, Logger logger) {
        return new EasyBansConfig(
                StorageConfig.fromYaml(root.section("storage"), logger),
                GeneralConfig.fromYaml(root.section("general")),
                CacheConfig.fromYaml(root.section("cache")),
                LocaleConfig.fromYaml(root.section("locale")),
                LinkedAccountsConfig.fromYaml(root.section("linked-accounts")),
                SyncConfig.fromYaml(root.section("sync"), logger),
                DiscordConfig.fromYaml(root.section("discord"))
        );
    }
}
