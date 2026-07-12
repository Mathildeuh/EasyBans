package fr.mathilde.easybans.command;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.mathilde.easybans.cache.OfflinePlayerCache;
import fr.mathilde.easybans.punishment.PunishmentType;
import fr.mathilde.easybans.template.TemplateRegistry;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Tab-completion helpers shared by every command: online + cached-offline player names, server names, flags. */
public final class TabCompleteUtil {

    private static final int MAX_SUGGESTIONS = 50;
    private static final List<String> COMMON_DURATIONS = List.of("10m", "1h", "6h", "12h", "1d", "3d", "7d", "14d", "30d", "permanent");

    private TabCompleteUtil() {
    }

    /** Online players first (most likely target), then cached offline names, prefix-matched and de-duplicated. */
    public static List<String> players(ProxyServer proxy, OfflinePlayerCache cache, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        Set<String> names = new LinkedHashSet<>();
        for (Player player : proxy.getAllPlayers()) {
            if (player.getUsername().toLowerCase(Locale.ROOT).startsWith(lower)) {
                names.add(player.getUsername());
            }
        }
        for (String offline : cache.suggestNames(prefix, MAX_SUGGESTIONS)) {
            names.add(offline);
            if (names.size() >= MAX_SUGGESTIONS) {
                break;
            }
        }
        return List.copyOf(names);
    }

    /** Online players only - used by commands that require the target to currently be connected (e.g. /kick). */
    public static List<String> onlinePlayers(ProxyServer proxy, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return proxy.getAllPlayers().stream()
                .map(Player::getUsername)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(lower))
                .toList();
    }

    public static List<String> servers(ProxyServer proxy, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return proxy.getAllServers().stream()
                .map(server -> server.getServerInfo().getName())
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(lower))
                .toList();
    }

    public static List<String> durations(String prefix) {
        return COMMON_DURATIONS.stream().filter(d -> d.startsWith(prefix.toLowerCase(Locale.ROOT))).toList();
    }

    public static List<String> templates(TemplateRegistry registry, PunishmentType type, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return registry.templateIds().stream()
                .filter(id -> registry.template(id).map(t -> t.type() == type).orElse(false))
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(lower))
                .toList();
    }

    /**
     * Suggestions for the {@code -s} / {@code -server:<name>} / {@code -t:<templateId>} flags,
     * based on whatever the currently-typed token looks like.
     *
     * @param includeTemplate whether {@code -t:} applies to this command (ban/mute only)
     */
    public static List<String> flags(String currentToken, ProxyServer proxy, TemplateRegistry registry,
                                      PunishmentType type, boolean includeTemplate) {
        if (currentToken.regionMatches(true, 0, "-server:", 0, Math.min(currentToken.length(), "-server:".length()))
                && currentToken.length() >= "-server:".length()) {
            String serverPrefix = currentToken.substring("-server:".length());
            return servers(proxy, serverPrefix).stream().map(name -> "-server:" + name).toList();
        }
        if (includeTemplate && currentToken.regionMatches(true, 0, "-t:", 0, Math.min(currentToken.length(), "-t:".length()))
                && currentToken.length() >= "-t:".length()) {
            String templatePrefix = currentToken.substring("-t:".length());
            return templates(registry, type, templatePrefix).stream().map(id -> "-t:" + id).toList();
        }

        List<String> options = new ArrayList<>(List.of("-s", "-server:"));
        if (includeTemplate) {
            options.add("-t:");
        }
        String lower = currentToken.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.startsWith(lower)).toList();
    }
}
