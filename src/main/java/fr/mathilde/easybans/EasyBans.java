package fr.mathilde.easybans;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.mathilde.easybans.bridge.StaffBridgeListener;
import fr.mathilde.easybans.bridge.StaffBridgeProtocol;
import fr.mathilde.easybans.cache.ActiveMuteCache;
import fr.mathilde.easybans.cache.OfflinePlayerCache;
import fr.mathilde.easybans.cache.UuidResolver;
import fr.mathilde.easybans.command.BanCommand;
import fr.mathilde.easybans.command.BanListCommand;
import fr.mathilde.easybans.command.EasyBansCommand;
import fr.mathilde.easybans.command.HistoryCommand;
import fr.mathilde.easybans.command.KickCommand;
import fr.mathilde.easybans.command.LookupCommand;
import fr.mathilde.easybans.command.MuteCommand;
import fr.mathilde.easybans.command.NoteCommand;
import fr.mathilde.easybans.command.PardonIpCommand;
import fr.mathilde.easybans.command.StaffHistoryCommand;
import fr.mathilde.easybans.command.UnbanCommand;
import fr.mathilde.easybans.command.UnmuteCommand;
import fr.mathilde.easybans.command.WarnCommand;
import fr.mathilde.easybans.config.ConfigLoader;
import fr.mathilde.easybans.config.EasyBansConfig;
import fr.mathilde.easybans.database.DatabaseProvider;
import fr.mathilde.easybans.database.dao.IpExemptionDao;
import fr.mathilde.easybans.database.dao.IpHistoryDao;
import fr.mathilde.easybans.database.dao.PlayerDao;
import fr.mathilde.easybans.database.dao.PunishmentDao;
import fr.mathilde.easybans.database.dao.SyncEventDao;
import fr.mathilde.easybans.database.migration.MigrationRunner;
import fr.mathilde.easybans.discord.DiscordWebhookService;
import fr.mathilde.easybans.history.HistoryService;
import fr.mathilde.easybans.importer.ImportService;
import fr.mathilde.easybans.kickscreen.KickScreenRenderer;
import fr.mathilde.easybans.linked.LinkedAccountService;
import fr.mathilde.easybans.listener.ChatMuteListener;
import fr.mathilde.easybans.listener.ConnectionListener;
import fr.mathilde.easybans.locale.LocaleService;
import fr.mathilde.easybans.message.MessageService;
import fr.mathilde.easybans.message.PunishmentBroadcaster;
import fr.mathilde.easybans.mute.MuteNetworkSync;
import fr.mathilde.easybans.punishment.PunishmentService;
import fr.mathilde.easybans.rollback.RollbackService;
import fr.mathilde.easybans.scope.ScopeResolver;
import fr.mathilde.easybans.sync.SyncEventType;
import fr.mathilde.easybans.sync.SyncService;
import fr.mathilde.easybans.sync.SyncServiceFactory;
import fr.mathilde.easybans.template.TemplateRegistry;
import fr.mathilde.easybans.warning.WarningTriggerService;
import org.bstats.velocity.Metrics;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.UUID;

public class EasyBans {

    private static final int BSTATS_PLUGIN_ID = 32581;

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final Metrics.Factory metricsFactory;

    private DatabaseProvider databaseProvider;
    private MessageService messageService;
    private TemplateRegistry templateRegistry;
    private SyncService syncService;
    private ImportService importService;
    private LocaleService localeService;
    private PunishmentService punishmentService;

