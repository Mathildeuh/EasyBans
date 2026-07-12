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
import fr.mathilde.easybans.template.TemplateRegistry;
import fr.mathilde.easybans.warning.WarningTriggerService;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class WarnCommand extends AbstractEasyBansCommand {

    private final PunishmentService punishmentService;
    private final WarningTriggerService triggerService;
    private final TemplateRegistry templateRegistry;
    private final PunishmentBroadcaster broadcaster;

    public WarnCommand(ProxyServer proxy, MessageService messages, LocaleService localeService,
                        UuidResolver uuidResolver, OfflinePlayerCache offlinePlayerCache,
                        PunishmentService punishmentService, WarningTriggerService triggerService,
                        TemplateRegistry templateRegistry, PunishmentBroadcaster broadcaster) {
        super(proxy, messages, localeService, uuidResolver, offlinePlayerCache);
        this.punishmentService = punishmentService;
        this.triggerService = triggerService;
        this.templateRegistry = templateRegistry;
        this.broadcaster = broadcaster;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        if (!checkPermission(source, Permissions.WARN)) {
            return;
        }
        if (args.length < 2) {
            send(source, "commands.warn.usage");
            return;
        }
        String targetName = args[0];
        String category = args[1];
        if (templateRegistry.category(category).isEmpty()) {
            send(source, "errors.unknown-category", PlaceholderContext.create().put("category", category).build());
            return;
        }
        ParsedFlags flags = CommandArgs.parseFlags(args, 2);
        boolean silent = flags.silent() && source.hasPermission(Permissions.SILENT);
        String reason = flags.remaining().isEmpty()
                ? messages.getPlain(localeOf(source), "commands.default-reason")
                : CommandArgs.joinFrom(flags.remaining(), 0);

        resolveTarget(source, targetName).thenAccept(uuidOpt -> uuidOpt.ifPresent(target -> {
            if (isExempt(source, target)) {
                return;
            }
            punishmentService.warn(target, targetName, category, reason, staffUuidOf(source), staffNameOf(source),
                    Optional.empty(), silent).thenAccept(warning -> {
                var ctx = PlaceholderContext.create()
                        .put("player", targetName)
                        .put("category", category)
                        .put("reason", reason)
                        .build();
                send(source, "commands.warn.success", ctx);
                broadcaster.broadcast("commands.warn.broadcast", silent, ctx);
                triggerService.onWarningIssued(target, targetName, category);
            });
        }));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            return suggestPlayers(args.length == 0 ? "" : args[0]);
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            return templateRegistry.categoryIds().stream().filter(c -> c.startsWith(prefix)).collect(Collectors.toList());
        }
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(Permissions.WARN);
    }
}
