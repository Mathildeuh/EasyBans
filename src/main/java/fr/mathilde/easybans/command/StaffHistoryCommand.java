package fr.mathilde.easybans.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.mathilde.easybans.cache.UuidResolver;
import fr.mathilde.easybans.history.HistoryService;
import fr.mathilde.easybans.locale.LocaleService;
import fr.mathilde.easybans.message.HistoryEntryFormatter;
import fr.mathilde.easybans.message.MessageService;
import fr.mathilde.easybans.message.PlaceholderContext;
import fr.mathilde.easybans.punishment.Punishment;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class StaffHistoryCommand extends AbstractEasyBansCommand {

    private static final int PAGE_SIZE = 8;

    private final HistoryService historyService;

    public StaffHistoryCommand(ProxyServer proxy, MessageService messages, LocaleService localeService,
                                UuidResolver uuidResolver, HistoryService historyService) {
        super(proxy, messages, localeService, uuidResolver);
        this.historyService = historyService;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        if (!checkPermission(source, Permissions.STAFF_HISTORY)) {
            return;
        }
        if (args.length < 1) {
            send(source, "commands.staffhistory.usage");
            return;
        }
        String staffName = args[0];
        int page = parsePage(args);

        resolveTarget(source, staffName).thenAccept(uuidOpt -> uuidOpt.ifPresent(staffUuid ->
                historyService.staffHistory(staffUuid, page, PAGE_SIZE).thenAccept(result -> {
                    var locale = localeOf(source);
                    send(source, "commands.staffhistory.header", PlaceholderContext.create()
                            .put("staff", staffName)
                            .put("page", page + 1)
                            .put("total_pages", result.totalPages())
                            .put("total", result.totalCount())
                            .build());

                    List<CompletableFuture<Void>> sends = result.entries().stream()
                            .map(entry -> resolveEntryTargetName(entry).thenAccept(targetName ->
                                    source.sendMessage(messages.get(locale, "commands.history.entry",
                                            HistoryEntryFormatter.forEntry(entry, targetName, messages, locale).build()))))
                            .toList();
                    CompletableFuture.allOf(sends.toArray(new CompletableFuture[0]));
                })));
    }

    private CompletableFuture<String> resolveEntryTargetName(Punishment entry) {
        return uuidResolver.resolveName(entry.targetUuid()).thenApply(name -> name.orElse(entry.targetUuid().toString()));
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
        return invocation.source().hasPermission(Permissions.STAFF_HISTORY);
    }
}
