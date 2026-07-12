package fr.mathilde.easybans.cache;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounded, LRU-evicted uuid&lt;-&gt;name cache used for offline tab-completion and quick
 * lookups. Two things keep this cheap to hold thousands of entries in memory: names are
 * stored as {@code byte[]} (Minecraft usernames are ASCII, so this halves the per-entry
 * overhead versus a Java {@code String}'s UTF-16 backing array plus object header), and the
 * map is capped at {@code maxSize} with true LRU eviction (access-order {@link LinkedHashMap})
 * instead of growing unbounded for the lifetime of the proxy.
 */
public final class OfflinePlayerCache {

    private final int maxSize;
    private final Map<UUID, byte[]> uuidToName;
    private final Map<String, UUID> nameToUuid = new ConcurrentHashMap<>();

    public OfflinePlayerCache(int maxSize) {
        this.maxSize = Math.max(64, maxSize);
        this.uuidToName = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<UUID, byte[]> eldest) {
                boolean remove = size() > OfflinePlayerCache.this.maxSize;
                if (remove) {
                    nameToUuid.remove(decode(eldest.getValue()).toLowerCase(Locale.ROOT));
                }
                return remove;
            }
        });
    }

    public void put(UUID uuid, String name) {
        synchronized (uuidToName) {
            uuidToName.put(uuid, encode(name));
        }
        nameToUuid.put(name.toLowerCase(Locale.ROOT), uuid);
    }

    public Optional<String> getName(UUID uuid) {
        byte[] value;
        synchronized (uuidToName) {
            value = uuidToName.get(uuid);
        }
        return Optional.ofNullable(value).map(this::decode);
    }

    public Optional<UUID> getUuid(String name) {
        return Optional.ofNullable(nameToUuid.get(name.toLowerCase(Locale.ROOT)));
    }

    /** Case-insensitive prefix search over cached offline names, for tab completion. */
    public List<String> suggestNames(String prefix, int limit) {
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        synchronized (uuidToName) {
            for (Map.Entry<UUID, byte[]> entry : uuidToName.entrySet()) {
                String name = decode(entry.getValue());
                if (name.toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
                    matches.add(name);
                    if (matches.size() >= limit) {
                        break;
                    }
                }
            }
        }
        return matches;
    }

    public int size() {
        return uuidToName.size();
    }

    private byte[] encode(String name) {
        return name.getBytes(StandardCharsets.US_ASCII);
    }

    private String decode(byte[] value) {
        return new String(value, StandardCharsets.US_ASCII);
    }
}
