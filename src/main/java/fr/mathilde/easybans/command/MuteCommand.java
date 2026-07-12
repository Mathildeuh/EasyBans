package fr.mathilde.easybans.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.mathilde.easybans.cache.OfflinePlayerCache;
import fr.mathilde.easybans.cache.UuidResolver;
import fr.mathilde.easybans.locale.LocaleService;
import fr.mathilde.easybans.message.MessageService;
import fr.mathilde.easybans.message.PlaceholderContext;
import fr.mathilde.easybans.message.PunishmentBroadcaster;
import fr.mathilde.easybans.message.PunishmentFormatter;
import fr.mathilde.easybans.punishment.Mute;
import fr.mathilde.easybans.punishment.PunishmentActionResult;
import fr.mathilde.easybans.punishment.PunishmentOutcome;
import fr.mathilde.easybans.punishment.PunishmentService;
import fr.mathilde.easybans.punishment.PunishmentType;
import fr.mathilde.easybans.scope.ScopeResolver;
import fr.mathilde.easybans.template.TemplateRegistry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class MuteCommand extends AbstractEasyBansCommand {

    private final PunishmentService punishmentService;
    private final ScopeResolver scopeResolver;
    private final PunishmentBroadcaster broadcaster;
    private final TemplateRegistry templateRegistry;

    public MuteCommand(ProxyServer proxy, MessageService messages, LocaleService localeService, UuidResolver uuidResolver,
                        OfflinePlayerCache offlinePlayerCache, PunishmentService punishmentService,
                        ScopeResolver scopeResolver, PunishmentBroadcaster broadcaster, TemplateRegistry templateRegistry) {
        super(proxy, messages, localeService, uuidResolver, offlinePlayerCache);
        this.punishmentService = punishmentService;
        this.scopeResolver = scopeResolver;
        this.broadcaster = broadcaster;
        this.templateRegistry = templateRegistry;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        if (!checkPermission(source, Permissions.MUTE)) {
            return;
        }
        if (args.length < 1) {
            send(source, "commands.mute.usage");
            return;
        }

        String targetName = args[0];
        ParsedFlags flags = CommandArgs.parseFlags(args, 1);
        if (flags.server().isPresent() && !scopeResolver.isKnownServer(flags.server().get())) {
            send(source, "errors.unknown-server");
            return;
        }
        Optional<String> scope = scopeResolver.resolve(flags.server());
        boolean bypassOverride = source.hasPermission(Permissions.OVERRIDE);
        boolean silent = flags.silent() && source.hasPermission(Permissions.SILENT);
        UUID staffUuid = staffUuidOf(source);
        String staffName = staffNameOf(source);

        resolveTarget(source, targetName).thenAccept(uuidOpt -> uuidOpt.ifPresent(target -> {
            if (isExempt(source, target)) {
                return;
            }

            if (flags.template().isPresent()) {
                punishmentService.muteFromTemplate(target, targetName, flags.template().get(), staffUuid, staffName,
                                scope, silent, bypassOverride)
                        .thenAccept(result -> handleResult(source, targetName, result));
                return;
            }

            List<String> remaining = flags.remaining();
            if (remaining.isEmpty()) {
                send(source, "commands.mute.usage");
                return;
            }
            CommandArgs.DurationAndReason parsed = CommandArgs.parseDurationAndReason(remaining, defaultReason(source));

            punishmentService.mute(target, targetName, parsed.reason(), staffUuid, staffName, scope,
                            parsed.duration(), silent, bypassOverride)
                    .thenAccept(result -> handleResult(source, targetName, result));
        }));
    }

    private void handleResult(CommandSource source, String targetName, PunishmentActionResult<Mute> result) {
        if (result.outcome() == PunishmentOutcome.ALREADY_PUNISHED) {
            send(source, "errors.already-muted", PlaceholderContext.create().put("player", targetName).build());
            return;
        }
        if (result.outcome() == PunishmentOutcome.TEMPLATE_NOT_FOUND) {
            send(source, "errors.template-not-found");
            return;
        }
        if (result.outcome() == PunishmentOutcome.TEMPLATE_WRONG_TYPE) {
            send(source, "errors.template-wrong-type");
            return;
        }
        Mute mute = result.punishment().orElseThrow();
        var locale = localeOf(source);
        var ctx = PunishmentFormatter.of(mute, null, messages, locale).put("player", targetName);
        send(source, "commands.mute.success", ctx.build());

        var broadcastCtx = PunishmentFormatter.of(mute, null, messages, locale).put("player", targetName);
        broadcaster.broadcast("commands.mute.broadcast", mute.silent(), broadcastCtx.build());
    }

    private String defaultReason(CommandSource source) {
        return messages.getPlain(localeOf(source), "commands.default-reason");
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            return suggestPlayers(args.length == 0 ? "" : args[0]);
        }
        String currentToken = args[args.length - 1];
        if (currentToken.startsWith("-")) {
            return TabCompleteUtil.flags(currentToken, proxy, templateRegistry, PunishmentType.MUTE, true);
        }
        if (args.length == 2) {
            return TabCompleteUtil.durations(currentToken);
        }
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(Permissions.MUTE);
    }
}
