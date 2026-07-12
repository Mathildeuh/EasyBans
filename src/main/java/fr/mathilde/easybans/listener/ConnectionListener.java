package fr.mathilde.easybans.listener;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import fr.mathilde.easybans.cache.ActiveMuteCache;
import fr.mathilde.easybans.cache.OfflinePlayerCache;
import fr.mathilde.easybans.database.dao.IpExemptionDao;
import fr.mathilde.easybans.database.dao.PlayerDao;
import fr.mathilde.easybans.database.dao.PunishmentDao;
import fr.mathilde.easybans.database.dao.IpHistoryDao;
import fr.mathilde.easybans.linked.LinkedAccountService;
import fr.mathilde.easybans.locale.LocaleService;
import fr.mathilde.easybans.locale.SupportedLocale;
import fr.mathilde.easybans.message.MessageService;
import fr.mathilde.easybans.message.PlaceholderContext;
import fr.mathilde.easybans.punishment.PunishmentService;
import fr.mathilde.easybans.punishment.Warning;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Handles the full connection lifecycle: ban/IP-ban gate at login, per-server local-ban gate
 * when switching backends, and post-login bookkeeping (player record, IP history, linked
 * account detection, offline warning delivery).
 */
public final class ConnectionListener {

    private final PunishmentService punishmentService;
    private final PunishmentDao punishmentDao;
    private final IpExemptionDao ipExemptionDao;
    private final IpHistoryDao ipHistoryDao;
    private final PlayerDao playerDao;
    private final LocaleService localeService;
    private final LinkedAccountService linkedAccountService;
    private final MessageService messages;
    private final OfflinePlayerCache offlinePlayerCache;
    private final ActiveMuteCache activeMuteCache;
    private final Logger logger;

    public ConnectionListener(PunishmentService punishmentService, PunishmentDao punishmentDao,
                               IpExemptionDao ipExemptionDao, IpHistoryDao ipHistoryDao, PlayerDao playerDao,
                               LocaleService localeService, LinkedAccountService linkedAccountService,
                               MessageService messages, OfflinePlayerCache offlinePlayerCache,
                               ActiveMuteCache activeMuteCache, Logger logger) {
        this.punishmentService = punishmentService;
        this.punishmentDao = punishmentDao;
        this.ipExemptionDao = ipExemptionDao;
        this.ipHistoryDao = ipHistoryDao;
        this.playerDao = playerDao;
        this.localeService = localeService;
        this.linkedAccountService = linkedAccountService;
        this.messages = messages;
        this.offlinePlayerCache = offlinePlayerCache;
        this.activeMuteCache = activeMuteCache;
        this.logger = logger;
    }

    @Subscribe
    public EventTask onLogin(LoginEvent event) {
        return EventTask.withContinuation(continuation -> {
            Player player = event.getPlayer();
            UUID uuid = player.getUniqueId();
            String ip = ipOf(player);

            localeService.resolveOnLogin(player).thenCompose(locale ->
                    punishmentService.checkLoginBan(uuid, null, locale).thenCompose(banScreen -> {
                        if (banScreen.isPresent()) {
                            event.setResult(ResultedEvent.ComponentResult.denied(banScreen.get()));
                            return CompletableFuture.<Void>completedFuture(null);
                        }
                        return ipExemptionDao.isExempt(uuid, ip).thenCompose(exempt -> {
                            if (exempt) {
                                return CompletableFuture.<Void>completedFuture(null);
                            }
                            return punishmentService.checkIpBan(ip, null, locale).thenAccept(ipBanScreen ->
                                    ipBanScreen.ifPresent(screen -> event.setResult(ResultedEvent.ComponentResult.denied(screen))));
                        });
                    })
            ).whenComplete((v, error) -> {
                if (error != null) {
                    logger.error("Login ban check failed for {}", player.getUsername(), error);
                }
                continuation.resume();
            });
        });
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String name = player.getUsername();
        String ip = ipOf(player);

        offlinePlayerCache.put(uuid, name);
        playerDao.upsertOnJoin(uuid, name, ip);
        ipHistoryDao.recordLogin(uuid, ip);
        linkedAccountService.onPlayerLogin(uuid, name, ip);

        punishmentDao.unacknowledgedWarnings(uuid).thenAccept(warnings -> {
            if (warnings.isEmpty()) {
                return;
            }
            SupportedLocale locale = localeService.getCached(uuid);
            for (Warning warning : warnings) {
                PlaceholderContext ctx = PlaceholderContext.create()
                        .put("reason", warning.reason())
                        .put("staff", warning.staffName())
                        .put("category", warning.category());
                Component message = messages.get(locale, "notify.warning-received", ctx.build());
                player.sendMessage(message);
            }
            punishmentDao.acknowledgeWarnings(uuid);
        });
    }

    @Subscribe
    public EventTask onServerPreConnect(ServerPreConnectEvent event) {
        return EventTask.withContinuation(continuation -> {
            Player player = event.getPlayer();
            String targetServer = event.getOriginalServer().getServerInfo().getName();
            punishmentService.checkLoginBan(player.getUniqueId(), targetServer, localeService.getCached(player.getUniqueId()))
                    .whenComplete((banScreen, error) -> {
                        if (error != null) {
                            logger.error("Per-server ban check failed for {} connecting to {}",
                                    player.getUsername(), targetServer, error);
                        } else if (banScreen.isPresent()) {
                            event.setResult(ServerPreConnectEvent.ServerResult.denied());
                            player.sendMessage(banScreen.get());
                        }
                        continuation.resume();
                    });
        });
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        punishmentService.refreshOnlineStatus(event.getPlayer().getUniqueId(), event.getServer().getServerInfo().getName());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        localeService.forget(uuid);
        // ActiveMuteCache is only meant to hold entries for currently-connected players (chat-mute
        // enforcement can't matter for someone who's offline); without this it grows unbounded for the
        // life of the process, since a mute's entry otherwise only self-heals lazily on next chat.
        activeMuteCache.clear(uuid);
    }

    private String ipOf(Player player) {
        InetSocketAddress address = player.getRemoteAddress();
        return address.getAddress().getHostAddress();
    }
}
