package fr.mathilde.easybans.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.mathilde.easybans.cache.OfflinePlayerCache;
import fr.mathilde.easybans.cache.UuidResolver;
import fr.mathilde.easybans.locale.LocaleService;
import fr.mathilde.easybans.message.MessageService;
import fr.mathilde.easybans.message.PlaceholderContext;
import fr.mathilde.easybans.punishment.PunishmentOutcome;
import fr.mathilde.easybans.punishment.PunishmentService;

import java.util.List;

public final class UnmuteCommand extends AbstractEasyBansCommand {

    private final PunishmentService punishmentService;

    public UnmuteCommand(ProxyServer proxy, MessageService messages, LocaleService localeService,
                          UuidResolver uuidResolver, OfflinePlayerCache offlinePlayerCache,
                          PunishmentService punishmentService) {
        super(proxy, messages, localeService, uuidResolver, offlinePlayerCache);
        this.punishmentService = punishmentService;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        if (!checkPermission(source, Permissions.UNMUTE)) {
            return;
        }
        if (args.length < 1) {
            send(source, "commands.unmute.usage");
            return;
        }
        String targetName = args[0];
        String reason = args.length > 1 ? CommandArgs.joinFrom(List.of(args), 1) : defaultReason(source);

        resolveTarget(source, targetName).thenAccept(uuidOpt -> uuidOpt.ifPresent(target ->
                punishmentService.unmute(target, targetName, staffUuidOf(source), staffNameOf(source), reason).thenAccept(outcome -> {
                    var ctx = PlaceholderContext.create().put("player", targetName).build();
                    if (outcome == PunishmentOutcome.NOT_FOUND) {
                        send(source, "errors.not-muted", ctx);
                    } else {
                        send(source, "commands.unmute.success", ctx);
                    }
                })));
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
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(Permissions.UNMUTE);
    }
}
