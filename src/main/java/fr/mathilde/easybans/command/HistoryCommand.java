package fr.mathilde.easybans.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.mathilde.easybans.cache.UuidResolver;
import fr.mathilde.easybans.history.HistoryService;
import fr.mathilde.easybans.locale.LocaleService;
import fr.mathilde.easybans.message.MessageService;
import fr.mathilde.easybans.message.PlaceholderContext;

public final class HistoryCommand extends AbstractEasyBansCommand {

    private static final int PAGE_SIZE = 8;

    private final HistoryService historyService;

    public HistoryCommand(ProxyServer proxy, MessageService messages, LocaleService localeService,
                           UuidResolver uuidResolver, HistoryService historyService) {
        super(proxy, messages, localeService, uuidResolver);
        this.historyService = historyService;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        if (!checkPermission(source, Permissions.HISTORY)) {
            return;
        }
        if (args.length < 1) {
            send(source, "commands.history.usage");
            return;
        }
        String targetName = args[0];
        int page = parsePage(args);

        resolveTarget(source, targetName).thenAccept(uuidOpt -> uuidOpt.ifPresent(target ->
                historyService.playerHistory(target, page, PAGE_SIZE).thenAccept(result -> {
                    var locale = localeOf(source);
                    send(source, "commands.history.header", PlaceholderContext.create()
                            .put("player", targetName)
                            .put("page", page + 1)
                            .put("total_pages", result.totalPages())
                            .put("total", result.totalCount())
                            .build());
                    for (var entry : result.entries()) {
                        source.sendMessage(messages.get(locale, "commands.history.entry",
                                fr.mathilde.easybans.message.HistoryEntryFormatter.forEntry(entry, targetName, messages, locale).build()));
                    }
                })));
    }

    private int parsePage(String[] args) {
        if (args.length < 2) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(args[1]) - 1);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(Permissions.HISTORY);
    }
}
