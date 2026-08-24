package fr.mathilde.easybans.listener;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.proxy.Player;
import fr.mathilde.easybans.cache.ActiveMuteCache;
import fr.mathilde.easybans.locale.LocaleService;
import fr.mathilde.easybans.message.MessageService;
import fr.mathilde.easybans.message.PunishmentFormatter;
import fr.mathilde.easybans.punishment.Mute;
import net.kyori.adventure.text.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Restricts a muted player's use of chat-adjacent bypass commands ({@code /msg}, {@code /tell},
 * {@code /w}, {@code /me}). Actual chat blocking no longer happens here: cancelling Velocity's
 * {@code PlayerChatEvent} without a companion plugin (SignedVelocity) desyncs the 1.19.1+
 * signed-chat chain, and SignedVelocity's own "remove unsecure chat warning" feature breaks chat
 * entirely for cracked players (see ARCHITECTURE.md). Chat is now blocked backend-side instead,
 * via the network-wide LuckPerms permission node - see {@link fr.mathilde.easybans.mute.MuteNetworkSync}
 * and {@code TiroirSurvival}'s {@code ChatFilterService}. This listener used to also deny
 * {@code PlayerChatEvent} and send the same notice, which duplicated the message the backend now
 * sends on its own - removed for that reason, not just as a cleanup.
 */
public final class ChatMuteListener {

    private static final Set<String> MUTE_BYPASS_ROOT_COMMANDS = Set.of("msg", "tell", "w", "me");

    private final ActiveMuteCache activeMuteCache;
    private final MessageService messages;
    private final LocaleService localeService;

    public ChatMuteListener(ActiveMuteCache activeMuteCache, MessageService messages, LocaleService localeService) {
        this.activeMuteCache = activeMuteCache;
        this.messages = messages;
        this.localeService = localeService;
    }

    @Subscribe(order = PostOrder.LAST)
    public void onCommandExecute(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player player)) {
            return;
        }
        Optional<Mute> mute = activeMuteCache.get(player.getUniqueId());
        if (mute.isEmpty()) {
            return;
        }
        if (!MUTE_BYPASS_ROOT_COMMANDS.contains(rootCommand(event.getCommand()))) {
            return;
        }
        event.setResult(CommandExecuteEvent.CommandResult.denied());
        sendMuteNotice(player, mute.get());
    }

    private void sendMuteNotice(Player player, Mute mute) {
        var locale = localeService.getCached(player.getUniqueId());
        Component notice = messages.get(locale, "commands.mute.notice",
                PunishmentFormatter.of(mute, null, messages, locale).build());
        player.sendMessage(notice);
    }

    private static String rootCommand(String commandLine) {
        if (commandLine == null || commandLine.isBlank()) {
            return "";
        }
        String trimmed = commandLine.stripLeading();
        int space = trimmed.indexOf(' ');
        String root = space < 0 ? trimmed : trimmed.substring(0, space);
        return root.toLowerCase(Locale.ROOT);
    }
}
