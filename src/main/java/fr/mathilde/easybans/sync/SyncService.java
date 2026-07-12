package fr.mathilde.easybans.sync;

import java.util.function.BiConsumer;

/**
 * Propagates lightweight "something changed" notifications between proxy instances sharing
 * the same database, so caches/UI on every node stay consistent without each node re-polling
 * the database on its own schedule. Payloads are small (a UUID as text) - the receiving node
 * re-reads the actual state (punishment, locale, ...) from the database; sync events carry no
 * authoritative data themselves.
 */
public interface SyncService {

    void publish(SyncEventType type, String payload);

    /** Registers a listener invoked (off the network thread) whenever a remote node publishes an event. */
    void addListener(BiConsumer<SyncEventType, String> listener);

    void start();

    void stop();
}
