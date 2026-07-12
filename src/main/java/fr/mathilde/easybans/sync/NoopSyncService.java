package fr.mathilde.easybans.sync;

import java.util.function.BiConsumer;

/** Used when {@code sync.mode: none} - single proxy instance, no cross-node propagation needed. */
public final class NoopSyncService implements SyncService {

    @Override
    public void publish(SyncEventType type, String payload) {
        // no-op: nothing to propagate to
    }

    @Override
    public void addListener(BiConsumer<SyncEventType, String> listener) {
        // no-op
    }

    @Override
    public void start() {
        // no-op
    }

    @Override
    public void stop() {
        // no-op
    }
}
