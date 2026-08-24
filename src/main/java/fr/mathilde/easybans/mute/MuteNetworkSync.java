package fr.mathilde.easybans.mute;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.ChannelMessageSink;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import fr.mathilde.easybans.punishment.Mute;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.Node;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Bridges mute state to backends that can't safely enforce chat mutes at the proxy layer: cancelling
 * Velocity's {@code PlayerChatEvent} without a companion plugin (SignedVelocity) installed and
 * correctly configured on every single server desyncs the 1.19.1+ signed-chat chain for premium
 * players, and SignedVelocity's own optional "remove unsecure chat warning" feature (via
 * PacketEvents/VPacketEvents) unconditionally forces {@code enforcesSecureChat} on the client's
 * JoinGame packet, which breaks chat entirely for cracked players who have no signing key at all.
 *
 * <p>Instead: a network-wide LuckPerms permission node ({@link #MUTED_PERMISSION}, with LuckPerms'
 * own expiry for temporary mutes - no polling needed on our side) is the source of truth a backend
 * can check with a plain {@code Player#hasPermission()}, enforced entirely backend-side (e.g. Paper's
 * own {@code AsyncChatEvent}), which never round-trips back through the proxy and therefore never
 * touches the signed-chat chain at all. A plugin message carries the human-readable reason/expiry
 * for display, since a bare permission can't carry that - see {@code TiroirSurvival}'s
 * {@code MutePluginMessageListener} for the consumer side.
 */
public final class MuteNetworkSync {

    public static final String MUTED_PERMISSION = "easybans.muted";
    public static final ChannelIdentifier MUTE_CHANNEL = MinecraftChannelIdentifier.create("easybans", "mute");

    private static final Predicate<Node> IS_MUTE_NODE = node -> node.getKey().equalsIgnoreCase(MUTED_PERMISSION);

    // sendPluginMessage can return false right around ServerConnectedEvent - it's an @AwaitingEvent
    // Velocity fires (and blocks the connection setup on) before the backend connection is
    // necessarily ready to relay plugin messages yet. A handful of short retries covers that race
    // without a fixed sleep; if it's still failing after this many attempts, something is actually
    // wrong (backend down, channel not registered) and is worth a log line instead of a silent drop.
    private static final int MAX_SEND_ATTEMPTS = 5;
    private static final Duration RETRY_DELAY = Duration.ofMillis(250);

    private final ProxyServer proxy;
    private final Object plugin;
    private final Logger logger;

    public MuteNetworkSync(ProxyServer proxy, Object plugin, Logger logger) {
        this.proxy = proxy;
        this.plugin = plugin;
        this.logger = logger;
    }

    /** Grants (or refreshes, on re-mute) the network-wide permission node and notifies the backend. */
    public void applyMute(Mute mute) {
        if (!luckPermsAvailable()) {
            return;
        }

        Node node = Node.builder(MUTED_PERMISSION)
                .expiry(mute.expiresAt().orElse(null))
                .build();

        luckPerms().getUserManager().modifyUser(mute.targetUuid(), user -> {
            user.data().clear(IS_MUTE_NODE);
            user.data().add(node);
        }).exceptionally(error -> {
            logger.warn("Impossible de synchroniser le mute LuckPerms pour {}", mute.targetUuid(), error);
            return null;
        });

        sendMuteInfo(mute.targetUuid(), Optional.of(mute));
    }

    /** Revokes the network-wide permission node and notifies the backend. */
    public void clearMute(UUID target) {
        if (luckPermsAvailable()) {
            luckPerms().getUserManager().modifyUser(target, user -> user.data().clear(IS_MUTE_NODE))
                    .exceptionally(error -> {
                        logger.warn("Impossible de retirer le mute LuckPerms pour {}", target, error);
                        return null;
                    });
        }

        sendMuteInfo(target, Optional.empty());
    }

    /**
     * Re-emits the current mute state (active or not) to whichever backend the player is currently
     * connected to - called right after {@code /mute}/{@code /unmute}. The player has been
     * connected for a while at that point, so {@code Player#getCurrentServer()} is reliable here -
     * unlike right after {@code ServerConnectedEvent}, see the {@link #sendMuteInfo(UUID, Optional,
     * RegisteredServer)} overload used there instead.
     */
    public void sendMuteInfo(UUID target, Optional<Mute> mute) {
        proxy.getPlayer(target).flatMap(Player::getCurrentServer).ifPresent(connection ->
                sendMuteInfo(target, mute, connection, connection.getServerInfo().getName(), 1));
    }

    /**
     * Re-emits the mute state to a specific, already-known backend - used by {@code
     * ConnectionListener#onServerConnected}, which has {@code event.getServer()} in hand directly.
     * Deliberately does NOT go through {@code Player#getCurrentServer()} the way the other overload
     * does: on at least one Velocity fork in the wild (Velocity-CTD), that accessor is observed to
     * still be empty while {@code ServerConnectedEvent}'s own subscribers are running, even though
     * the event itself already carries the correct {@code RegisteredServer} - relying on it here
     * made every single post-reconnect resync silently no-op (logged as "player has no current
     * server yet"), which is exactly what left reconnected players unmuted backend-side despite the
     * LuckPerms permission still being intact. {@code RegisteredServer} implements {@code
     * ChannelMessageSink} directly, so there's no need to re-derive anything from the player at all.
     */
    public void sendMuteInfo(UUID target, Optional<Mute> mute, RegisteredServer server) {
        sendMuteInfo(target, mute, server, server.getServerInfo().getName(), 1);
    }

    /**
     * {@code ChannelMessageSink#sendPluginMessage} returns {@code false} rather than throwing when
     * the message couldn't be relayed. Silently ignoring that return value (as this used to) meant a
     * mute could stop being enforced backend-side for the rest of the session the moment a send was
     * lost, since nothing else re-syncs {@code MuteSyncCache} afterwards - only another
     * disconnect/reconnect would self-heal it. Retrying a few times a short delay apart, against the
     * SAME sink (it's a stable server-level object either way, not something that goes stale like a
     * player's current-server snapshot can), absorbs a transient failure instead of dropping the
     * resync on the floor.
     */
    private void sendMuteInfo(UUID target, Optional<Mute> mute, ChannelMessageSink sink, String serverName, int attempt) {
        boolean sent = sink.sendPluginMessage(MUTE_CHANNEL, output -> {
            output.writeUTF(target.toString());
            boolean muted = mute.isPresent();
            output.writeBoolean(muted);
            if (muted) {
                output.writeUTF(mute.get().reason());
                boolean hasExpiry = mute.get().expiresAt().isPresent();
                output.writeBoolean(hasExpiry);
                if (hasExpiry) {
                    output.writeLong(mute.get().expiresAt().get().toEpochMilli());
                }
            }
        });
        if (sent) {
            return;
        }
        if (attempt >= MAX_SEND_ATTEMPTS) {
            logger.warn("Impossible d'envoyer l'état du mute pour {} au backend \"{}\" après {} tentatives - "
                            + "le chat de ce joueur ne sera pas bloqué backend-side tant qu'il ne se reconnecte pas",
                    target, serverName, attempt);
            return;
        }
        proxy.getScheduler().buildTask(plugin, () -> sendMuteInfo(target, mute, sink, serverName, attempt + 1))
                .delay(RETRY_DELAY).schedule();
    }

    private boolean luckPermsAvailable() {
        try {
            LuckPermsProvider.get();
            return true;
        } catch (IllegalStateException e) {
            logger.warn("LuckPerms n'est pas chargé - le mute \"{}\" ne sera pas synchronisé réseau (permission node)", MUTED_PERMISSION);
            return false;
        }
    }

    private LuckPerms luckPerms() {
        return LuckPermsProvider.get();
    }
}
