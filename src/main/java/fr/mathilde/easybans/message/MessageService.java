package fr.mathilde.easybans.message;

import fr.mathilde.easybans.config.LocaleConfig;
import fr.mathilde.easybans.locale.SupportedLocale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Loads {@code messages_<locale>.yml} for every supported locale and resolves keys through a
 * fallback chain: requested locale -&gt; configured default locale -&gt; English -&gt; the raw key
 * itself (logged as a warning, since that always indicates a genuinely missing translation).
 *
 * <p>Values are MiniMessage strings. Dynamic values ({@code {REASON}}, {@code {STAFF}}, ...)
 * are injected as {@link TagResolver}s built with {@code Placeholder.unparsed(...)} by callers
 * - never via raw string concatenation before parsing - so a punishment reason typed by staff
 * (which might contain {@code <} characters) can never inject MiniMessage formatting into a
 * rendered message.
 */
public final class MessageService {

    private volatile Map<SupportedLocale, Map<String, Object>> messagesByLocale = new EnumMap<>(SupportedLocale.class);
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final SupportedLocale defaultLocale;
    private final Path dataDirectory;
    private final LocaleConfig config;
    private final Logger logger;

    public MessageService(Path dataDirectory, LocaleConfig config, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.config = config;
        this.logger = logger;
        this.defaultLocale = SupportedLocale.fromCode(config.defaultLocale()).orElse(SupportedLocale.EN);
        reload();
    }

    /** Re-reads every {@code messages_<locale>.yml} from disk in place - used by {@code /easybans reload}. */
    public synchronized void reload() {
        Path messagesDir = dataDirectory.resolve("messages");
        try {
            Files.createDirectories(messagesDir);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create messages directory", e);
        }

        // Always load English (ultimate fallback) plus every locale the operator configured.
        java.util.Set<SupportedLocale> toLoad = new java.util.LinkedHashSet<>();
        toLoad.add(SupportedLocale.EN);
        for (String code : config.supportedLocales()) {
            SupportedLocale.fromCode(code).ifPresentOrElse(toLoad::add,
                    () -> logger.warn("Unsupported locale code in config.yml: '{}'", code));
        }

        Map<SupportedLocale, Map<String, Object>> freshlyLoaded = new EnumMap<>(SupportedLocale.class);
        for (SupportedLocale locale : toLoad) {
            freshlyLoaded.put(locale, loadOrGenerate(messagesDir, locale));
        }
        // Swap the whole reference atomically so concurrent readers never observe a partially-rebuilt map.
        this.messagesByLocale = freshlyLoaded;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadOrGenerate(Path messagesDir, SupportedLocale locale) {
        String fileName = "messages_" + locale.code() + ".yml";
        Path target = messagesDir.resolve(fileName);
        try {
            if (!Files.exists(target)) {
                try (InputStream in = MessageService.class.getResourceAsStream("/messages/" + fileName)) {
                    if (in == null) {
                        logger.warn("No bundled messages for locale '{}', it will fall back to English", locale.code());
                        return Map.of();
                    }
                    Files.copy(in, target);
                }
            }
            try (InputStream in = Files.newInputStream(target)) {
                Object loaded = new Yaml().load(in);
                return loaded instanceof Map ? (Map<String, Object>) loaded : Map.of();
            }
        } catch (IOException e) {
            logger.error("Failed to load {}", fileName, e);
            return Map.of();
        }
    }

    public Component get(SupportedLocale locale, String key, TagResolver... resolvers) {
        Object raw = resolve(locale, key);
        if (raw == null) {
            return Component.text(key);
        }
        return miniMessage.deserialize(String.valueOf(raw), resolvers);
    }

    /**
     * Same as {@link #get} but returns plain text (colors/formatting stripped) - for values
     * that get embedded as a *placeholder* into another message (e.g. the default reason text
     * plugged into {@code <reason>}) rather than sent directly. {@code Component#toString()} is
     * never a substitute for this: it returns Adventure's debug representation of the component
     * tree, not its rendered text.
     */
    public String getPlain(SupportedLocale locale, String key, TagResolver... resolvers) {
        return PlainTextComponentSerializer.plainText().serialize(get(locale, key, resolvers));
    }

    /** For multi-line content (kick screens): a YAML list under the key becomes one Component per line. */
    @SuppressWarnings("unchecked")
    public List<Component> getMultiline(SupportedLocale locale, String key, TagResolver... resolvers) {
        Object raw = resolve(locale, key);
        if (raw instanceof List<?> lines) {
            return lines.stream()
                    .map(line -> miniMessage.deserialize(String.valueOf(line), resolvers))
                    .toList();
        }
        if (raw == null) {
            return List.of(Component.text(key));
        }
        return List.of(miniMessage.deserialize(String.valueOf(raw), resolvers));
    }

    public boolean hasKey(SupportedLocale locale, String key) {
        return navigate(messagesByLocale.getOrDefault(locale, Map.of()), key) != null;
    }

    /** Every top-level dotted key present in the given locale's file, flattened - used by the key-parity test. */
    public java.util.Set<String> allKeys(SupportedLocale locale) {
        java.util.Set<String> keys = new java.util.TreeSet<>();
        collectKeys(messagesByLocale.getOrDefault(locale, Map.of()), "", keys);
        return keys;
    }

    @SuppressWarnings("unchecked")
    private void collectKeys(Map<String, Object> map, String prefix, java.util.Set<String> out) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            if (entry.getValue() instanceof Map<?, ?> nested) {
                collectKeys((Map<String, Object>) nested, path, out);
            } else {
                out.add(path);
            }
        }
    }

    private Object resolve(SupportedLocale locale, String key) {
        Object value = navigate(messagesByLocale.getOrDefault(locale, Map.of()), key);
        if (value != null) {
            return value;
        }
        if (locale != defaultLocale) {
            value = navigate(messagesByLocale.getOrDefault(defaultLocale, Map.of()), key);
            if (value != null) {
                return value;
            }
        }
        if (locale != SupportedLocale.EN && defaultLocale != SupportedLocale.EN) {
            value = navigate(messagesByLocale.getOrDefault(SupportedLocale.EN, Map.of()), key);
            if (value != null) {
                return value;
            }
        }
        logger.warn("Missing message key '{}' (not found for '{}', default '{}', or English)",
                key, locale.code(), defaultLocale.code());
        return null;
    }

    @SuppressWarnings("unchecked")
    private Object navigate(Map<String, Object> root, String dottedKey) {
        String[] parts = dottedKey.split("\\.");
        Object current = root;
        for (String part : parts) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }
}
