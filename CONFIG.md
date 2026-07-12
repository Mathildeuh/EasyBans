# CONFIG.md

Full reference for `config.yml`, `templates.yml`, and `messages/messages_*.yml`, plus how to
add a new language. All three files live in the plugin's data folder
(`plugins/easybans/` by default) and are generated with sensible defaults on first run.

## config.yml

```yaml
storage:
  type: h2                     # h2 (default), mysql, mariadb, postgresql
  host: localhost
  port: 3306
  database: easybans
  username: easybans
  password: ""
  use-ssl: false
  table-prefix: "easybans_"
  pool-size: 10
  connection-timeout-ms: 5000
  h2-file-name: easybans       # H2 only: file name (no extension) under the data folder

general:
  debug: false
  default-scope: global        # "global" or a server name, used when no -server:<name> flag is given
  broadcast-punishments: true
  broadcast-permission: "easybans.notify.broadcast"
  kick-online-player-on-ban: true

cache:
  offline-player-cache-size: 5000   # bounded LRU cache of uuid<->name for offline players
  uuid-cache-size: 5000

locale:
  default-locale: fr
  auto-detect-locale: true     # try the client's own locale before falling back to default-locale
  supported-locales: [fr, en, es, it, ru, ar, de]

linked-accounts:
  notify-staff: true           # notify players with easybans.notify.linked
  auto-ban: false              # also ban new accounts sharing an IP with a banned one (off by default - see ARCHITECTURE.md)

sync:
  mode: database                # none, database (polling, zero extra infra), redis (pub/sub, near-instant)
  poll-interval-seconds: 3      # only used when mode: database
  redis:
    host: localhost
    port: 6379
    password: ""
    channel: "easybans:sync"

discord:
  enabled: false
  webhook-url: ""
  username: "EasyBans"
  avatar-url: ""
  events:
    ban: true
    unban: true
    mute: true
    unmute: true
    warn: true
    kick: true
```

### MySQL/MariaDB/PostgreSQL

Set `storage.type`, fill in `host`/`port`/`database`/`username`/`password`, restart the proxy.
Schema migrations run automatically on startup. Switching storage types does **not** migrate
existing data between backends - it's a fresh schema on the new backend.

### Redis sync

Only relevant for networks running more than one Velocity instance against the same database.
`mode: database` (the default) needs nothing extra and propagates changes within
`poll-interval-seconds`. `mode: redis` needs a reachable Redis server but propagates instantly
with no polling.

## templates.yml

Two independent things live here:

### Punishment templates (escalating ban/mute)

```yaml
punishment-templates:
  cheating:
    type: ban              # ban or mute
    stages:
      - after: 1            # 1st time this template is applied to a player
        duration: 3d
        reason: "Cheating - 1st offense"
      - after: 2
        duration: 14d
        reason: "Cheating - 2nd offense"
      - after: 3
        duration: permanent
        reason: "Cheating - repeat offense"
        kickscreen: "cheating.final"   # optional: resolves to kickscreen.template.cheating.final
```

Apply a template instead of a manual duration/reason with `-t:<id>`, e.g. `/ban Steve -t:cheating`.
The plugin counts how many times this player already triggered this template and applies the
matching stage (the last configured stage repeats indefinitely once exceeded).

### Warning categories and auto-escalation

```yaml
warning-categories:
  spam:
    display-name: "Spam"
    thresholds:
      - count: 3
        commands:
          - "mute %player% 1h Automatic mute: repeated spam warnings"
      - count: 5
        commands:
          - "ban %player% 1d Automatic ban: repeated spam warnings"
```

`count` is the *total* number of warnings ever issued to the player in that category (not a
rolling window). Commands run from the proxy console with `%player%` substituted.

## messages/messages_<locale>.yml

Everything the plugin ever displays lives here, in [MiniMessage](https://docs.advntr.dev/minimessage/format.html)
format - no legacy `&`-color codes. `messages_en.yml` is the canonical key list; every other
locale file must have exactly that key set (enforced by a unit test - `MessageKeyParityTest`).

### Placeholders

Placeholders use MiniMessage tag syntax `<name>`, not the `{NAME}` notation from the original
spec - `{DURATION}` corresponds to `<duration>`, `{STAFF}` to `<staff>`, etc. Available on
punishment-related keys:

| Tag                    | Meaning                                                        |
|-------------------------|-----------------------------------------------------------------|
| `<reason>`             | Punishment reason                                                |
| `<staff>`              | Staff member's name (or `CONSOLE`)                               |
| `<server>`             | Server name where the event happened (may be empty for global)  |
| `<duration>`           | Remaining time until expiry, or "Permanent"                      |
| `<original_duration>`  | Duration at the time the punishment was created                  |
| `<time_since>`         | Elapsed time since the punishment was created                    |
| `<original_date>`      | Creation date/time (UTC, `yyyy-MM-dd HH:mm`)                     |
| `<id>`                 | Punishment's database id                                         |
| `<player>`             | Target player's name (command feedback/broadcast keys only)      |

Every one of these is injected as literal text (`Placeholder.unparsed`), never re-parsed as
MiniMessage - a reason containing `<red>` displays literally, it can't inject formatting.

### Kick screens

`kickscreen.ban`, `kickscreen.ban-ip`, and `kickscreen.kick` are YAML lists of strings, one
Component per line. A punishment template stage can point at a custom screen via its
`kickscreen:` field (e.g. `cheating.final` resolves to `kickscreen.template.cheating.final`);
if that key is missing in the player's locale, EasyBans falls back to the generic
`kickscreen.ban`/`kickscreen.kick`.

**RTL guidance (Arabic):** keep each placeholder on its own line, right after a label and a
colon (`السبب: <white><reason>`), rather than embedding it mid-sentence. The Minecraft client
renders each line's bidi direction independently; a placeholder value buried inside a long
right-to-left sentence is where visual glitches tend to show up, not at the end of a short
labeled line. `messages_ar.yml` follows this pattern throughout.

### Adding a new language

1. Copy `messages_en.yml` to `messages_<code>.yml` in `plugins/easybans/messages/` (use an
   [ISO 639-1](https://en.wikipedia.org/wiki/List_of_ISO_639_language_codes) code).
2. Translate every value, keeping all `<tag>` placeholders and YAML structure identical.
3. Add `<code>` to `locale.supported-locales` in `config.yml`.
4. `/easybans reload` (or restart).

If you're adding it to the plugin's bundled defaults rather than a single server's install,
also add the file under `src/main/resources/messages/` and extend `SupportedLocale` (an enum -
this part does need a rebuild) plus `MessageKeyParityTest`'s locale list.
