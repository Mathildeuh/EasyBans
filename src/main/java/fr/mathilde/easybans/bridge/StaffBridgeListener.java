package fr.mathilde.easybans.bridge;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.mathilde.easybans.punishment.PunishmentType;
import fr.mathilde.easybans.template.TemplateRegistry;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Consumer side of {@link StaffBridgeProtocol}. Every action is turned back into the exact command
 * line a staff member would have typed (see each {@code case} below, cross-checked against
 * {@code command/WarnCommand}, {@code MuteCommand}, {@code BanCommand}, {@code KickCommand},
 * {@code NoteCommand} and {@code HistoryCommand}'s own argument parsing) and handed to
 * {@link com.velocitypowered.api.command.CommandManager#executeAsync} - so permission checks,
 * {@code -t:}/{@code -s} flags, broadcasting, Discord notifications and localized feedback all stay
 * defined in exactly one place. If the staff member isn't connected to the proxy anymore by the time
 * this arrives, the action is silently dropped (nothing sane to attribute it to).
 */
public final class StaffBridgeListener {

    private final ProxyServer proxy;
    private final TemplateRegistry templateRegistry;
    private final Logger logger;

    public StaffBridgeListener(ProxyServer proxy, TemplateRegistry templateRegistry, Logger logger) {
        this.proxy = proxy;
        this.templateRegistry = templateRegistry;
        this.logger = logger;
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (event.getIdentifier().equals(StaffBridgeProtocol.ACTION_CHANNEL)) {
            event.setResult(PluginMessageEvent.ForwardResult.handled());
            handleAction(event.getData());
        } else if (event.getIdentifier().equals(StaffBridgeProtocol.QUERY_CHANNEL)) {
            event.setResult(PluginMessageEvent.ForwardResult.handled());
            handleQuery(event.getData());
        }
    }

    private void handleAction(byte[] data) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            StaffBridgeProtocol.Action action = StaffBridgeProtocol.Action.values()[in.readByte()];
            UUID staffUuid = UUID.fromString(in.readUTF());
            String targetName = in.readUTF();

            Optional<Player> staff = proxy.getPlayer(staffUuid);
            if (staff.isEmpty()) {
                return;
            }

            List<String> parts = new ArrayList<>();
            switch (action) {
                case WARN -> {
                    parts.add("warn");
                    parts.add(targetName);
                    parts.add(in.readUTF()); // category
                    String reason = in.readUTF();
                    if (in.readBoolean()) {
                        parts.add("-s");
                    }
                    parts.add(reason);
                }
                case MUTE_TEMPLATE -> {
                    parts.add("mute");
                    parts.add(targetName);
                    parts.add("-t:" + in.readUTF());
                    if (in.readBoolean()) {
                        parts.add("-s");
                    }
                }
                case MUTE_DURATION -> {
                    parts.add("mute");
                    parts.add(targetName);
                    String duration = in.readUTF();
                    String reason = in.readUTF();
                    if (in.readBoolean()) {
                        parts.add("-s");
                    }
                    parts.add(duration);
                    parts.add(reason);
                }
                case BAN_TEMPLATE -> {
                    parts.add("ban");
                    parts.add(targetName);
                    parts.add("-t:" + in.readUTF());
                    if (in.readBoolean()) {
                        parts.add("-s");
                    }
                }
                case BAN_DURATION -> {
                    parts.add("ban");
                    parts.add(targetName);
                    String duration = in.readUTF();
                    String reason = in.readUTF();
                    if (in.readBoolean()) {
                        parts.add("-s");
                    }
                    parts.add(duration);
                    parts.add(reason);
                }
                case KICK -> {
                    parts.add("kick");
                    parts.add(targetName);
                    String reason = in.readUTF();
                    if (in.readBoolean()) {
                        parts.add("-s");
                    }
                    parts.add(reason);
                }
                case NOTE -> {
                    parts.add("note");
                    parts.add(targetName);
                    parts.add(in.readUTF()); // reason - NoteCommand has no flag parsing at all
                }
                case HISTORY -> {
                    parts.add("history");
                    parts.add(targetName);
                    parts.add(String.valueOf(in.readInt() + 1)); // wire page is 0-based, the command is 1-based
                }
            }
            proxy.getCommandManager().executeAsync(staff.get(), String.join(" ", parts));
        } catch (IOException | IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            logger.warn("[StaffBridge] Malformed action payload received", e);
        }
    }

    private void handleQuery(byte[] data) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            String requestId = in.readUTF();
            StaffBridgeProtocol.QueryType type = StaffBridgeProtocol.QueryType.values()[in.readByte()];
            UUID staffUuid = UUID.fromString(in.readUTF());

            List<String[]> entries = switch (type) {
                case LIST_MUTE_TEMPLATES -> templatesOfType(PunishmentType.MUTE);
                case LIST_BAN_TEMPLATES -> templatesOfType(PunishmentType.BAN);
                case LIST_CATEGORIES -> categories();
            };
            sendQueryResult(staffUuid, requestId, entries);
        } catch (IOException | IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            logger.warn("[StaffBridge] Malformed query payload received", e);
        }
    }

    /** {@code {id, id}} pairs - templates have no separate display name, unlike warning categories. */
    private List<String[]> templatesOfType(PunishmentType type) {
        List<String[]> result = new ArrayList<>();
        for (String id : templateRegistry.templateIds()) {
            templateRegistry.template(id)
                    .filter(t -> t.type() == type)
                    .ifPresent(t -> result.add(new String[] {t.id(), t.id()}));
        }
        return result;
    }

    private List<String[]> categories() {
        List<String[]> result = new ArrayList<>();
        for (String id : templateRegistry.categoryIds()) {
            templateRegistry.category(id).ifPresent(c -> result.add(new String[] {c.id(), c.displayName()}));
        }
        return result;
    }

    private void sendQueryResult(UUID staffUuid, String requestId, List<String[]> entries) {
        proxy.getPlayer(staffUuid).flatMap(Player::getCurrentServer).ifPresent(connection ->
                connection.sendPluginMessage(StaffBridgeProtocol.QUERY_RESULT_CHANNEL, output -> {
                    output.writeUTF(requestId);
                    output.writeInt(entries.size());
                    for (String[] entry : entries) {
                        output.writeUTF(entry[0]);
                        output.writeUTF(entry[1]);
                    }
                }));
    }
}
