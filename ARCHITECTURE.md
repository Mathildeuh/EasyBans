# ARCHITECTURE.md

Design decisions behind EasyBans, and the reasoning for each, so future changes don't
accidentally undo a deliberate trade-off.

## Single Gradle module, not core/velocity/api

A `core`/`velocity`/`api` split is worth its complexity when the same business logic needs to
run on multiple platforms (e.g. a Paper *and* a Velocity build sharing one core, the way
LiteBans does with its Bukkit + BungeeCord + Velocity components). EasyBans targets Velocity
only - there is no companion backend plugin - so a multi-module split would add Gradle
ceremony (inter-project dependencies, separate publishing) without a second consumer to justify
it. Package-level separation (`punishment`, `database`, `command`, ...) gives the same
readability benefit within one module. If a backend companion plugin (e.g. for enforcing mutes
at the chat-content level rather than just cancelling proxy-routed chat) is ever added, extracting
`punishment`/`database`/`message` into a `core` module at that point is straightforward, because
none of those packages import anything Velocity-specific except through constructor-injected
interfaces (`ProxyServer`, `Player`, ...).

## Database

### One `punishments` table, not five

Bans, mutes, warnings, kicks, and notes live in a single `easybans_punishments` table with a
`type` discriminator column, rather than one table per kind. `/history` and `/staffhistory`
need every punishment type sorted by time in one page - a single indexed query
(`WHERE target_uuid = ? ORDER BY created_at DESC LIMIT ? OFFSET ?`) does that; five separate
tables would need a `UNION ALL` plus a merge-sort in application code for the same result. The
cost is a handful of columns that are only meaningful for some types (`target_ip`/`ip_banned`
only apply to bans, `category` only to warnings) - acceptable for a moderation plugin's row
volume and schema simplicity.

### MariaDB Connector/J for both "mysql" and "mariadb"

The MariaDB JDBC driver speaks the MySQL wire protocol and works against real MySQL servers
too, so one driver (Apache-licensed) covers both `storage.type: mysql` and
`storage.type: mariadb` instead of bundling MySQL Connector/J (GPL, and a second driver to
relocate/shade for no functional gain). This is the same choice LiteBans and several other
Velocity plugins make.

### Migrations

A small hand-rolled versioned migration runner (`database/migration/`) rather than Flyway -
there is exactly one schema version at this point and the four supported dialects only differ
in two spots (auto-increment PK syntax, handled by `SchemaSupport`), so a dependency dedicated
to schema versioning would outweigh what it replaces. If the schema grows enough versions that
hand-written migrations become unwieldy, Flyway is a reasonable thing to introduce then.

### Template/warning progress: computed, not stored

Rather than a separate `template_progress` table, "how many times has this player triggered
template X" is a `COUNT(*)` over `punishments` filtered by `template_id`. One source of truth,
no risk of the counter and the actual rows drifting out of sync; the query is cheap since it's
indexed and only runs when a punishment is being created, not on a hot path.

## i18n / MiniMessage

### Placeholder injection via `TagResolver`, not string substitution

Dynamic values (`<reason>`, `<staff>`, ...) are resolved via Adventure's
`Placeholder.unparsed(name, value)` *before* MiniMessage deserializes the template string, not
by doing `template.replace("{REASON}", reason)` and then parsing the result. This matters
because punishment reasons are staff-typed free text: if it were substituted into the raw
string before parsing, a reason like `<rainbow>lol` would inject MiniMessage formatting (or
worse, a `<click:run_command:...>` tag) into a message rendered with elevated trust. Unparsed
placeholders are always literal text, never re-parsed.

### `{VAR}` in the spec vs `<var>` in the files

The functional spec describes placeholders as `{DURATION}`, `{REASON}`, etc. (the LiteBans
convention). Since MiniMessage is the mandated format and its tag syntax is `<tag>`, the actual
files use `<duration>`, `<reason>`, `<staff>`, `<server>`, `<time_since>`, `<original_date>`,
`<original_duration>`, `<id>` - the same variables, MiniMessage-native syntax. Documented in
CONFIG.md's placeholder table.

