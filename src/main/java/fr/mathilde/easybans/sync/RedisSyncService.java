package fr.mathilde.easybans.sync;

import fr.mathilde.easybans.config.SyncConfig;
import org.slf4j.Logger;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

/**
 * Redis pub/sub sync strategy: near-instant propagation, no polling, at the cost of an extra
 * piece of infrastructure. Opt in via {@code sync.mode: redis}.
 */
public final class RedisSyncService implements SyncService {

    private final JedisPool pool;
    private final String channel;
    private final String nodeId = UUID.randomUUID().toString();
    private final List<BiConsumer<SyncEventType, String>> listeners = new CopyOnWriteArrayList<>();
    private final Logger logger;
    private final ExecutorService subscriberExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "EasyBans-Redis-Subscriber");
        t.setDaemon(true);
        return t;
    });
    private JedisPubSub pubSub;

    public RedisSyncService(SyncConfig config, Logger logger) {
        this.logger = logger;
        this.channel = config.redisChannel();
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(8);
        if (config.redisPassword() == null || config.redisPassword().isBlank()) {
            this.pool = new JedisPool(poolConfig, config.redisHost(), config.redisPort());
        } else {
            this.pool = new JedisPool(poolConfig, config.redisHost(), config.redisPort(), 2000, config.redisPassword());
        }
    }

    @Override
    public void publish(SyncEventType type, String payload) {
        String message = type.name() + "|" + nodeId + "|" + payload;
        try (var jedis = pool.getResource()) {
            jedis.publish(channel, message);
        } catch (Exception e) {
            logger.warn("Failed to publish sync event to Redis", e);
        }
    }

    @Override
    public void addListener(BiConsumer<SyncEventType, String> listener) {
        listeners.add(listener);
    }

    @Override
    public void start() {
        pubSub = new JedisPubSub() {
            @Override
            public void onMessage(String subscribedChannel, String message) {
                handleMessage(message);
            }
        };
        subscriberExecutor.submit(() -> {
            try (var jedis = pool.getResource()) {
                jedis.subscribe(pubSub, channel);
            } catch (Exception e) {
                logger.warn("Redis subscription loop terminated unexpectedly", e);
            }
        });
    }

    private void handleMessage(String message) {
        String[] parts = message.split("\\|", 3);
        if (parts.length != 3) {
            return;
        }
        String originNode = parts[1];
        if (originNode.equals(nodeId)) {
            return; // already applied locally
        }
        SyncEventType type;
        try {
            type = SyncEventType.valueOf(parts[0]);
        } catch (IllegalArgumentException e) {
            return;
        }
        String payload = parts[2];
        for (BiConsumer<SyncEventType, String> listener : listeners) {
            try {
                listener.accept(type, payload);
            } catch (Exception e) {
                logger.warn("Sync listener threw an exception", e);
            }
        }
    }

    @Override
    public void stop() {
        if (pubSub != null && pubSub.isSubscribed()) {
            pubSub.unsubscribe();
        }
        subscriberExecutor.shutdownNow();
        pool.close();
    }
}
