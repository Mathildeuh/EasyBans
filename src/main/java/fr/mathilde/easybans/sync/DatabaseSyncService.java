package fr.mathilde.easybans.sync;

import fr.mathilde.easybans.database.dao.SyncEventDao;
import org.slf4j.Logger;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

/**
 * Default zero-dependency sync strategy: writes each change as a row in
 * {@code easybans_sync_events} and polls for new rows on a fixed interval. Trades a few
 * seconds of propagation latency for needing nothing beyond the database every deployment
 * already has.
 */
public final class DatabaseSyncService implements SyncService {

    private final SyncEventDao dao;
    private final Logger logger;
    private final int pollIntervalSeconds;
    private final String nodeId = UUID.randomUUID().toString();
    private final List<BiConsumer<SyncEventType, String>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong lastSeenId = new AtomicLong(0);
    private ScheduledExecutorService scheduler;

    public DatabaseSyncService(SyncEventDao dao, int pollIntervalSeconds, Logger logger) {
        this.dao = dao;
        this.pollIntervalSeconds = pollIntervalSeconds;
        this.logger = logger;
    }

    @Override
    public void publish(SyncEventType type, String payload) {
        dao.publish(type, payload, nodeId);
    }

    @Override
    public void addListener(BiConsumer<SyncEventType, String> listener) {
        listeners.add(listener);
    }

    @Override
    public void start() {
        dao.latestId().thenAccept(lastSeenId::set);
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "EasyBans-Sync-Poll");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::poll, pollIntervalSeconds, pollIntervalSeconds, TimeUnit.SECONDS);
    }

    private void poll() {
        dao.findSince(lastSeenId.get()).thenAccept(events -> {
            for (SyncEvent event : events) {
                lastSeenId.updateAndGet(current -> Math.max(current, event.id()));
                if (event.originNode().equals(nodeId)) {
                    continue; // we already applied this change locally when we made it
                }
                for (BiConsumer<SyncEventType, String> listener : listeners) {
                    try {
                        listener.accept(event.type(), event.payload());
                    } catch (Exception e) {
                        logger.warn("Sync listener threw an exception", e);
                    }
                }
            }
        }).exceptionally(e -> {
            logger.warn("Failed to poll sync events", e);
            return null;
        });
    }

    @Override
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }
}
