package fr.mathilde.easybans.config;

import java.util.HashMap;
import java.util.Map;

public record DiscordConfig(
        boolean enabled,
        String webhookUrl,
        String username,
        String avatarUrl,
        Map<String, Boolean> events
) {
    static DiscordConfig fromYaml(YamlSection section) {
        YamlSection eventsSection = section.section("events");
        Map<String, Boolean> events = new HashMap<>();
        for (String key : new String[]{"ban", "unban", "mute", "unmute", "warn", "kick"}) {
            events.put(key, eventsSection.getBoolean(key, true));
        }
        return new DiscordConfig(
                section.getBoolean("enabled", false),
                section.getString("webhook-url", ""),
                section.getString("username", "EasyBans"),
                section.getString("avatar-url", ""),
                events
        );
    }

    public boolean isEventEnabled(String action) {
        return enabled && events.getOrDefault(action, true);
    }
}
