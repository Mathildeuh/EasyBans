package fr.mathilde.easybans.cache;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.mathilde.easybans.database.dao.PlayerDao;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Resolves a player name to a UUID (and back) through, in order: currently-online players,
 * the in-memory offline cache, the local {@code players} table, and finally the Mojang API for
 * a player who has never joined this network before. Every layer that succeeds populates the
 * cheaper layers above it.
 */
public final class UuidResolver {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final ProxyServer proxy;
    private final OfflinePlayerCache cache;
    private final PlayerDao playerDao;
    private final Executor executor;
    private final Logger logger;

    public UuidResolver(ProxyServer proxy, OfflinePlayerCache cache, PlayerDao playerDao, Executor executor, Logger logger) {
        this.proxy = proxy;
        this.cache = cache;
        this.playerDao = playerDao;
        this.executor = executor;
        this.logger = logger;
    }

    public CompletableFuture<Optional<UUID>> resolveUuid(String name) {
        Optional<Player> online = proxy.getPlayer(name);
        if (online.isPresent()) {
            return CompletableFuture.completedFuture(Optional.of(online.get().getUniqueId()));
        }

        Optional<UUID> cached = cache.getUuid(name);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached);
        }

        return playerDao.findByName(name).thenCompose(dbRecord -> {
            if (dbRecord.isPresent()) {
                cache.put(dbRecord.get().uuid(), dbRecord.get().name());
                return CompletableFuture.completedFuture(Optional.of(dbRecord.get().uuid()));
            }
            return resolveViaMojang(name);
        });
    }

    public CompletableFuture<Optional<String>> resolveName(UUID uuid) {
        Optional<Player> online = proxy.getPlayer(uuid);
        if (online.isPresent()) {
            return CompletableFuture.completedFuture(Optional.of(online.get().getUsername()));
        }

        Optional<String> cached = cache.getName(uuid);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached);
        }

        return playerDao.findByUuid(uuid).thenApply(record -> {
            record.ifPresent(r -> cache.put(r.uuid(), r.name()));
            return record.map(fr.mathilde.easybans.player.PlayerRecord::name);
        });
    }

    private CompletableFuture<Optional<UUID>> resolveViaMojang(String name) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + name))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();
                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
                    return Optional.empty();
                }
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                String rawId = json.get("id").getAsString();
                String resolvedName = json.get("name").getAsString();
                UUID uuid = dashUuid(rawId);
                cache.put(uuid, resolvedName);
                return Optional.of(uuid);
            } catch (Exception e) {
                logger.warn("Mojang API lookup failed for '{}': {}", name, e.toString());
                return Optional.empty();
            }
        }, executor);
    }

    private UUID dashUuid(String raw) {
        String dashed = raw.replaceFirst(
                "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5");
        return UUID.fromString(dashed);
    }
}
