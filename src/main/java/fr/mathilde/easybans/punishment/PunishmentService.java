package fr.mathilde.easybans.punishment;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.mathilde.easybans.cache.ActiveMuteCache;
import fr.mathilde.easybans.config.GeneralConfig;
import fr.mathilde.easybans.database.dao.PunishmentDao;
import fr.mathilde.easybans.discord.DiscordWebhookService;
import fr.mathilde.easybans.kickscreen.KickScreenRenderer;
import fr.mathilde.easybans.locale.LocaleService;
import fr.mathilde.easybans.locale.SupportedLocale;
import fr.mathilde.easybans.sync.SyncEventType;
import fr.mathilde.easybans.sync.SyncService;
import fr.mathilde.easybans.template.PunishmentTemplate;
import fr.mathilde.easybans.template.TemplateEngine;
import fr.mathilde.easybans.template.TemplateRegistry;
import fr.mathilde.easybans.template.TemplateStage;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Central place where every punishment mutation happens: enforces anti-overwrite, persists the
 * row, disconnects/mutes the target if they're online, publishes a cross-instance sync event,
 * and notifies Discord. Commands should never talk to {@link PunishmentDao} directly - they go
 * through this service so every entry point gets the same guarantees.
 */
public final class PunishmentService {

    private final ProxyServer proxy;
    private final PunishmentDao dao;
    private final TemplateRegistry templateRegistry;
    private final SyncService syncService;
    private final DiscordWebhookService discord;
    private final KickScreenRenderer kickScreenRenderer;
    private final LocaleService localeService;
    private final GeneralConfig generalConfig;
    private final ActiveMuteCache activeMuteCache;
    private final Logger logger;

    public PunishmentService(ProxyServer proxy, PunishmentDao dao, TemplateRegistry templateRegistry,
                              SyncService syncService, DiscordWebhookService discord,
                              KickScreenRenderer kickScreenRenderer, LocaleService localeService,
                              GeneralConfig generalConfig, ActiveMuteCache activeMuteCache, Logger logger) {
        this.proxy = proxy;
        this.dao = dao;
        this.templateRegistry = templateRegistry;
        this.syncService = syncService;
        this.discord = discord;
        this.kickScreenRenderer = kickScreenRenderer;
        this.localeService = localeService;
        this.generalConfig = generalConfig;
        this.activeMuteCache = activeMuteCache;
        this.logger = logger;
    }

    // ---------------------------------------------------------------- ban

    public CompletableFuture<PunishmentActionResult<Ban>> ban(UUID target, String targetName, Optional<String> targetIp,
                                                                boolean ipBan, String reason, UUID staffUuid,
                                                                String staffName, Optional<String> scope,
                                                                Optional<Duration> duration, boolean silent,
                                                                boolean bypassOverride) {
        return dao.insertBanIfNoneActive(target, targetIp, ipBan, reason, staffUuid, staffName, scope, null,
                        Optional.empty(), silent, Instant.now(), duration.map(Instant.now()::plus))
                .thenCompose(insertedOpt -> {
                    if (insertedOpt.isPresent()) {
                        afterBanCreated(insertedOpt.get(), targetName);
                        return CompletableFuture.completedFuture(PunishmentActionResult.success(insertedOpt.get()));
                    }
                    if (!bypassOverride) {
                        return CompletableFuture.completedFuture(PunishmentActionResult.<Ban>failure(PunishmentOutcome.ALREADY_PUNISHED));
                    }
                    return dao.deactivateAllActive(target, "BAN", staffUuid, staffName,
                                    "Replaced by a new ban issued by " + staffName)
                            .thenCompose(count -> dao.insertBan(target, targetIp, ipBan, reason, staffUuid, staffName,
                                    scope, null, Optional.empty(), silent, Instant.now(),
                                    duration.map(Instant.now()::plus)))
                            .thenApply(ban -> {
                                afterBanCreated(ban, targetName);
                                return PunishmentActionResult.success(ban);
                            });
                });
    }

