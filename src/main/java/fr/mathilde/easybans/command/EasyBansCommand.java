package fr.mathilde.easybans.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.mathilde.easybans.cache.UuidResolver;
import fr.mathilde.easybans.database.dao.IpExemptionDao;
import fr.mathilde.easybans.importer.ImportReport;
import fr.mathilde.easybans.importer.ImportService;
import fr.mathilde.easybans.importer.ImportSource;
import fr.mathilde.easybans.locale.LocaleService;
import fr.mathilde.easybans.message.MessageService;
import fr.mathilde.easybans.message.PlaceholderContext;
import fr.mathilde.easybans.rollback.RollbackService;
import fr.mathilde.easybans.template.TemplateRegistry;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** {@code /easybans} - the admin root command: allow, rollback, import, language, template, reload. */
public final class EasyBansCommand extends AbstractEasyBansCommand {

    private final IpExemptionDao ipExemptionDao;
    private final RollbackService rollbackService;
    private final ImportService importService;
    private final TemplateRegistry templateRegistry;
    private final Runnable reloadCallback;
    private final String pluginVersion;

    public EasyBansCommand(ProxyServer proxy, MessageService messages, LocaleService localeService,
                            UuidResolver uuidResolver, IpExemptionDao ipExemptionDao, RollbackService rollbackService,
                            ImportService importService, TemplateRegistry templateRegistry, Runnable reloadCallback,
                            String pluginVersion) {
        super(proxy, messages, localeService, uuidResolver);
        this.ipExemptionDao = ipExemptionDao;
        this.rollbackService = rollbackService;
        this.importService = importService;
        this.templateRegistry = templateRegistry;
        this.reloadCallback = reloadCallback;
        this.pluginVersion = pluginVersion;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        if (args.length == 0) {
            sendHelp(source);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "allow" -> handleAllow(source, args);
            case "rollback" -> handleRollback(source, args);
            case "import" -> handleImport(source, args);
            case "language", "lang" -> handleLanguage(source, args);
            case "template", "templates" -> handleTemplate(source, args);
            case "reload" -> handleReload(source);
            case "version" -> send(source, "commands.easybans.version",
                    PlaceholderContext.create().put("version", pluginVersion).build());
            default -> sendHelp(source);
        }
    }

    private void sendHelp(CommandSource source) {
        send(source, "commands.easybans.help");
    }

    // ---------------------------------------------------------------- allow

    private void handleAllow(CommandSource source, String[] args) {
        if (!checkPermission(source, Permissions.ALLOW)) {
            return;
        }
        if (args.length < 3) {
            send(source, "commands.easybans.allow.usage");
            return;
        }
        String targetName = args[1];
        String ip = args[2];
        resolveTarget(source, targetName).thenAccept(uuidOpt -> uuidOpt.ifPresent(target ->
                ipExemptionDao.allow(target, ip, staffUuidOf(source), staffNameOf(source), "Exempted via /easybans allow")
                        .thenAccept(created -> {
                            var ctx = PlaceholderContext.create().put("player", targetName).put("ip", ip).build();
                            send(source, created ? "commands.easybans.allow.success" : "commands.easybans.allow.already", ctx);
                        })));
    }

    // ---------------------------------------------------------------- rollback

    private void handleRollback(CommandSource source, String[] args) {
        if (!checkPermission(source, Permissions.ROLLBACK)) {
            return;
        }
        if (args.length < 2) {
            send(source, "commands.easybans.rollback.usage");
            return;
        }
        if (args[1].equalsIgnoreCase("confirm")) {
            rollbackService.confirmRollback(staffUuidOf(source), staffNameOf(source)).thenAccept(countOpt -> {
                if (countOpt.isEmpty()) {
                    send(source, "commands.easybans.rollback.no-pending");
                } else {
                    send(source, "commands.easybans.rollback.done",
                            PlaceholderContext.create().put("count", countOpt.get()).build());
                }
            });
            return;
        }
        String targetStaffName = args[1];
        resolveTarget(source, targetStaffName).thenAccept(uuidOpt -> uuidOpt.ifPresent(targetStaff -> {
            rollbackService.requestRollback(staffUuidOf(source), targetStaff, targetStaffName);
            send(source, "commands.easybans.rollback.confirm-prompt",
                    PlaceholderContext.create().put("staff", targetStaffName).build());
        }));
    }

    // ---------------------------------------------------------------- import

    private void handleImport(CommandSource source, String[] args) {
        if (!checkPermission(source, Permissions.IMPORT)) {
            return;
        }
        if (args.length < 3) {
            send(source, "commands.easybans.import.usage");
            return;
        }
        String sourceId = args[1];
        String location = args[2];
        ImportSource importSource;
        try {
            importSource = ImportSource.valueOf(sourceId.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            send(source, "commands.easybans.import.unknown-source",
                    PlaceholderContext.create().put("source", sourceId).build());
            return;
        }

        send(source, "commands.easybans.import.started",
                PlaceholderContext.create().put("source", importSource.name()).build());
        importService.importFrom(importSource, location, staffUuidOf(source), staffNameOf(source))
                .thenAccept(report -> send(source, "commands.easybans.import.report", PlaceholderContext.create()
                        .put("imported", report.imported())
                        .put("skipped", report.skippedDuplicates())
                        .put("errors", report.errors())
                        .build()))
                .exceptionally(e -> {
                    send(source, "commands.easybans.import.failed",
                            PlaceholderContext.create().put("error", String.valueOf(e.getMessage())).build());
                    return null;
                });
    }

    // ---------------------------------------------------------------- language

    private void handleLanguage(CommandSource source, String[] args) {
        if (rejectIfNotPlayer(source)) {
            return;
        }
        Player player = (Player) source;
        if (args.length < 2) {
            send(source, "commands.easybans.language.usage");
            return;
        }
        localeService.setLocale(player.getUniqueId(), args[1]).thenAccept(success -> {
            if (success) {
                send(source, "commands.easybans.language.success",
                        PlaceholderContext.create().put("locale", args[1]).build());
            } else {
                send(source, "commands.easybans.language.unsupported",
                        PlaceholderContext.create().put("locale", args[1]).build());
            }
        });
    }

    // ---------------------------------------------------------------- template

    private void handleTemplate(CommandSource source, String[] args) {
        if (!checkPermission(source, Permissions.TEMPLATE)) {
            return;
        }
        String templates = String.join(", ", templateRegistry.templateIds());
        String categories = String.join(", ", templateRegistry.categoryIds());
        send(source, "commands.easybans.template.list", PlaceholderContext.create()
                .put("templates", templates.isBlank() ? "-" : templates)
                .put("categories", categories.isBlank() ? "-" : categories)
                .build());
    }

    // ---------------------------------------------------------------- reload

    private void handleReload(CommandSource source) {
        if (!checkPermission(source, Permissions.RELOAD)) {
            return;
        }
        reloadCallback.run();
        send(source, "commands.easybans.reload.success");
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return List.of("allow", "rollback", "import", "language", "template", "reload", "version").stream()
                    .filter(s -> s.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
