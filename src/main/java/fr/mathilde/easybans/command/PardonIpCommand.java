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

/** Vanilla's {@code /pardon-ip <ip>} - lifts an IP ban directly by address, no player lookup involved. */
public final class PardonIpCommand extends AbstractEasyBansCommand {

    private final PunishmentService punishmentService;

    public PardonIpCommand(ProxyServer proxy, MessageService messages, LocaleService localeService,
                            UuidResolver uuidResolver, OfflinePlayerCache offlinePlayerCache,
                            PunishmentService punishmentService) {
        super(proxy, messages, localeService, uuidResolver, offlinePlayerCache);
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
            send(source, "commands.pardonip.usage");
            return;
        }
        String ip = args[0];
        String reason = args.length > 1 ? CommandArgs.joinFrom(List.of(args), 1) : defaultReason(source);

        punishmentService.unbanIp(ip, staffUuidOf(source), staffNameOf(source), reason).thenAccept(outcome -> {
            var ctx = PlaceholderContext.create().put("ip", ip).build();
            if (outcome == PunishmentOutcome.NOT_FOUND) {
                send(source, "errors.ip-not-banned", ctx);
            } else {
                send(source, "commands.pardonip.success", ctx);
            }
        });
    }

    private String defaultReason(CommandSource source) {
        return messages.getPlain(localeOf(source), "commands.default-reason");
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(Permissions.UNBAN);
    }
}