    public CompletableFuture<PunishmentActionResult<Ban>> banFromTemplate(UUID target, String targetName,
                                                                            Optional<String> targetIp, boolean ipBan,
                                                                            String templateId, UUID staffUuid,
                                                                            String staffName, Optional<String> scope,
                                                                            boolean silent, boolean bypassOverride) {
        Optional<PunishmentTemplate> template = templateRegistry.template(templateId);
        if (template.isEmpty()) {
            return CompletableFuture.completedFuture(PunishmentActionResult.failure(PunishmentOutcome.TEMPLATE_NOT_FOUND));
        }
        if (template.get().type() != PunishmentType.BAN) {
            return CompletableFuture.completedFuture(PunishmentActionResult.failure(PunishmentOutcome.TEMPLATE_WRONG_TYPE));
        }
        return dao.countByTemplate(target, templateId).thenCompose(previousCount -> {
            TemplateStage stage = TemplateEngine.resolveStage(template.get(), previousCount);
            Optional<Instant> expiresAt = stage.duration().map(Instant.now()::plus);
            return dao.insertBanIfNoneActive(target, targetIp, ipBan, stage.reason(), staffUuid, staffName, scope,
                            templateId, stage.kickscreenKey(), silent, Instant.now(), expiresAt)
                    .thenCompose(insertedOpt -> {
                        if (insertedOpt.isPresent()) {
                            afterBanCreated(insertedOpt.get(), targetName);
                            return CompletableFuture.completedFuture(PunishmentActionResult.success(insertedOpt.get()));
                        }
                        if (!bypassOverride) {
                            return CompletableFuture.completedFuture(PunishmentActionResult.<Ban>failure(PunishmentOutcome.ALREADY_PUNISHED));
                        }
                        return dao.deactivateAllActive(target, "BAN", staffUuid, staffName,
                                        "Replaced by a new ban issued by " + staffName)
                                .thenCompose(count -> dao.insertBan(target, targetIp, ipBan, stage.reason(), staffUuid,
                                        staffName, scope, templateId, stage.kickscreenKey(), silent, Instant.now(),
                                        expiresAt))
                                .thenApply(ban -> {
                                    afterBanCreated(ban, targetName);
                                    return PunishmentActionResult.success(ban);
                                });
                    });
        });
    }

    private void afterBanCreated(Ban ban, String targetName) {
        if (generalConfig.kickOnlinePlayerOnBan()) {
            proxy.getPlayer(ban.targetUuid()).ifPresent(player -> disconnectWithBanScreen(player, ban));
        }
        syncService.publish(SyncEventType.PUNISHMENT_CHANGED, ban.targetUuid().toString());
        discord.notifyBan(ban, targetName);
    }

    private void disconnectWithBanScreen(Player player, Ban ban) {
        SupportedLocale locale = localeService.getCached(player.getUniqueId());
        Component screen = ban.ipBanned()
                ? kickScreenRenderer.renderIpBan(ban, locale, player.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse(null))
                : kickScreenRenderer.renderBan(ban, locale, player.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse(null));
        player.disconnect(screen);
    }

    public CompletableFuture<Optional<Component>> checkLoginBan(UUID target, String serverName, SupportedLocale locale) {
        return dao.findActiveBan(target, serverName).thenApply(banOpt ->
                banOpt.map(ban -> ban.ipBanned()
                        ? kickScreenRenderer.renderIpBan(ban, locale, serverName)
                        : kickScreenRenderer.renderBan(ban, locale, serverName)));
    }

    public CompletableFuture<Optional<Component>> checkIpBan(String ip, String serverName, SupportedLocale locale) {
        return dao.findActiveIpBan(ip, serverName).thenApply(banOpt ->
                banOpt.map(ban -> kickScreenRenderer.renderIpBan(ban, locale, serverName)));
    }

    public CompletableFuture<PunishmentOutcome> unban(UUID target, String targetName, UUID staffUuid, String staffName,
                                                        String reason) {
        return dao.findAnyActiveBan(target).thenCompose(existing -> {
            if (existing.isEmpty()) {
                return CompletableFuture.completedFuture(PunishmentOutcome.NOT_FOUND);
            }
            return dao.deactivate(existing.get().id(), staffUuid, staffName, reason).thenApply(ok -> {
                syncService.publish(SyncEventType.PUNISHMENT_CHANGED, target.toString());
                RemovalInfo removal = new RemovalInfo(staffUuid, staffName, Instant.now(), reason);
                discord.notifyUnban(existing.get(), targetName, removal);
                return PunishmentOutcome.SUCCESS;
            });
        });
    }

