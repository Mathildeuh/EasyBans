package fr.mathilde.easybans.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import fr.mathilde.easybans.cache.ActiveMuteCache;
import fr.mathilde.easybans.locale.LocaleService;
import fr.mathilde.easybans.message.MessageService;
import fr.mathilde.easybans.message.PunishmentFormatter;
import fr.mathilde.easybans.punishment.Mute;
import net.kyori.adventure.text.Component;

import java.util.Optional;

/**
 * Enforces mutes at chat time using {@link ActiveMuteCache} (never a database query per
 * message - see the cache's javadoc). Mute enforcement only works for chat routed through the
 * proxy (Velocity's {@code PlayerChatEvent}); it cannot see chat handled entirely backend-side
 * by a server that doesn't forward it - see ARCHITECTURE.md.
 *
 * <p><b>Requires SignedVelocity on 1.19.1+ clients.</b> Since chat signing was introduced,
 * cancelling {@code PlayerChatEvent} on the proxy without also informing the backend server
 * desyncs the client's signed-chat chain, which can produce "unsecure chat" warnings or, on
 * servers enforcing secure profiles, disconnect the player entirely. Installing
 * <a href="https://github.com/4drian3d/SignedVelocity">SignedVelocity</a> on the proxy AND on
 * every backend server fixes this - it requires no API integration from EasyBans itself, it
 * just needs to be present. See README.md for the installation note.
 */
public final class ChatMuteListener {

    private final ActiveMuteCache activeMuteCache;
    private final MessageService messages;
    private final LocaleService localeService;

    public ChatMuteListener(ActiveMuteCache activeMuteCache, MessageService messages, LocaleService localeService) {
        this.activeMuteCache = activeMuteCache;
        this.messages = messages;
        this.localeService = localeService;
    }

    @Subscribe
    @SuppressWarnings("deprecation") // setResult(denied) is deprecated by Velocity precisely because of the
    // 1.19.1+ signed-chat interaction documented above - SignedVelocity is the documented fix, not a code change.
    public void onChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        Optional<Mute> mute = activeMuteCache.get(player.getUniqueId());
        if (mute.isEmpty()) {
            return;
        }
        event.setResult(PlayerChatEvent.ChatResult.denied());
        var locale = localeService.getCached(player.getUniqueId());
        Component notice = messages.get(locale, "commands.mute.notice",
                PunishmentFormatter.of(mute.get(), null, messages, locale).build());
        player.sendMessage(notice);
    }
}
