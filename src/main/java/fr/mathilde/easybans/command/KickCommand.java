package fr.mathilde.easybans.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.mathilde.easybans.cache.OfflinePlayerCache;
import fr.mathilde.easybans.cache.UuidResolver;
import fr.mathilde.easybans.locale.LocaleService;
import fr.mathilde.easybans.message.MessageService;
import fr.mathilde.easybans.message.PlaceholderContext;
import fr.mathilde.easybans.message.PunishmentBroadcaster;
import fr.mathilde.easybans.punishment.PunishmentService;
import fr.mathilde.easybans.scope.ScopeResolver;

import java.util.List;
import java.util.Optional;

public final class KickCommand extends AbstractEasyBansCommand {

    private final PunishmentService punishmentService;
    private final ScopeResolver scopeResolver;
    private final PunishmentBroadcaster broadcaster;

    public KickCommand(ProxyServer proxy, MessageService messages, LocaleService localeService,
                        UuidResolver uuidResolver, OfflinePlayerCache offlinePlayerCache,
                        PunishmentService punishmentService, ScopeResolver scopeResolver,
                        PunishmentBroadcaster broadcaster) {
        super(proxy, messages, localeService, uuidResolver, offlinePlayerCache);
        this.punishmentService = punishmentService;
        this.scopeResolver = scopeResolver;
        this.broadcaster = broadcaster;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        if (!checkPermission(source, Permissions.KICK)) {
            return;
        }
        if (args.length < 1) {
            send(source, "commands.kick.usage");
            return;
        }
        String targetName = args[0];
        ParsedFlags flags = CommandArgs.parseFlags(args, 1);
        if (flags.server().isPresent() && !scopeResolver.isKnownServer(flags.server().get())) {
            send(source, "errors.unknown-server");
            return;
        }
        Optional<String> scope = scopeResolver.resolve(flags.server());
        boolean silent = flags.silent() && source.hasPermission(Permissions.SILENT);
        String reason = flags.remaining().isEmpty()
                ? messages.getPlain(localeOf(source), "commands.default-reason")
                : CommandArgs.joinFrom(flags.remaining(), 0);

        resolveTarget(source, targetName).thenAccept(uuidOpt -> uuidOpt.ifPresent(target -> {
            if (proxy.getPlayer(target).isEmpty()) {
                send(source, "errors.player-not-online", PlaceholderContext.create().put("player", targetName).build());
                return;
            }
            if (isExempt(source, target)) {
                return;
            }
            punishmentService.kick(target, targetName, reason, staffUuidOf(source), staffNameOf(source), scope, silent)
                    .thenAccept(kick -> {
                        var ctx = PlaceholderContext.create().put("player", targetName).put("reason", reason).build();
                        send(source, "commands.kick.success", ctx);
                        broadcaster.broadcast("commands.kick.broadcast", silent, ctx);
                    });
        }));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            // Kick requires the target to be online, so only suggest currently-connected players.
            return TabCompleteUtil.onlinePlayers(proxy, args.length == 0 ? "" : args[0]);
        }
        String currentToken = args[args.length - 1];
        if (currentToken.startsWith("-")) {
            return TabCompleteUtil.flags(currentToken, proxy, null, null, false);
        }
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(Permissions.KICK);
    }
}
