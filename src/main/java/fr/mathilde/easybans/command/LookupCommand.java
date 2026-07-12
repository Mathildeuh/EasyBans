package fr.mathilde.easybans.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.mathilde.easybans.cache.OfflinePlayerCache;
import fr.mathilde.easybans.cache.UuidResolver;
import fr.mathilde.easybans.locale.LocaleService;
import fr.mathilde.easybans.message.MessageService;
import fr.mathilde.easybans.message.PlaceholderContext;
import fr.mathilde.easybans.message.PunishmentFormatter;
import fr.mathilde.easybans.punishment.Punishment;
import fr.mathilde.easybans.punishment.PunishmentService;

import java.util.List;

/** {@code /lookup <id>} - shows a single punishment's full details by its database id. */
public final class LookupCommand extends AbstractEasyBansCommand {

    private final PunishmentService punishmentService;

    public LookupCommand(ProxyServer proxy, MessageService messages, LocaleService localeService,
                          UuidResolver uuidResolver, OfflinePlayerCache offlinePlayerCache,
                          PunishmentService punishmentService) {
        super(proxy, messages, localeService, uuidResolver, offlinePlayerCache);
        this.punishmentService = punishmentService;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        if (!checkPermission(source, Permissions.HISTORY)) {
            return;
        }
        if (args.length < 1) {
            send(source, "commands.lookup.usage");
            return;
        }
        long id;
        try {
            id = Long.parseLong(args[0]);
        } catch (NumberFormatException e) {
            send(source, "commands.lookup.usage");
            return;
        }

        punishmentService.findById(id).thenAccept(punishmentOpt -> {
            if (punishmentOpt.isEmpty()) {
                send(source, "commands.lookup.not-found", PlaceholderContext.create().put("id", id).build());
                return;
            }
            Punishment punishment = punishmentOpt.get();
            uuidResolver.resolveName(punishment.targetUuid()).thenAccept(nameOpt -> {
                var locale = localeOf(source);
                String status = messages.getPlain(locale, punishment.isCurrentlyActive() ? "common.status-active" : "common.status-inactive");
                var ctx = PunishmentFormatter.of(punishment, null, messages, locale)
                        .put("type", punishment.type().name())
                        .put("target", nameOpt.orElse(punishment.targetUuid().toString()))
                        .put("scope", punishment.serverScope().orElse("Global"))
                        .put("status", status)
                        .build();
                send(source, "commands.lookup.entry", ctx);
            });
        });
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(Permissions.HISTORY);
    }
}