### Key-parity enforced by a compile-time-adjacent test, not at runtime

`MessageKeyParityTest` loads every bundled locale and asserts identical key sets against
English. Catching a missing/typo'd key in CI is strictly better than discovering it in
production when a player's language falls back silently for one specific message.

### Duration formatting is numeric, not localized words

`DurationFormatter` renders `3d 2h 15m` the same way in every language rather than pluralizing
"day(s)/jour(s)/Tag(e)/día(s)" per locale, which would need a full CLDR-style plural-rules
table for 7 languages. A compact numeric format reads unambiguously in every language and
keeps `DurationFormatter` a single, easily-unit-tested, locale-free utility.

## Punishment templates and kickscreens

Each `TemplateStage` may reference its own kickscreen key (`kickscreen: "cheating.final"` ->
`kickscreen.template.cheating.final`), so a ban's rendered screen is resolved from a
`kickscreen_key` column stored **at creation time** - not recomputed later by re-deriving
"which stage produced this" from the escalation counters. Storing it once, at the moment the
template decided the stage, is simpler and can't drift if the template config changes between
when the ban was issued and when the player is later shown the screen (e.g. after a
`/easybans reload`).

## Anti-overwrite (requirement 17)

A new ban/mute is rejected with `ALREADY_PUNISHED` if the target already has *any* currently
active punishment of that type, regardless of the new punishment's scope (global vs a specific
server) - checking only same-scope overlaps would let a staff member "silently upgrade" a
server-scoped mute to a global one without realizing an active punishment already existed.
`easybans.override` bypasses the check entirely for staff who need to intentionally replace an
existing punishment (e.g. correcting a wrong duration).

## Multi-instance sync

`sync.mode: database` (default) polls a small `easybans_sync_events` table every few seconds;
`sync.mode: redis` publishes over pub/sub for near-instant propagation. Database polling is the
default because every deployment already has the database - no extra infrastructure for a
single-proxy (or even most multi-proxy) setups. Sync events carry no punishment data themselves,
only an event type and a UUID payload; the receiving node re-reads authoritative state from the
database. This means the "payload" is trusted to be *a pointer*, not *the truth* - a node that
missed several events still ends up correct on the next one, since it just triggers a
re-fetch.

## Linked accounts: notify by default, auto-ban opt-in

A shared IP is not proof of ban evasion (NAT, households, schools, public wifi, mobile
carriers with a small IPv4 pool). `linked-accounts.notify-staff: true` /
`linked-accounts.auto-ban: false` by default reflects that: staff get a heads-up to make a
judgment call, but the plugin doesn't auto-ban a household member of a banned player unless an
operator explicitly opts in.

## Caches

- **`OfflinePlayerCache`**: bounded LRU (access-order `LinkedHashMap`) uuid&lt;-&gt;name cache,
  names stored as `byte[]` (ASCII) instead of `String` to roughly halve per-entry overhead -
  this backs both offline tab-completion and the general name/uuid resolution fallback chain.
- **`ActiveMuteCache`**: holds active-mute state only for currently-online players, refreshed
  on login/server-switch/mute-creation/removal and on remote sync events. Chat-mute enforcement
  reads this cache, never the database, so a chat message never triggers a DB round trip.
  Active *ban* status isn't cached the same way because it's only checked at connection time
  (login, server switch), which is already async and infrequent per player.

## Chat mutes are enforced backend-side via a LuckPerms permission node, not `PlayerChatEvent`

