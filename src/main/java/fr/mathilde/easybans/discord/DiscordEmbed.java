package fr.mathilde.easybans.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Minimal builder for a single Discord webhook embed - just the fields EasyBans needs. */
public final class DiscordEmbed {

    private final String title;
    private final int color;
    private final List<JsonObject> fields = new ArrayList<>();

    public DiscordEmbed(String title, int color) {
        this.title = title;
        this.color = color;
    }

    public DiscordEmbed field(String name, String value, boolean inline) {
        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("value", value == null || value.isBlank() ? "-" : value);
        field.addProperty("inline", inline);
        fields.add(field);
        return this;
    }

    public JsonObject toJson() {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", title);
        embed.addProperty("color", color);
        embed.addProperty("timestamp", Instant.now().toString());
        JsonArray fieldsArray = new JsonArray();
        fields.forEach(fieldsArray::add);
        embed.add("fields", fieldsArray);
        return embed;
    }
}
