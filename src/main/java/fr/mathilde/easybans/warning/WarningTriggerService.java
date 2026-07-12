package fr.mathilde.easybans.warning;

import com.velocitypowered.api.proxy.ProxyServer;
import fr.mathilde.easybans.database.dao.PunishmentDao;
import fr.mathilde.easybans.template.TemplateRegistry;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * After a warning is recorded, checks whether the player just reached a configured threshold
 * for that category and, if so, runs the configured console commands (e.g. auto-mute after 3
 * "spam" warnings).
 */
public final class WarningTriggerService {

    private final ProxyServer proxy;
    private final TemplateRegistry registry;
    private final PunishmentDao punishmentDao;
    private final Logger logger;

    public WarningTriggerService(ProxyServer proxy, TemplateRegistry registry, PunishmentDao punishmentDao, Logger logger) {
        this.proxy = proxy;
        this.registry = registry;
        this.punishmentDao = punishmentDao;
        this.logger = logger;
    }

    public void onWarningIssued(UUID target, String targetName, String category) {
        registry.category(category).ifPresent(warningCategory ->
                punishmentDao.countByCategory(target, category).thenAccept(total -> {
                    for (WarningThreshold threshold : warningCategory.thresholdsMatching(total)) {
                        for (String command : threshold.commands()) {
                            String resolved = command.replace("%player%", targetName);
                            logger.info("Warning threshold reached: executing '{}' for {}", resolved, targetName);
                            proxy.getCommandManager().executeAsync(proxy.getConsoleCommandSource(), resolved);
                        }
                    }
                }).exceptionally(e -> {
                    logger.warn("Failed to evaluate warning thresholds for category '{}'", category, e);
                    return null;
                }));
    }
}
