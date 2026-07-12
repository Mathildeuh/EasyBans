package fr.mathilde.easybans.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin, null-safe accessor over a nested {@code Map<String, Object>} produced by SnakeYAML.
 * Records don't map cleanly onto SnakeYAML's default POJO binding, so config is parsed into
 * plain maps and read through this wrapper instead - it keeps default-value handling and
 * validation in one place.
 */
public final class YamlSection {

    private final Map<String, Object> raw;

    @SuppressWarnings("unchecked")
    public YamlSection(Map<String, Object> raw) {
        this.raw = raw != null ? raw : new LinkedHashMap<>();
    }

    public static YamlSection empty() {
        return new YamlSection(new LinkedHashMap<>());
    }

    @SuppressWarnings("unchecked")
    public YamlSection section(String path) {
        Object value = raw.get(path);
        if (value instanceof Map<?, ?> map) {
            return new YamlSection((Map<String, Object>) map);
        }
        return YamlSection.empty();
    }

    public String getString(String key, String def) {
        Object value = raw.get(key);
        return value != null ? String.valueOf(value) : def;
    }

    public int getInt(String key, int def) {
        Object value = raw.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return def;
    }

    public long getLong(String key, long def) {
        Object value = raw.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return def;
    }

    public boolean getBoolean(String key, boolean def) {
        Object value = raw.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return def;
    }

    @SuppressWarnings("unchecked")
    public List<String> getStringList(String key) {
        Object value = raw.get(key);
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object o : list) {
                result.add(String.valueOf(o));
            }
            return result;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> raw() {
        return raw;
    }

    public boolean has(String key) {
        return raw.containsKey(key);
    }
}
