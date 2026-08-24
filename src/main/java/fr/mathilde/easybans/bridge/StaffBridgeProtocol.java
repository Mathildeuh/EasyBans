package fr.mathilde.easybans.bridge;

import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;

/**
 * Wire contract for the {@code /staffmode} bridge exposed to Tiroir-Survival (Paper backend) - lets
 * its in-game moderation menu drive the real punishment pipeline (permission checks, templates,
 * broadcast, Discord, history) without duplicating any of it. Mirrors {@code mute/MuteNetworkSync}'s
 * approach: plain plugin-message channels, constants duplicated verbatim on both sides
 * ({@code fr.mathilde.tiroirSurvival.staffbridge.StaffBridgeProtocol}) - a contract between two
 * separate plugins (this one on Velocity, Tiroir-Survival on Paper), not a code dependency.
 *
 * <p>Deliberately built as "reconstruct the exact command line a staff member would have typed, then
 * run it through {@code CommandManager#executeAsync}" (see {@link StaffBridgeListener}) rather than
 * re-implementing permission checks / {@code PunishmentService} orchestration here - the same
 * pattern {@code warning.WarningTriggerService} already uses for its auto-triggered commands. This
 * means every check (permission, exempt, override), every user-facing message, and every broadcast
 * stays defined in exactly one place (the real command classes).
 */
public final class StaffBridgeProtocol {

    /** Paper -> Velocity, fire-and-forget: run a punishment action as a given staff member. */
    public static final ChannelIdentifier ACTION_CHANNEL = MinecraftChannelIdentifier.create("easybans", "staffbridge_action");
    /** Paper -> Velocity: ask for a small picklist (templates/categories) needed to render the menu. */
    public static final ChannelIdentifier QUERY_CHANNEL = MinecraftChannelIdentifier.create("easybans", "staffbridge_query");
    /** Velocity -> Paper: answer to {@link #QUERY_CHANNEL}, correlated by the request's {@code requestId}. */
    public static final ChannelIdentifier QUERY_RESULT_CHANNEL = MinecraftChannelIdentifier.create("easybans", "staffbridge_query_result");

    /** Ordinal written as the first byte of every {@link #ACTION_CHANNEL} payload. */
    public enum Action {
        WARN,
        MUTE_TEMPLATE,
        MUTE_DURATION,
        BAN_TEMPLATE,
        BAN_DURATION,
        KICK,
        NOTE,
        /** Dumps a page of {@code /history <target> <page>} straight into the staff's chat. */
        HISTORY
    }

    /** Ordinal written as the second byte of every {@link #QUERY_CHANNEL} payload (after the requestId). */
    public enum QueryType {
        LIST_MUTE_TEMPLATES,
        LIST_BAN_TEMPLATES,
        LIST_CATEGORIES
    }

    private StaffBridgeProtocol() {
    }
}