    @Inject
    public EasyBans(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory, Metrics.Factory metricsFactory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.metricsFactory = metricsFactory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        metricsFactory.make(this, BSTATS_PLUGIN_ID);

        EasyBansConfig config = ConfigLoader.load(dataDirectory, logger);

        databaseProvider = new DatabaseProvider(config.storage(), dataDirectory, logger);
        try {
            new MigrationRunner(databaseProvider, logger).run();
        } catch (SQLException e) {
            throw new IllegalStateException("Database migration failed - refusing to start", e);
        }

        PlayerDao playerDao = new PlayerDao(databaseProvider);
        PunishmentDao punishmentDao = new PunishmentDao(databaseProvider);
        IpExemptionDao ipExemptionDao = new IpExemptionDao(databaseProvider);
        IpHistoryDao ipHistoryDao = new IpHistoryDao(databaseProvider);
        SyncEventDao syncEventDao = new SyncEventDao(databaseProvider);

        OfflinePlayerCache offlinePlayerCache = new OfflinePlayerCache(config.cache().offlinePlayerCacheSize());
        ActiveMuteCache activeMuteCache = new ActiveMuteCache();
        UuidResolver uuidResolver = new UuidResolver(proxy, offlinePlayerCache, playerDao, databaseProvider.executor(), logger);

        templateRegistry = new TemplateRegistry(dataDirectory, logger);

        syncService = SyncServiceFactory.create(config.sync(), syncEventDao, logger);

        messageService = new MessageService(dataDirectory, config.locale(), logger);
        localeService = new LocaleService(playerDao, config.locale(), syncService);
        KickScreenRenderer kickScreenRenderer = new KickScreenRenderer(messageService);
        DiscordWebhookService discord = new DiscordWebhookService(config.discord(), databaseProvider.executor(), logger);

        MuteNetworkSync muteNetworkSync = new MuteNetworkSync(proxy, this, logger);
        punishmentService = new PunishmentService(proxy, punishmentDao, templateRegistry, syncService, discord,
                kickScreenRenderer, localeService, config.general(), activeMuteCache, muteNetworkSync, logger);
        HistoryService historyService = new HistoryService(punishmentDao);
        RollbackService rollbackService = new RollbackService(punishmentDao);
        WarningTriggerService warningTriggerService = new WarningTriggerService(proxy, templateRegistry, punishmentDao, logger);
        ScopeResolver scopeResolver = new ScopeResolver(proxy, config.general().defaultScope());
        LinkedAccountService linkedAccountService = new LinkedAccountService(proxy, ipHistoryDao, punishmentDao,
                playerDao, punishmentService, config.linkedAccounts(), messageService, localeService, logger);
        importService = new ImportService(punishmentDao, uuidResolver);
        PunishmentBroadcaster broadcaster = new PunishmentBroadcaster(proxy, messageService, localeService, config.general());

        syncService.start();
        syncService.addListener((type, payload) -> onSyncEvent(type, payload, punishmentService));

        registerListeners(punishmentDao, ipExemptionDao, ipHistoryDao, playerDao, linkedAccountService,
                offlinePlayerCache, activeMuteCache, messageService);
        registerCommands(uuidResolver, offlinePlayerCache, punishmentService, historyService, rollbackService,
                warningTriggerService, scopeResolver, broadcaster, ipExemptionDao, playerDao);

        logger.info("EasyBans enabled ({} storage, {} sync)", config.storage().type(), config.sync().mode());
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (syncService != null) {
            syncService.stop();
        }
        if (importService != null) {
            importService.shutdown();
        }
        if (databaseProvider != null) {
            databaseProvider.close();
        }
    }

    private void onSyncEvent(SyncEventType type, String payload, PunishmentService punishmentService) {
        try {
            UUID uuid = UUID.fromString(payload);
            switch (type) {
                case PUNISHMENT_CHANGED -> proxy.getPlayer(uuid).ifPresent(player -> punishmentService.refreshOnlineStatus(
                        uuid, player.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse(null)));
                case LOCALE_CHANGED -> localeService.applyRemoteChange(uuid);
            }
        } catch (IllegalArgumentException e) {
            logger.warn("Received malformed sync event payload: {}", payload);
        }
    }

    private void registerListeners(PunishmentDao punishmentDao, IpExemptionDao ipExemptionDao, IpHistoryDao ipHistoryDao,
                                    PlayerDao playerDao, LinkedAccountService linkedAccountService,
                                    OfflinePlayerCache offlinePlayerCache, ActiveMuteCache activeMuteCache,
                                    MessageService messages) {
        ConnectionListener connectionListener = new ConnectionListener(punishmentService, punishmentDao, ipExemptionDao,
                ipHistoryDao, playerDao, localeService, linkedAccountService, messages, offlinePlayerCache,
                activeMuteCache, logger);
        proxy.getEventManager().register(this, connectionListener);

        ChatMuteListener chatMuteListener = new ChatMuteListener(activeMuteCache, messages, localeService);
        proxy.getEventManager().register(this, chatMuteListener);

        // /staffmode bridge (Tiroir-Survival, Paper backend) - registration is required for the proxy
        // to receive plugin messages sent FROM a backend at all, see StaffBridgeProtocol's javadoc.
        proxy.getChannelRegistrar().register(StaffBridgeProtocol.ACTION_CHANNEL);
        proxy.getChannelRegistrar().register(StaffBridgeProtocol.QUERY_CHANNEL);
        proxy.getEventManager().register(this, new StaffBridgeListener(proxy, templateRegistry, logger));
    }

