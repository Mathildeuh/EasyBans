package fr.mathilde.easybans.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.mathilde.easybans.cache.OfflinePlayerCache;
import fr.mathilde.easybans.cache.UuidResolver;
import fr.mathilde.easybans.locale.LocaleService;
import fr.mathilde.easybans.message.MessageService;
import fr.mathilde.easybans.message.PlaceholderContext;
import fr.mathilde.easybans.punishment.Ban;
import fr.mathilde.easybans.punishment.PunishmentService;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Vanilla's {@code /banlist [players|ips]} - lists currently active bans (paginated at 8/page like /history). */
public final class BanListCommand extends AbstractEasyBansCommand {

    private static final int PAGE_SIZE = 8;

    private final PunishmentService punishmentService;

    public BanListCommand(ProxyServer proxy, MessageService messages, LocaleService localeService,
                           UuidResolver uuidResolver, OfflinePlayerCache offlinePlayerCache,
                           PunishmentService punishmentService) {
        super(proxy, messages, localeService, uuidResolver, offlinePlayerCache);
        this.punishmentService = punishmentService;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        if (!checkPermission(source, Permissions.BANLIST)) {
            return;
        }
        boolean ipOnly = args.length > 0 && args[0].equalsIgnoreCase("ips");
        boolean hasFilterKeyword = args.length > 0 && (ipOnly || args[0].equalsIgnoreCase("players"));
        int page = parsePage(args, hasFilterKeyword ? 1 : 0);

        CompletableFuture<List<Ban>> bansFuture = punishmentService.activeBans(ipOnly, page, PAGE_SIZE);
        CompletableFuture<Integer> countFuture = punishmentService.countActiveBans(ipOnly);

        bansFuture.thenCombine(countFuture, (bans, total) -> {
            int totalPages = Math.max(1, (int) Math.ceil(total / (double) PAGE_SIZE));
            send(source, "commands.banlist.header", PlaceholderContext.create()
                    .put("page", page + 1)
                    .put("total_pages", totalPages)
                    .put("total", total)
                    .build());

            List<CompletableFuture<Void>> sends = bans.stream()
                    .map(ban -> resolveTargetLabel(ban).thenAccept(label -> {
                        var ctx = PlaceholderContext.create()
                                .put("id", String.valueOf(ban.id()))
                                .put("target", label)
                                .put("reason", ban.reason())
                                .put("staff", ban.staffName())
                                .build();
                        send(source, "commands.banlist.entry", ctx);
                    }))
                    .toList();
            CompletableFuture.allOf(sends.toArray(new CompletableFuture[0]));
            return null;
        });
    }

    private CompletableFuture<String> resolveTargetLabel(Ban ban) {
        if (ban.ipBanned()) {
            return CompletableFuture.completedFuture(ban.targetIp().orElse("?"));
        }
        return uuidResolver.resolveName(ban.targetUuid()).thenApply(name -> name.orElse(ban.targetUuid().toString()));
    }

    private int parsePage(String[] args, int fromIndex) {
        if (args.length <= fromIndex) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(args[fromIndex]) - 1);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase();
            return List.of("players", "ips").stream().filter(s -> s.startsWith(prefix)).toList();
        }
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(Permissions.BANLIST);
    }
}
