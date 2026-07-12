package fr.mathilde.easybans.config;

public record EasyBansConfig(
        StorageConfig storage,
        GeneralConfig general,
        CacheConfig cache,
        LocaleConfig locale,
        LinkedAccountsConfig linkedAccounts,
        SyncConfig sync,
        DiscordConfig discord
) {
    static EasyBansConfig fromYaml(YamlSection root) {
        return new EasyBansConfig(
                StorageConfig.fromYaml(root.section("storage")),
                GeneralConfig.fromYaml(root.section("general")),
                CacheConfig.fromYaml(root.section("cache")),
                LocaleConfig.fromYaml(root.section("locale")),
                LinkedAccountsConfig.fromYaml(root.section("linked-accounts")),
                SyncConfig.fromYaml(root.section("sync")),
                DiscordConfig.fromYaml(root.section("discord"))
        );
    }
}