    private void registerCommands(UuidResolver uuidResolver, OfflinePlayerCache offlinePlayerCache,
                                   PunishmentService punishmentService, HistoryService historyService,
                                   RollbackService rollbackService, WarningTriggerService warningTriggerService,
                                   ScopeResolver scopeResolver, PunishmentBroadcaster broadcaster,
                                   IpExemptionDao ipExemptionDao, PlayerDao playerDao) {
        var commandManager = proxy.getCommandManager();

        commandManager.register(commandManager.metaBuilder("easybans").build(),
                new EasyBansCommand(proxy, messageService, localeService, uuidResolver, offlinePlayerCache,
                        ipExemptionDao, rollbackService, importService, templateRegistry, this::reload, pluginVersion()));

        // Registering the exact vanilla names too (ban-ip, pardon, pardon-ip, banlist) makes Velocity intercept them
        // here instead of forwarding to the backend Paper/Spigot/Bukkit server's own vanilla ban system.
        commandManager.register(commandManager.metaBuilder("ban").build(),
                new BanCommand(proxy, messageService, localeService, uuidResolver, offlinePlayerCache,
                        punishmentService, scopeResolver, broadcaster, templateRegistry, playerDao, false));
        commandManager.register(commandManager.metaBuilder("banip").aliases("ban-ip").build(),
                new BanCommand(proxy, messageService, localeService, uuidResolver, offlinePlayerCache,
                        punishmentService, scopeResolver, broadcaster, templateRegistry, playerDao, true));
        commandManager.register(commandManager.metaBuilder("unban").aliases("pardon").build(),
                new UnbanCommand(proxy, messageService, localeService, uuidResolver, offlinePlayerCache, punishmentService));
        commandManager.register(commandManager.metaBuilder("pardon-ip").build(),
                new PardonIpCommand(proxy, messageService, localeService, uuidResolver, offlinePlayerCache, punishmentService));
        commandManager.register(commandManager.metaBuilder("banlist").build(),
                new BanListCommand(proxy, messageService, localeService, uuidResolver, offlinePlayerCache, punishmentService));

        commandManager.register(commandManager.metaBuilder("mute").build(),
                new MuteCommand(proxy, messageService, localeService, uuidResolver, offlinePlayerCache,
                        punishmentService, scopeResolver, broadcaster, templateRegistry));
        commandManager.register(commandManager.metaBuilder("unmute").build(),
                new UnmuteCommand(proxy, messageService, localeService, uuidResolver, offlinePlayerCache, punishmentService));

        commandManager.register(commandManager.metaBuilder("warn").build(),
                new WarnCommand(proxy, messageService, localeService, uuidResolver, offlinePlayerCache,
                        punishmentService, warningTriggerService, templateRegistry, scopeResolver, broadcaster));
        commandManager.register(commandManager.metaBuilder("kick").build(),
                new KickCommand(proxy, messageService, localeService, uuidResolver, offlinePlayerCache,
                        punishmentService, scopeResolver, broadcaster));
        commandManager.register(commandManager.metaBuilder("note").build(),
                new NoteCommand(proxy, messageService, localeService, uuidResolver, offlinePlayerCache, punishmentService));

        commandManager.register(commandManager.metaBuilder("history").build(),
                new HistoryCommand(proxy, messageService, localeService, uuidResolver, offlinePlayerCache, historyService));
        commandManager.register(commandManager.metaBuilder("staffhistory").build(),
                new StaffHistoryCommand(proxy, messageService, localeService, uuidResolver, offlinePlayerCache, historyService));
        commandManager.register(commandManager.metaBuilder("lookup").build(),
                new LookupCommand(proxy, messageService, localeService, uuidResolver, offlinePlayerCache, punishmentService));
    }

    private String pluginVersion() {
        return proxy.getPluginManager().getPlugin("easybans")
                .flatMap(p -> p.getDescription().getVersion())
                .orElse("unknown");
    }

    private void reload() {
        messageService.reload();
        templateRegistry.reload();
        logger.info("EasyBans messages and templates reloaded. Storage/Discord/sync settings require a restart to change.");
    }
}
