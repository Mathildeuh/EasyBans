package fr.mathilde.easybans.command;

/** Every permission node EasyBans checks. Full reference with descriptions: PERMISSIONS.md. */
public final class Permissions {

    public static final String BAN = "easybans.ban";
    public static final String BAN_IP = "easybans.banip";
    public static final String UNBAN = "easybans.unban";
    public static final String MUTE = "easybans.mute";
    public static final String UNMUTE = "easybans.unmute";
    public static final String WARN = "easybans.warn";
    public static final String KICK = "easybans.kick";
    public static final String NOTE = "easybans.note";
    public static final String HISTORY = "easybans.history";
    public static final String STAFF_HISTORY = "easybans.staffhistory";
    /** {@code /banlist} - vanilla's own ban-listing command, replaced by EasyBans. */
    public static final String BANLIST = "easybans.banlist";
    public static final String ROLLBACK = "easybans.rollback";
    public static final String IMPORT = "easybans.import";
    public static final String ALLOW = "easybans.allow";
    public static final String RELOAD = "easybans.reload";
    public static final String TEMPLATE = "easybans.template";

    /** Bypasses anti-overwrite: allowed to replace an already-active ban/mute on a target. */
    public static final String OVERRIDE = "easybans.override";
    /** Player cannot be punished at all (protects staff from accidental self-punishment mistakes). */
    public static final String EXEMPT = "easybans.exempt";
    /** Notified when a player connects sharing an IP with a banned account. */
    public static final String NOTIFY_LINKED = "easybans.notify.linked";
    /** Sees the broadcast message every punishment sends (unless issued with -s / silent). */
    public static final String NOTIFY_BROADCAST = "easybans.notify.broadcast";
    /** Can silence a punishment's broadcast with the -s flag. */
    public static final String SILENT = "easybans.silent";

    private Permissions() {
    }
}
