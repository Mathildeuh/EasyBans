package fr.mathilde.easybans.config;

public record GeneralConfig(
        boolean debug,
        String defaultScope,
        boolean broadcastPunishments,
        String broadcastPermission,
        boolean kickOnlinePlayerOnBan
) {
    static GeneralConfig fromYaml(YamlSection section) {
        return new GeneralConfig(
                section.getBoolean("debug", false),
                section.getString("default-scope", "global"),
                section.getBoolean("broadcast-punishments", true),
                section.getString("broadcast-permission", "easybans.notify.broadcast"),
                section.getBoolean("kick-online-player-on-ban", true)
        );
    }
}
