package fr.mathilde.easybans.locale;

import com.velocitypowered.api.proxy.Player;
import fr.mathilde.easybans.config.LocaleConfig;
import fr.mathilde.easybans.database.dao.PlayerDao;
import fr.mathilde.easybans.sync.SyncEventType;
import fr.mathilde.easybans.sync.SyncService;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the effective locale for a player: persisted per-player choice first, then
 * (optionally) the client's own locale, then the configured server default.
 */
public final class LocaleService {

    private final PlayerDao playerDao;
    private final LocaleConfig config;
    private final SyncService syncService;
    private final SupportedLocale defaultLocale;
    private final Map<UUID, SupportedLocale> resolved = new ConcurrentHashMap<>();

    public LocaleService(PlayerDao playerDao, LocaleConfig config, SyncService syncService) {
        this.playerDao = playerDao;
        this.config = config;
        this.syncService = syncService;
        this.defaultLocale = SupportedLocale.fromCode(config.defaultLocale()).orElse(SupportedLocale.EN);
    }

    public SupportedLocale defaultLocale() {
        return defaultLocale;
    }

    public SupportedLocale getCached(UUID uuid) {
        return resolved.getOrDefault(uuid, defaultLocale);
    }

    /** Called on login: resolves and caches the effective locale for this player. */
    public CompletableFuture<SupportedLocale> resolveOnLogin(Player player) {
        return playerDao.findByUuid(player.getUniqueId()).thenApply(record -> {
            Optional<SupportedLocale> persisted = record.flatMap(r -> r.locale().flatMap(SupportedLocale::fromCode));
            SupportedLocale locale = persisted.orElseGet(() -> {
                if (config.autoDetectLocale()) {
                    return SupportedLocale.fromJavaLocale(player.getPlayerSettings().getLocale()).orElse(defaultLocale);
                }
                return defaultLocale;
            });
            resolved.put(player.getUniqueId(), locale);
            return locale;
        });
    }

    public void forget(UUID uuid) {
        resolved.remove(uuid);
    }

    public CompletableFuture<Boolean> setLocale(UUID uuid, String code) {
        Optional<SupportedLocale> requested = SupportedLocale.fromCode(code);
        if (requested.isEmpty() || !config.supportedLocales().contains(requested.get().code())) {
            return CompletableFuture.completedFuture(false);
        }
        resolved.put(uuid, requested.get());
        return playerDao.setLocale(uuid, requested.get().code()).thenApply(v -> {
            syncService.publish(SyncEventType.LOCALE_CHANGED, uuid.toString());
            return true;
        });
    }

    /** Applies a locale change published by another proxy instance. */
    public void applyRemoteChange(UUID uuid) {
        playerDao.findByUuid(uuid).thenAccept(record -> record.ifPresent(r ->
                r.locale().flatMap(SupportedLocale::fromCode).ifPresent(locale -> resolved.put(uuid, locale))));
    }
}
