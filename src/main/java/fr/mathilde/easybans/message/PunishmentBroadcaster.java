package fr.mathilde.easybans.message;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.mathilde.easybans.config.GeneralConfig;
import fr.mathilde.easybans.locale.LocaleService;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class PunishmentBroadcaster {

    private final ProxyServer proxy;
    private final MessageService messages;
    private final LocaleService localeService;
    private final GeneralConfig config;

    public PunishmentBroadcaster(ProxyServer proxy, MessageService messages, LocaleService localeService,
                                  GeneralConfig config) {
        this.proxy = proxy;
        this.messages = messages;
        this.localeService = localeService;
        this.config = config;
    }

    public void broadcast(String key, boolean silent, TagResolver... resolvers) {
        if (!config.broadcastPunishments() || silent) {
            return;
        }
        for (Player player : proxy.getAllPlayers()) {
            if (player.hasPermission(config.broadcastPermission())) {
                var locale = localeService.getCached(player.getUniqueId());
                player.sendMessage(messages.get(locale, key, resolvers));
            }
        }
    }
}
