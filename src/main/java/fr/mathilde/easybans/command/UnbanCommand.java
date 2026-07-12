package fr.mathilde.easybans.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.mathilde.easybans.cache.UuidResolver;
import fr.mathilde.easybans.locale.LocaleService;
import fr.mathilde.easybans.message.MessageService;
import fr.mathilde.easybans.message.PlaceholderContext;
import fr.mathilde.easybans.punishment.PunishmentOutcome;
import fr.mathilde.easybans.punishment.PunishmentService;

import java.util.List;

public final class UnbanCommand extends AbstractEasyBansCommand {

    private final PunishmentService punishmentService;

    public UnbanCommand(ProxyServer proxy, MessageService messages, LocaleService localeService,
                         UuidResolver uuidResolver, PunishmentService punishmentService) {
        super(proxy, messages, localeService, uuidResolver);
        this.punishmentService = punishmentService;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        if (!checkPermission(source, Permissions.UNBAN)) {
            return;
        }
        if (args.length < 1) {
            send(source, "commands.unban.usage");
            return;
        }
        String targetName = args[0];
        String reason = args.length > 1 ? CommandArgs.joinFrom(List.of(args), 1) : defaultReason(source);

        resolveTarget(source, targetName).thenAccept(uuidOpt -> uuidOpt.ifPresent(target ->
                punishmentService.unban(target, staffUuidOf(source), staffNameOf(source), reason).thenAccept(outcome -> {
                    var ctx = PlaceholderContext.create().put("player", targetName).build();
                    if (outcome == PunishmentOutcome.NOT_FOUND) {
                        send(source, "errors.not-banned", ctx);
                    } else {
                        send(source, "commands.unban.success", ctx);
                    }
                })));
    }

    private String defaultReason(CommandSource source) {
        return messages.get(localeOf(source), "commands.default-reason").toString();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(Permissions.UNBAN);
    }
}
