package fr.mathilde.easybans.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.mathilde.easybans.cache.UuidResolver;
import fr.mathilde.easybans.locale.LocaleService;
import fr.mathilde.easybans.locale.SupportedLocale;
import fr.mathilde.easybans.message.MessageService;
import fr.mathilde.easybans.message.PlaceholderContext;
import fr.mathilde.easybans.punishment.PunishmentConstants;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public abstract class AbstractEasyBansCommand implements SimpleCommand {

    protected final ProxyServer proxy;
    protected final MessageService messages;
    protected final LocaleService localeService;
    protected final UuidResolver uuidResolver;

    protected AbstractEasyBansCommand(ProxyServer proxy, MessageService messages, LocaleService localeService,
                                       UuidResolver uuidResolver) {
        this.proxy = proxy;
        this.messages = messages;
        this.localeService = localeService;
        this.uuidResolver = uuidResolver;
    }

    protected SupportedLocale localeOf(CommandSource source) {
        if (source instanceof Player player) {
            return localeService.getCached(player.getUniqueId());
        }
        return localeService.defaultLocale();
    }

    protected void send(CommandSource source, String key, TagResolver... resolvers) {
        source.sendMessage(messages.get(localeOf(source), key, resolvers));
    }

    protected boolean checkPermission(CommandSource source, String permission) {
        if (!source.hasPermission(permission)) {
            send(source, "errors.no-permission");
            return false;
        }
        return true;
    }

    protected UUID staffUuidOf(CommandSource source) {
        return source instanceof Player player ? player.getUniqueId() : PunishmentConstants.CONSOLE_UUID;
    }

    protected String staffNameOf(CommandSource source) {
        return source instanceof Player player ? player.getUsername() : PunishmentConstants.CONSOLE_NAME;
    }

    /** Resolves a target name to a UUID, sending an error message and completing empty if not found. */
    protected CompletableFuture<Optional<UUID>> resolveTarget(CommandSource source, String name) {
        return uuidResolver.resolveUuid(name).thenApply(uuid -> {
            if (uuid.isEmpty()) {
                PlaceholderContext ctx = PlaceholderContext.create().put("player", name);
                send(source, "errors.player-not-found", ctx.build());
            }
            return uuid;
        });
    }

    /** True (and sends a message) if the target is online and holds easybans.exempt. */
    protected boolean isExempt(CommandSource source, UUID target) {
        Optional<Player> online = proxy.getPlayer(target);
        if (online.isPresent() && online.get().hasPermission(Permissions.EXEMPT)) {
            send(source, "errors.target-exempt");
            return true;
        }
        return false;
    }

    /** True (and sends a message) if this command was run from a source that isn't a player. */
    protected boolean rejectIfNotPlayer(CommandSource source) {
        if (!(source instanceof Player)) {
            send(source, "errors.players-only");
            return true;
        }
        return false;
    }
}
