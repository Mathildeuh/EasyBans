package fr.mathilde.easybans.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.mathilde.easybans.cache.OfflinePlayerCache;
import fr.mathilde.easybans.cache.UuidResolver;
import fr.mathilde.easybans.locale.LocaleService;
import fr.mathilde.easybans.message.MessageService;
import fr.mathilde.easybans.message.PlaceholderContext;
import fr.mathilde.easybans.punishment.PunishmentService;

import java.util.List;

public final class NoteCommand extends AbstractEasyBansCommand {

    private final PunishmentService punishmentService;

    public NoteCommand(ProxyServer proxy, MessageService messages, LocaleService localeService,
                        UuidResolver uuidResolver, OfflinePlayerCache offlinePlayerCache,
                        PunishmentService punishmentService) {
        super(proxy, messages, localeService, uuidResolver, offlinePlayerCache);
        this.punishmentService = punishmentService;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        if (!checkPermission(source, Permissions.NOTE)) {
            return;
        }
        if (args.length < 2) {
            send(source, "commands.note.usage");
            return;
        }
        String targetName = args[0];
        String note = CommandArgs.joinFrom(List.of(args), 1);

        resolveTarget(source, targetName).thenAccept(uuidOpt -> uuidOpt.ifPresent(target ->
                punishmentService.note(target, note, staffUuidOf(source), staffNameOf(source)).thenAccept(n -> {
                    var ctx = PlaceholderContext.create().put("player", targetName).build();
                    send(source, "commands.note.success", ctx);
                })));
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
        return invocation.source().hasPermission(Permissions.NOTE);
    }
}
