package fr.mathilde.easybans.linked;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.mathilde.easybans.command.Permissions;
import fr.mathilde.easybans.config.LinkedAccountsConfig;
import fr.mathilde.easybans.database.dao.IpHistoryDao;
import fr.mathilde.easybans.database.dao.PlayerDao;
import fr.mathilde.easybans.database.dao.PunishmentDao;
import fr.mathilde.easybans.locale.LocaleService;
import fr.mathilde.easybans.message.MessageService;
import fr.mathilde.easybans.message.PlaceholderContext;
import fr.mathilde.easybans.punishment.PunishmentConstants;
import fr.mathilde.easybans.punishment.PunishmentService;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Detects, on login, whether the connecting player shares an IP with an already-banned
 * account. Depending on config, notifies staff with {@link Permissions#NOTIFY_LINKED} and/or
 * automatically bans the new account too (off by default - a shared IP is not proof of ban
 * evasion, e.g. households/NAT/school networks, so auto-ban is an opt-in trade-off).
 */
public final class LinkedAccountService {

    private final ProxyServer proxy;
    private final IpHistoryDao ipHistoryDao;
    private final PunishmentDao punishmentDao;
    private final PlayerDao playerDao;
    private final PunishmentService punishmentService;
    private final LinkedAccountsConfig config;
    private final MessageService messages;
    private final LocaleService localeService;
    private final Logger logger;

    public LinkedAccountService(ProxyServer proxy, IpHistoryDao ipHistoryDao, PunishmentDao punishmentDao,
                                 PlayerDao playerDao, PunishmentService punishmentService,
                                 LinkedAccountsConfig config, MessageService messages, LocaleService localeService,
                                 Logger logger) {
        this.proxy = proxy;
        this.ipHistoryDao = ipHistoryDao;
        this.punishmentDao = punishmentDao;
        this.playerDao = playerDao;
        this.punishmentService = punishmentService;
        this.config = config;
        this.messages = messages;
        this.localeService = localeService;
        this.logger = logger;
    }

    public void onPlayerLogin(UUID uuid, String name, String ip) {
        if (!config.notifyStaff() && !config.autoBan()) {
            return;
        }
        AtomicBoolean autoBanned = new AtomicBoolean(false);
        ipHistoryDao.findAccountsSharingIp(uuid, ip).thenAccept(others -> {
            for (UUID other : others) {
                punishmentDao.findAnyActiveBan(other).thenAccept(activeBan -> {
                    if (activeBan.isEmpty()) {
                        return;
                    }
                    playerDao.findByUuid(other).thenAccept(otherRecord -> {
                        String otherName = otherRecord.map(r -> r.name()).orElse(other.toString());
                        if (config.notifyStaff()) {
                            notifyStaff(name, otherName, ip);
                        }
                        // Only ever attempt one auto-ban per login, even if the player shares an IP with several
                        // banned accounts - and never bypass anti-overwrite here, so a race with another auto-ban
                        // or a manual /ban for the same target can't produce duplicate active bans.
                        if (config.autoBan() && autoBanned.compareAndSet(false, true)) {
                            punishmentService.ban(uuid, name, Optional.of(ip), false,
                                    "Linked account of banned player " + otherName, PunishmentConstants.CONSOLE_UUID,
                                    PunishmentConstants.CONSOLE_NAME, Optional.empty(), Optional.empty(), true, false);
                        }
                    }).exceptionally(e -> {
                        logger.warn("Linked-account player lookup failed for {} (shared IP with {})", name, other, e);
                        return null;
                    });
                }).exceptionally(e -> {
                    logger.warn("Linked-account ban check failed for {} (shared IP with {})", name, other, e);
                    return null;
                });
            }
        }).exceptionally(e -> {
            logger.warn("Linked-account check failed for {}", name, e);
            return null;
        });
    }

    private void notifyStaff(String name, String bannedAccountName, String ip) {
        List<Player> staff = proxy.getAllPlayers().stream()
                .filter(p -> p.hasPermission(Permissions.NOTIFY_LINKED))
                .toList();
        for (Player member : staff) {
            var locale = localeService.getCached(member.getUniqueId());
            PlaceholderContext ctx = PlaceholderContext.create()
                    .put("player", name)
                    .put("linked_player", bannedAccountName)
                    .put("ip", ip);
            Component message = messages.get(locale, "notify.linked-account", ctx.build());
            member.sendMessage(message);
        }
    }
}
