package fr.mathilde.easybans.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.ArrayList;
import java.util.List;

/**
 * Small fluent builder over Adventure's {@link TagResolver}s. Every text value is registered
 * with {@code Placeholder.unparsed} so it is inserted literally rather than re-parsed as
 * MiniMessage - this is what stops a punishment reason like {@code "<rainbow>gg"} typed by
 * staff from injecting formatting into rendered messages.
 */
public final class PlaceholderContext {

    private final List<TagResolver> resolvers = new ArrayList<>();

    public static PlaceholderContext create() {
        return new PlaceholderContext();
    }

    public PlaceholderContext put(String key, String value) {
        resolvers.add(Placeholder.unparsed(key, value == null ? "" : value));
        return this;
    }

    public PlaceholderContext put(String key, long value) {
        return put(key, String.valueOf(value));
    }

    /** Use only for values that are meant to carry their own formatting (rare - most values should be unparsed). */
    public PlaceholderContext putComponent(String key, Component value) {
        resolvers.add(Placeholder.component(key, value));
        return this;
    }

    public TagResolver[] build() {
        return resolvers.toArray(new TagResolver[0]);
    }
}
