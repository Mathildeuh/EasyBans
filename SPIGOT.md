# SPIGOT.md

Resource description for publishing EasyBans on SpigotMC. The text below is written in
**BBCode** (SpigotMC's resource-description editor accepts BBCode tags directly) - paste the
content between the `[BBCODE]` markers into the resource description field. Everything outside
those markers (this header, the notes) is guidance for you, not part of the listing.

Fill in the bracketed placeholders (`[...]`) before publishing: download/source links,
Discord/support links, and screenshots. Do not publish with placeholders left in.

---

[BBCODE]
[CENTER][SIZE=6][B]EasyBans[/B][/SIZE]
A complete moderation suite for Velocity proxies - bans, mutes, warnings, kicks, escalating
punishment templates, and full multi-language support, working out of the box.[/CENTER]

[CENTER][I]No MySQL required to get started - runs on an embedded H2 database by default.[/I][/CENTER]

[HR][/HR]

[SIZE=5][B]What is EasyBans?[/B][/SIZE]
EasyBans is a network-wide moderation plugin for [B]Velocity[/B] proxies. It replaces every
vanilla ban-related command ([B]/ban[/B], [B]/ban-ip[/B], [B]/pardon[/B], [B]/pardon-ip[/B],
[B]/banlist[/B], [B]/kick[/B]) at the proxy level, so your staff get one consistent moderation
system across your entire network instead of each backend server enforcing its own vanilla
bans. Bans, mutes, warnings, kicks and staff notes are all tracked centrally, by UUID, with
full history and staff accountability.

[SIZE=5][B]Key Features[/B][/SIZE]
[LIST]
[*][B]Bans, IP bans, mutes, warnings, kicks & staff notes[/B] - all UUID-based, all with reasons, durations, and staff attribution
[*][B]Escalating punishment templates[/B] - configure a punishment ladder (e.g. 3d -> 14d -> permanent) that automatically applies the right stage based on a player's prior offenses
[*][B]Warning categories with auto-escalation[/B] - "3 spam warnings -> auto-mute", "5 warnings -> auto-ban", fully configurable
[*][B]Per-server scoping[/B] - ban a player from one backend server only, or network-wide, with a single flag
[*][B]Multi-instance sync[/B] - running more than one proxy against the same database? Punishments propagate automatically (database polling or Redis pub/sub)
[*][B]Full multi-language support[/B] - French, English, Spanish, Italian, Russian, Arabic and German out of the box, all in MiniMessage format (hex colors, gradients, hover text). Players can set their own language with [B]/easybans language <code>[/B]
[*][B]Fully customizable, multi-line kick/ban screens[/B] - with gradients, hover text, and per-template-stage overrides
[*][B]Discord webhook integration[/B] - every ban/mute/warn/kick posted to your staff Discord as a rich embed
[*][B]Linked-account (alt) detection[/B] - notifies staff (or optionally auto-bans) when a new connection shares an IP with a banned account
[*][B]Import from other plugins[/B] - migrate straight from vanilla ban-lists, EssentialsX, MaxBans, BanManager, AdvancedBan, BungeeAdminTools, or LiteBans
[*][B]Tab-completion everywhere[/B] - online and offline player names, flags, durations, templates, categories, servers
[*][B]MySQL, MariaDB, PostgreSQL & H2[/B] - pick your backend, or just use the zero-config embedded default
[/LIST]

[SIZE=5][B]Commands[/B][/SIZE]
[CODE]
/ban <player> <duration|reason...> [-s] [-server:<name>] [-t:<template>]
/banip <player> <duration|reason...> [-s] [-server:<name>]
/unban <player> [reason...]
/pardon-ip <ip> [reason...]
/mute <player> <duration|reason...> [-s] [-server:<name>] [-t:<template>]
/unmute <player> [reason...]
/warn <player> <category> <reason...>
/kick <player> <reason...> [-server:<name>]
/note <player> <note...>
/history <player> [page]
/staffhistory <staff> [page]
/banlist [players|ips] [page]
/lookup <id>
/easybans allow <player> <ip>
/easybans rollback <staff|confirm>
/easybans import <source> <location>
/easybans language <code>
/easybans template
/easybans reload
[/CODE]
[I]Every vanilla ban command name ([B]ban-ip[/B], [B]pardon[/B], [B]pardon-ip[/B], [B]banlist[/B])
is registered as an alias, so EasyBans transparently takes over for staff already used to
vanilla syntax.[/I]

[SIZE=5][B]Requirements[/B][/SIZE]
[LIST]
[*]Java 25+
[*]Velocity proxy
[*]Nothing else for a single-proxy setup - H2 is embedded and zero-config
[*][B]For mutes on 1.19.1+ clients[/B], install [URL='https://github.com/4drian3d/SignedVelocity']SignedVelocity[/URL] on the proxy and every backend server (prevents chat-signature desync when a mute cancels a message)
[/LIST]

[SIZE=5][B]Configuration[/B][/SIZE]
Everything is generated with sensible defaults on first run: [B]config.yml[/B] (storage,
sync, Discord, linked-account detection, locale), [B]templates.yml[/B] (punishment templates
and warning categories), and [B]messages_<locale>.yml[/B] for each of the 7 bundled languages.
Full reference: see [B]CONFIG.md[/B] in the plugin's GitHub repository.

[SIZE=5][B]Permissions[/B][/SIZE]
Every permission is namespaced under [B]easybans.*[/B] (e.g. [B]easybans.ban[/B],
[B]easybans.mute[/B], [B]easybans.warn[/B], [B]easybans.kick[/B], [B]easybans.history[/B],
[B]easybans.rollback[/B], [B]easybans.import[/B]...) - nothing is granted by default beyond
server operators. Full list in [B]PERMISSIONS.md[/B] on GitHub.

[SIZE=5][B]Links[/B][/SIZE]
[LIST]
[*][B]Source / Issues:[/B] [URL][link to GitHub repository][/URL]
[*][B]Documentation:[/B] README.md / CONFIG.md / ARCHITECTURE.md / PERMISSIONS.md in the repository
[*][B]Support / Discord:[/B] [link to your support Discord]
[/LIST]

[HR][/HR]
[CENTER][SIZE=3]Found a bug or have a feature request? Open an issue on GitHub or reach out on Discord.[/SIZE][/CENTER]
[/BBCODE]

---

## Suggested Spigot resource metadata

- **Category:** Proxy Support (Velocity)
- **Supported platforms/versions:** Velocity, Minecraft 1.26.2 (Velocity API `4.0.0-SNAPSHOT`)
- **Tags/keywords:** ban, moderation, mute, kick, warn, velocity, proxy, punishment, i18n, multi-language, discord webhook

## Before publishing, replace:

- `[link to GitHub repository]`
- `[link to your support Discord]`
- Add at least 2-3 screenshots (kick screen, `/history` output, config file) - Spigot resource
  pages with screenshots get significantly more downloads.
