package fr.mathilde.easybans.importer;

public enum ImportSource {
    /** Mojang's own banned-players.json / banned-ips.json. */
    VANILLA,
    /** EssentialsX userdata (banned/muted flags only - no expiry tracking in the source format). */
    ESSENTIALS,
    /** Legacy MaxBans MySQL schema. */
    MAXBANS,
    /** BanManager MySQL schema. */
    BANMANAGER,
    /** AdvancedBan MySQL schema. */
    ADVANCEDBAN,
    /** BungeeAdminTools YAML ban lists. */
    BUNGEEADMINTOOLS,
    /** LiteBans MySQL/MariaDB/H2/PostgreSQL schema. */
    LITEBANS
}
