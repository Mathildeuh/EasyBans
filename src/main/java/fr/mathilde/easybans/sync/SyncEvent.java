package fr.mathilde.easybans.sync;

public record SyncEvent(long id, SyncEventType type, String payload, String originNode, long createdAtMillis) {
}