An earlier version cancelled Velocity's `PlayerChatEvent` to enforce a mute
(`ChatMuteListener.onChat`, now removed). Since chat signing was introduced in 1.19.1, cancelling
a *signed* message without informing the backend desyncs the client's signature chain
(unsecure-chat warnings, or a disconnect on servers enforcing secure profiles). The documented
fix was [SignedVelocity](https://github.com/4drian3d/SignedVelocity) - but that plugin's own
optional "remove unsecure chat warning" feature (PacketEvents/VPacketEvents) unconditionally
forces `enforcesSecureChat` on the client's JoinGame packet, which breaks chat entirely for
*cracked* players (they have no signing key at all to satisfy that requirement). Not viable on
a mixed cracked/premium network, which is what this proxy serves.

Instead, `mute/MuteNetworkSync.java` sends the mute reason/expiry to whichever backend the target
is currently connected to over a plugin message channel (`easybans:mute`), synchronously at the
moment `/mute`/`/unmute` runs (and again on every reconnect/server-switch, so a backend that
restarted still recovers it). The backend enforces the mute itself, backend-side, using ONLY this
message (cached in `TiroirSurvival`'s `MuteSyncCache`, with expiry checked locally by comparing
timestamps - no dependency on LuckPerms' cross-instance sync timing for the actual block) and
cancels its own native chat event (see `ChatFilterService`); because that cancellation never
round-trips back through the proxy, it never touches the signed-chat chain at all, regardless of
whether the muted player is signed (premium) or not (cracked). `ChatMuteListener` still exists,
but only to block chat-adjacent bypass commands (`/msg`, `/tell`, `/w`, `/me`) - a different,
unrelated concern from chat itself.

`MuteNetworkSync` *also* grants/revokes a network-wide LuckPerms permission node
(`easybans.muted`) in parallel, kept for other systems that may want to check "is this player
muted" (e.g. a scoreboard tag) and for LuckPerms' own auditing - but it is **not** what gates
chat, specifically because its propagation from proxy to backend depends on LuckPerms' configured
messaging service and isn't necessarily instant (observed in practice: mute/unmute could take
several seconds to minutes to take effect when only the permission was checked). If a backend
other than `TiroirSurvival` needs the same enforcement, it must
implement its own `easybans.muted` check and consume the same plugin message format - this isn't
generic yet, it was built for one backend (see the note in README.md if that changes).

## Importers: honest about format variance

Vanilla's ban-list JSON and LiteBans' schema are stable, documented formats, so those two
importers should work unmodified against any install. AdvancedBan, BanManager, MaxBans, and
BungeeAdminTools have all changed their storage schema across versions (and BanManager/MaxBans
predate consistent UUID storage in places). Their importers implement real, working queries
against each plugin's most common layout, with the exact SQL/YAML-key assumptions called out in
that importer's class-level javadoc - treat them as a strong starting point to verify against
your specific installed version before running against production data, not a guarantee that
covers every historical release.

## `/easybans reload` scope

Reloads `messages_*.yml` and `templates.yml` in place (both are re-read from disk and their
holder objects swap their internal state atomically, so every already-injected reference picks
up the change with no restart). Storage/Discord/sync settings are not hot-reloadable - changing
the database backend or sync transport live is a much riskier operation (connection pool
teardown mid-request, sync listeners left registered against a stale service) for a benefit
(avoiding one proxy restart) that doesn't justify the risk. Change those in `config.yml` and
restart.

## Target versions

- **Java 25** (toolchain in `build.gradle.kts`; Gradle auto-provisions it via the Foojay
  resolver if it isn't already installed).
- **Velocity `4.0.0-SNAPSHOT`** as the compile-time API coordinate (Velocity publishes its API
  as a snapshot on the PaperMC repository regardless of stability), targeting Minecraft 1.26.2
  clients.
- **bStats** plugin id `32581` (Velocity platform) for anonymous usage metrics - see
  https://bstats.org/plugin/velocity/BetterBans/32581.

## Points where the spec was ambiguous and a default was chosen

- **Package root**: `fr.mathilde.easybans` (all-lowercase, standard Java convention) rather
  than the original skeleton's `fr.mathilde.easyBans`.
- **Warning threshold semantics**: a threshold fires when the player's *all-time* warning count
  in that category exactly equals `count` (not a rolling time window, not `>=`). Simpler to
  configure and reason about; a rolling-window variant can be added later as an optional field
  on `WarningThreshold` without a schema change.
- **`/easybans allow`** exemption reason is a fixed string; the command doesn't take a custom
  reason argument since the spec didn't call for one and it isn't displayed to players.
- **History pagination size**: fixed at 8 entries/page (not configurable) - simple default,
  trivial to expose as a config key later if requested.
