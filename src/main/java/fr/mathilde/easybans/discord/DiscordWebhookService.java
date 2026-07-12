package fr.mathilde.easybans.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.mathilde.easybans.config.DiscordConfig;
import fr.mathilde.easybans.punishment.Ban;
import fr.mathilde.easybans.punishment.DurationFormatter;
import fr.mathilde.easybans.punishment.Kick;
import fr.mathilde.easybans.punishment.Mute;
import fr.mathilde.easybans.punishment.RemovalInfo;
import fr.mathilde.easybans.punishment.Warning;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Posts moderation events to a Discord webhook as embeds. All calls are fire-and-forget from
 * the caller's perspective (return no value, log failures) since a Discord outage must never
 * affect punishment processing itself.
 */
public final class DiscordWebhookService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final int COLOR_BAN = 0xE74C3C;
    private static final int COLOR_UNBAN = 0x2ECC71;
    private static final int COLOR_MUTE = 0xE67E22;
    private static final int COLOR_UNMUTE = 0x2ECC71;
    private static final int COLOR_WARN = 0xF1C40F;
    private static final int COLOR_KICK = 0x9B59B6;

    private final DiscordConfig config;
    private final Executor executor;
    private final Logger logger;

    public DiscordWebhookService(DiscordConfig config, Executor executor, Logger logger) {
        this.config = config;
        this.executor = executor;
        this.logger = logger;
    }

    public void notifyBan(Ban ban, String targetName) {
        if (!config.isEventEnabled("ban")) {
            return;
        }
        DiscordEmbed embed = new DiscordEmbed(ban.ipBanned() ? "Player IP-banned" : "Player banned", COLOR_BAN)
                .field("Player", targetName, true)
                .field("Staff", ban.staffName(), true)
                .field("Scope", ban.serverScope().orElse("Global"), true)
                .field("Duration", formatRemaining(ban.expiresAt()), true)
                .field("Reason", ban.reason(), false);
        send(embed);
    }

    public void notifyUnban(Ban ban, String targetName, RemovalInfo removal) {
        if (!config.isEventEnabled("unban")) {
            return;
        }
        DiscordEmbed embed = new DiscordEmbed("Player unbanned", COLOR_UNBAN)
                .field("Player", targetName, true)
                .field("Staff", removal.removedByName(), true)
                .field("Reason", removal.removedReason(), false);
        send(embed);
    }

    public void notifyMute(Mute mute, String targetName) {
        if (!config.isEventEnabled("mute")) {
            return;
        }
        DiscordEmbed embed = new DiscordEmbed("Player muted", COLOR_MUTE)
                .field("Player", targetName, true)
                .field("Staff", mute.staffName(), true)
                .field("Scope", mute.serverScope().orElse("Global"), true)
                .field("Duration", formatRemaining(mute.expiresAt()), true)
                .field("Reason", mute.reason(), false);
        send(embed);
    }

    public void notifyUnmute(Mute mute, String targetName, RemovalInfo removal) {
        if (!config.isEventEnabled("unmute")) {
            return;
        }
        DiscordEmbed embed = new DiscordEmbed("Player unmuted", COLOR_UNMUTE)
                .field("Player", targetName, true)
                .field("Staff", removal.removedByName(), true)
                .field("Reason", removal.removedReason(), false);
        send(embed);
    }

    public void notifyWarn(Warning warning, String targetName) {
        if (!config.isEventEnabled("warn")) {
            return;
        }
        DiscordEmbed embed = new DiscordEmbed("Player warned", COLOR_WARN)
                .field("Player", targetName, true)
                .field("Staff", warning.staffName(), true)
                .field("Category", warning.category(), true)
                .field("Reason", warning.reason(), false);
        send(embed);
    }

    public void notifyKick(Kick kick, String targetName) {
        if (!config.isEventEnabled("kick")) {
            return;
        }
        DiscordEmbed embed = new DiscordEmbed("Player kicked", COLOR_KICK)
                .field("Player", targetName, true)
                .field("Staff", kick.staffName(), true)
                .field("Reason", kick.reason(), false);
        send(embed);
    }

    private String formatRemaining(Optional<Instant> expiresAt) {
        if (expiresAt.isEmpty()) {
            return "Permanent";
        }
        Duration remaining = Duration.between(Instant.now(), expiresAt.get());
        return remaining.isNegative() ? "Expired" : DurationFormatter.format(remaining);
    }

    private void send(DiscordEmbed embed) {
        if (!config.enabled() || config.webhookUrl() == null || config.webhookUrl().isBlank()) {
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("username", config.username());
        if (!config.avatarUrl().isBlank()) {
            payload.addProperty("avatar_url", config.avatarUrl());
        }
        JsonArray embeds = new JsonArray();
        embeds.add(embed.toJson());
        payload.add("embeds", embeds);

        executor.execute(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(config.webhookUrl()))
                        .timeout(Duration.ofSeconds(8))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                        .build();
                HttpResponse<Void> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() >= 300) {
                    logger.warn("Discord webhook returned status {}", response.statusCode());
                }
            } catch (Exception e) {
                logger.warn("Failed to send Discord webhook notification: {}", e.toString());
            }
        });
    }
}
