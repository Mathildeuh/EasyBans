package fr.mathilde.easybans.cache;

import fr.mathilde.easybans.punishment.Mute;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory active-mute status for online players, so the chat-mute gate never hits the
 * database on every message (requirement: no blocking/frequent queries on hot paths). Only
 * ever holds entries for currently-connected players; refreshed on login, server switch, mute
 * creation/removal, and remote sync events.
 */
public final class ActiveMuteCache {

    private final ConcurrentMap<UUID, Mute> activeMutes = new ConcurrentHashMap<>();

    public void put(UUID uuid, Mute mute) {
        activeMutes.put(uuid, mute);
    }

    public void clear(UUID uuid) {
        activeMutes.remove(uuid);
    }

    public Optional<Mute> get(UUID uuid) {
        Mute mute = activeMutes.get(uuid);
        if (mute == null) {
            return Optional.empty();
        }
        if (!mute.isCurrentlyActive()) {
            activeMutes.remove(uuid);
            return Optional.empty();
        }
        return Optional.of(mute);
    }
}
