# EasyBans

A moderation plugin for [Velocity](https://velocitypowered.com/) proxies: bans, IP bans,
mutes, warnings, kicks and staff notes, with escalating punishment templates, per-server
scoping, multi-instance sync, Discord webhooks, linked-account detection, and full i18n
(fr/en/es/it/ru/ar/de) - all working out of the box with an embedded H2 database.

See also: [CONFIG.md](CONFIG.md) (full configuration reference and how to add a language),
[ARCHITECTURE.md](ARCHITECTURE.md) (design decisions and trade-offs), and
[PERMISSIONS.md](PERMISSIONS.md) (every permission node).

## Requirements

- Java 25+
- A Velocity 4.0.0(-SNAPSHOT) proxy
- Nothing else for a single-proxy setup with the default H2 storage. MySQL/MariaDB/PostgreSQL
  and Redis are supported but optional - see CONFIG.md.
- **For mutes on 1.19.1+ clients**, install [SignedVelocity](https://github.com/4drian3d/SignedVelocity)
  on the proxy *and* on every backend server. Without it, cancelling a signed chat message to
  enforce a mute can desync the client's chat-signature chain (unsecure-chat warnings, or a
  disconnect on servers enforcing secure profiles). SignedVelocity needs no configuration or
  API integration from EasyBans - it just needs to be present.

## Installation

1. Drop the built jar (`build/libs/easybans-<version>.jar` after `./gradlew build`) into your
   Velocity `plugins/` folder.
2. Start the proxy once to generate `plugins/easybans/config.yml`, `templates.yml`, and
   `messages/messages_*.yml`.
3. (Optional) Edit `config.yml` if you want MySQL/MariaDB/PostgreSQL instead of the default
   embedded H2, enable the Discord webhook, or switch multi-instance sync to Redis.
4. Restart the proxy.

That's it - `/ban`, `/mute`, `/warn`, `/kick`, `/history`, etc. are available immediately with
op-only permissions until you wire up your permission plugin (see PERMISSIONS.md).

## Building

```
./gradlew build          # compiles and produces the shaded plugin jar (build/libs)
./gradlew shadowJar       # just the shaded jar
./gradlew runVelocity     # launches a local Velocity server with this plugin loaded, in run/
./gradlew test            # unit tests (duration parsing, template escalation, i18n key parity)
```

## Quick command reference

```
/ban <player> <duration|reason...> [-s] [-server:<name>] [-t:<template>]
/banip <player> <duration|reason...> [-s] [-server:<name>]
/unban <player> [reason...]
/mute <player> <duration|reason...> [-s] [-server:<name>] [-t:<template>]
/unmute <player> [reason...]
/warn <player> <category> <reason...>
/kick <player> <reason...> [-server:<name>]
/note <player> <note...>
/history <player> [page]
/staffhistory <staff> [page]

/easybans allow <player> <ip>          - exempt an account from IP bans without lifting the ban
/easybans rollback <staff>             - request rollback of every active punishment by <staff>
/easybans rollback confirm             - confirm within 30 seconds
/easybans import <source> <location>   - VANILLA, ESSENTIALS, MAXBANS, BANMANAGER, ADVANCEDBAN,
                                          BUNGEEADMINTOOLS, LITEBANS
/easybans language <code>               - set your own locale (fr/en/es/it/ru/ar/de by default)
/easybans template                      - list configured punishment templates/warning categories
/easybans reload                        - reload messages_*.yml and templates.yml
/easybans version
```

`-duration` accepts compact tokens like `1d`, `2h30m`, `1w`, or `permanent`/`perm`/`-1`.
`-server:<name>` scopes the punishment to a single backend server instead of the whole network.
`-t:<templateId>` applies the next escalation stage of a configured punishment template instead
of a manual duration/reason.

## A note on the imported-from-other-plugins support

`/easybans import` covers vanilla `banned-players.json`/`banned-ips.json`, EssentialsX,
MaxBans, BanManager, AdvancedBan, BungeeAdminTools, and LiteBans. The last five all involve
third-party database/file schemas that vary across versions - the bundled importers use each
plugin's most common layout, but if your install is older/customized, check the importer's
source comment for the exact query it runs before importing into production data. Vanilla and
LiteBans imports are the most reliable since those schemas are stable and well documented.
