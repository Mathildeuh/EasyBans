package fr.mathilde.easybans.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.mathilde.easybans.cache.UuidResolver;
import fr.mathilde.easybans.locale.LocaleService;
import fr.mathilde.easybans.message.MessageService;
import fr.mathilde.easybans.message.PlaceholderContext;
import fr.mathilde.easybans.message.PunishmentBroadcaster;
import fr.mathilde.easybans.message.PunishmentFormatter;
import fr.mathilde.easybans.punishment.Ban;
import fr.mathilde.easybans.punishment.PunishmentActionResult;
import fr.mathilde.easybans.punishment.PunishmentOutcome;
import fr.mathilde.easybans.punishment.PunishmentService;
import fr.mathilde.easybans.scope.ScopeResolver;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Backs both {@code /ban} and {@code /banip} - the only difference is whether the player's current IP is banned too. */
public final class BanCommand extends AbstractEasyBansCommand {

    private final PunishmentService punishmentService;
    private final ScopeResolver scopeResolver;
    private final PunishmentBroadcaster broadcaster;
    private final boolean ipMode;

    public BanCommand(ProxyServer proxy, MessageService messages, LocaleService localeService, UuidResolver uuidResolver,
                       PunishmentService punishmentService, ScopeResolver scopeResolver,
                       PunishmentBroadcaster broadcaster, boolean ipMode) {
        super(proxy, messages, localeService, uuidResolver);
        this.punishmentService = punishmentService;
        this.scopeResolver = scopeResolver;
        this.broadcaster = broadcaster;
        this.ipMode = ipMode;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        String permission = ipMode ? Permissions.BAN_IP : Permissions.BAN;
        if (!checkPermission(source, permission)) {
            return;
        }
        if (args.length < 1) {
            send(source, ipMode ? "commands.banip.usage" : "commands.ban.usage");
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
                punishmentService.banFromTemplate(target, targetName, flags.template().get(), staffUuid, staffName,
                                scope, silent, bypassOverride)
                        .thenAccept(result -> handleResult(source, targetName, result));
                return;
            }

            List<String> remaining = flags.remaining();
            if (remaining.isEmpty()) {
                send(source, ipMode ? "commands.banip.usage" : "commands.ban.usage");
                return;
            }
            CommandArgs.DurationAndReason parsed = CommandArgs.parseDurationAndReason(remaining, defaultReason(source));

            Optional<String> ip = ipMode ? currentIpOf(target) : Optional.empty();
            if (ipMode && ip.isEmpty()) {
                send(source, "errors.player-never-joined");
                return;
            }

            punishmentService.ban(target, targetName, ip, ipMode, parsed.reason(), staffUuid, staffName, scope,
                            parsed.duration(), silent, bypassOverride)
                    .thenAccept(result -> handleResult(source, targetName, result));
        }));
    }

    private Optional<String> currentIpOf(UUID target) {
        return proxy.getPlayer(target)
                .map(Player::getRemoteAddress)
                .map(addr -> addr.getAddress().getHostAddress());
    }

    private void handleResult(CommandSource source, String targetName, PunishmentActionResult<Ban> result) {
        if (result.outcome() == PunishmentOutcome.ALREADY_PUNISHED) {
            send(source, "errors.already-banned", PlaceholderContext.create().put("player", targetName).build());
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
        Ban ban = result.punishment().orElseThrow();
        var locale = localeOf(source);
        var ctx = PunishmentFormatter.of(ban, null, messages, locale).put("player", targetName);
        send(source, ipMode ? "commands.banip.success" : "commands.ban.success", ctx.build());

        var broadcastCtx = PunishmentFormatter.of(ban, null, messages, locale).put("player", targetName);
        broadcaster.broadcast(ipMode ? "commands.banip.broadcast" : "commands.ban.broadcast", ban.silent(), broadcastCtx.build());
    }

    private String defaultReason(CommandSource source) {
        return messages.get(localeOf(source), "commands.default-reason").toString();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(ipMode ? Permissions.BAN_IP : Permissions.BAN);
    }
}
