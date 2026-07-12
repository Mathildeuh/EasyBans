package fr.mathilde.easybans.kickscreen;

import fr.mathilde.easybans.locale.SupportedLocale;
import fr.mathilde.easybans.message.MessageService;
import fr.mathilde.easybans.message.PlaceholderContext;
import fr.mathilde.easybans.message.PunishmentFormatter;
import fr.mathilde.easybans.punishment.Ban;
import fr.mathilde.easybans.punishment.Kick;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.List;

/**
 * Renders the multi-line disconnect screen shown for bans and kicks. Screens are resolved in
 * the target player's own language (falling back through {@link MessageService}'s normal
 * chain) so a banned player always reads their ban reason in a language they understand.
 *
 * <p>RTL note (Arabic): the Minecraft client renders each line's bidi direction independently,
 * so the main risk is a placeholder value (a date, a duration) visually splitting a right-to-
 * left line. The bundled {@code messages_ar.yml} keeps each placeholder on its own line for
 * exactly this reason - see CONFIG.md for the full guidance when authoring new templates.
 */
public final class KickScreenRenderer {

    private final MessageService messages;

    public KickScreenRenderer(MessageService messages) {
        this.messages = messages;
    }

    public Component renderBan(Ban ban, SupportedLocale locale, String serverName) {
        String key = ban.kickscreenKey().map(k -> "kickscreen.template." + k).orElse("kickscreen.ban");
        if (!messages.hasKey(locale, key)) {
            key = "kickscreen.ban";
        }
        return render(key, PunishmentFormatter.of(ban, serverName, messages, locale), locale);
    }

    public Component renderIpBan(Ban ban, SupportedLocale locale, String serverName) {
        String key = "kickscreen.ban-ip";
        if (!messages.hasKey(locale, key)) {
            key = "kickscreen.ban";
        }
        return render(key, PunishmentFormatter.of(ban, serverName, messages, locale), locale);
    }

    public Component renderKick(Kick kick, SupportedLocale locale, String serverName) {
        String key = kick.kickscreenKey().map(k -> "kickscreen.template." + k).orElse("kickscreen.kick");
        if (!messages.hasKey(locale, key)) {
            key = "kickscreen.kick";
        }
        PlaceholderContext ctx = PlaceholderContext.create()
                .put("reason", kick.reason())
                .put("staff", kick.staffName())
                .put("server", serverName == null ? "-" : serverName)
                .put("id", String.valueOf(kick.id()));
        return render(key, ctx, locale);
    }

    private Component render(String key, PlaceholderContext ctx, SupportedLocale locale) {
        TagResolver[] resolvers = ctx.build();
        List<Component> lines = messages.getMultiline(locale, key, resolvers);
        Component result = Component.empty().decoration(TextDecoration.ITALIC, false);
        for (int i = 0; i < lines.size(); i++) {
            result = result.append(lines.get(i));
            if (i < lines.size() - 1) {
                result = result.append(Component.newline());
            }
        }
        return result;
    }
}