    /** Looks up any punishment (of any type) by its database id - backs {@code /easybans lookup <id>}. */
    public CompletableFuture<Optional<Punishment>> findById(long id) {
        return dao.findById(id);
    }

    /** Unbans by raw IP address rather than player - backs vanilla's {@code /pardon-ip}. */
    public CompletableFuture<PunishmentOutcome> unbanIp(String ip, UUID staffUuid, String staffName, String reason) {
        return dao.findAnyActiveIpBan(ip).thenCompose(existing -> {
            if (existing.isEmpty()) {
                return CompletableFuture.completedFuture(PunishmentOutcome.NOT_FOUND);
            }
            return dao.deactivate(existing.get().id(), staffUuid, staffName, reason).thenApply(ok -> {
                RemovalInfo removal = new RemovalInfo(staffUuid, staffName, Instant.now(), reason);
                discord.notifyUnban(existing.get(), ip, removal);
                return PunishmentOutcome.SUCCESS;
            });
        });
    }

    public CompletableFuture<List<Ban>> activeBans(boolean ipOnly, int page, int pageSize) {
        return dao.activeBans(ipOnly, page, pageSize);
    }

    public CompletableFuture<Integer> countActiveBans(boolean ipOnly) {
        return dao.countActiveBans(ipOnly);
    }

    // ---------------------------------------------------------------- mute

    public CompletableFuture<PunishmentActionResult<Mute>> mute(UUID target, String targetName, String reason,
                                                                  UUID staffUuid, String staffName,
                                                                  Optional<String> scope, Optional<Duration> duration,
                                                                  boolean silent, boolean bypassOverride) {
        return dao.insertMuteIfNoneActive(target, reason, staffUuid, staffName, scope, null, silent, Instant.now(),
                        duration.map(Instant.now()::plus))
                .thenCompose(insertedOpt -> {
                    if (insertedOpt.isPresent()) {
                        afterMuteCreated(insertedOpt.get(), targetName);
                        return CompletableFuture.completedFuture(PunishmentActionResult.success(insertedOpt.get()));
                    }
                    if (!bypassOverride) {
                        return CompletableFuture.completedFuture(PunishmentActionResult.<Mute>failure(PunishmentOutcome.ALREADY_PUNISHED));
                    }
                    return dao.deactivateAllActive(target, "MUTE", staffUuid, staffName,
                                    "Replaced by a new mute issued by " + staffName)
                            .thenCompose(count -> dao.insertMute(target, reason, staffUuid, staffName, scope, null,
                                    silent, Instant.now(), duration.map(Instant.now()::plus)))
                            .thenApply(mute -> {
                                afterMuteCreated(mute, targetName);
                                return PunishmentActionResult.success(mute);
                            });
                });
    }

    public CompletableFuture<PunishmentActionResult<Mute>> muteFromTemplate(UUID target, String targetName,
                                                                             String templateId, UUID staffUuid,
                                                                             String staffName, Optional<String> scope,
                                                                             boolean silent, boolean bypassOverride) {
        Optional<PunishmentTemplate> template = templateRegistry.template(templateId);
        if (template.isEmpty()) {
            return CompletableFuture.completedFuture(PunishmentActionResult.failure(PunishmentOutcome.TEMPLATE_NOT_FOUND));
        }
        if (template.get().type() != PunishmentType.MUTE) {
            return CompletableFuture.completedFuture(PunishmentActionResult.failure(PunishmentOutcome.TEMPLATE_WRONG_TYPE));
        }
        return dao.countByTemplate(target, templateId).thenCompose(previousCount -> {
            TemplateStage stage = TemplateEngine.resolveStage(template.get(), previousCount);
            Optional<Instant> expiresAt = stage.duration().map(Instant.now()::plus);
            return dao.insertMuteIfNoneActive(target, stage.reason(), staffUuid, staffName, scope, templateId,
                            silent, Instant.now(), expiresAt)
                    .thenCompose(insertedOpt -> {
                        if (insertedOpt.isPresent()) {
                            afterMuteCreated(insertedOpt.get(), targetName);
                            return CompletableFuture.completedFuture(PunishmentActionResult.success(insertedOpt.get()));
                        }
                        if (!bypassOverride) {
                            return CompletableFuture.completedFuture(PunishmentActionResult.<Mute>failure(PunishmentOutcome.ALREADY_PUNISHED));
                        }
                        return dao.deactivateAllActive(target, "MUTE", staffUuid, staffName,
                                        "Replaced by a new mute issued by " + staffName)
                                .thenCompose(count -> dao.insertMute(target, stage.reason(), staffUuid, staffName,
                                        scope, templateId, silent, Instant.now(), expiresAt))
                                .thenApply(mute -> {
                                    afterMuteCreated(mute, targetName);
                                    return PunishmentActionResult.success(mute);
                                });
                    });
        });
    }

    private void afterMuteCreated(Mute mute, String targetName) {
        activeMuteCache.put(mute.targetUuid(), mute);
        syncService.publish(SyncEventType.PUNISHMENT_CHANGED, mute.targetUuid().toString());
        discord.notifyMute(mute, targetName);
    }

    public CompletableFuture<PunishmentOutcome> unmute(UUID target, String targetName, UUID staffUuid, String staffName,
                                                         String reason) {
        return dao.findAnyActiveMute(target).thenCompose(existing -> {
            if (existing.isEmpty()) {
                return CompletableFuture.completedFuture(PunishmentOutcome.NOT_FOUND);
            }
            return dao.deactivate(existing.get().id(), staffUuid, staffName, reason).thenApply(ok -> {
                activeMuteCache.clear(target);
                syncService.publish(SyncEventType.PUNISHMENT_CHANGED, target.toString());
                RemovalInfo removal = new RemovalInfo(staffUuid, staffName, Instant.now(), reason);
                discord.notifyUnmute(existing.get(), targetName, removal);
                return PunishmentOutcome.SUCCESS;
            });
        });
    }

    public CompletableFuture<Optional<Mute>> checkActiveMute(UUID target, String serverName) {
        return dao.findActiveMute(target, serverName);
    }

    /** Re-reads active ban/mute state from the database and refreshes in-memory caches for an online player. */
    public void refreshOnlineStatus(UUID target, String serverName) {
        dao.findActiveMute(target, serverName).thenAccept(muteOpt ->
                muteOpt.ifPresentOrElse(mute -> activeMuteCache.put(target, mute), () -> activeMuteCache.clear(target)));
        dao.findActiveBan(target, serverName).thenAccept(banOpt -> banOpt.ifPresent(ban ->
                proxy.getPlayer(target).ifPresent(player -> disconnectWithBanScreen(player, ban))));
    }

    // ---------------------------------------------------------------- warn / kick / note

    public CompletableFuture<Warning> warn(UUID target, String targetName, String category, String reason,
                                            UUID staffUuid, String staffName, Optional<String> scope, boolean silent) {
        boolean online = proxy.getPlayer(target).isPresent();
        return dao.insertWarning(target, category, reason, staffUuid, staffName, scope, silent, Instant.now(),
                Optional.empty(), online).thenApply(warning -> {
            syncService.publish(SyncEventType.PUNISHMENT_CHANGED, target.toString());
            discord.notifyWarn(warning, targetName);
            return warning;
        });
    }

    public CompletableFuture<Kick> kick(UUID target, String targetName, String reason, UUID staffUuid,
                                         String staffName, Optional<String> scope, boolean silent) {
        return dao.insertKick(target, reason, staffUuid, staffName, scope, Optional.empty(), silent, Instant.now())
                .thenApply(kick -> {
                    proxy.getPlayer(target).ifPresent(player -> {
                        SupportedLocale locale = localeService.getCached(player.getUniqueId());
                        String server = player.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse(null);
                        player.disconnect(kickScreenRenderer.renderKick(kick, locale, server));
                    });
                    discord.notifyKick(kick, targetName);
                    return kick;
                });
    }

    public CompletableFuture<Note> note(UUID target, String reason, UUID staffUuid, String staffName) {
        return dao.insertNote(target, reason, staffUuid, staffName, Instant.now());
    }

    public PunishmentDao dao() {
        return dao;
    }
}
