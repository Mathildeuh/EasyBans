# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

EasyBans is a full moderation plugin for [Velocity](https://velocitypowered.com/) proxies:
bans, IP bans, mutes, warnings, kicks, staff notes, escalating punishment templates,
per-server scoping, multi-instance sync, Discord webhooks, linked-account detection, and i18n
(fr/en/es/it/ru/ar/de), working out of the box on an embedded H2 database.

- Package root: `fr.mathilde.easybans`
- Plugin id: `easybans` (declared in `src/main/resources/velocity-plugin.json`)
- Targets Velocity API `4.0.0-SNAPSHOT`, Java 25 toolchain, Minecraft 1.26.2

See README.md, CONFIG.md, ARCHITECTURE.md, and PERMISSIONS.md for full detail - this file only
covers what a coding agent needs to get oriented quickly.

## Build system

Gradle with the Kotlin DSL (`build.gradle.kts`). Key plugins:
- `com.gradleup.shadow` - produces the shaded/fat jar with every non-`compileOnly` dependency
  relocated under `fr.mathilde.easybans.libs.*`; `build` depends on `shadowJar`
- `xyz.jpenilla.run-velocity` - spins up a local Velocity instance for manual testing

```
./gradlew build          # compiles and produces the shaded plugin jar (build/libs)
./gradlew shadowJar       # just the shaded jar
./gradlew runVelocity     # launches a local Velocity server with this plugin loaded, in run/
./gradlew test            # unit tests
./gradlew clean
```

Single test class: `./gradlew test --tests "fr.mathilde.easybans.punishment.DurationParserTest"`

`velocity-plugin.json`'s `${version}` placeholder is expanded from `gradle.properties`
(`version=1.0.0`) via `processResources` - don't hardcode the version string in that file.

## Architecture notes

- Single Gradle module with package-level separation (no `core`/`api` split - see
  ARCHITECTURE.md for why).
- The plugin main class (`EasyBans.java`) is Guice-injected (`ProxyServer`, `Logger`,
  `@DataDirectory Path`, bStats `Metrics.Factory`) and wires every service by hand in
  `onProxyInitialization` - there is no DI framework beyond what Velocity/Guice already
  provides at the plugin-entry-point level.
- Punishments (ban/mute/warn/kick/note) are modeled as a sealed `Punishment` interface over
  records (`punishment/`), backed by a single `easybans_punishments` table with a `type`
  discriminator column - not one table per kind (see ARCHITECTURE.md).
- All database access goes through `PunishmentDao`/`PlayerDao`/etc. (`database/dao/`), and all
  of it is async via `DatabaseProvider#supplyAsync`/`runAsync`, which hop onto a dedicated
  executor - never call DAO methods synchronously from a Velocity network thread.
- Commands (`command/`) extend `AbstractEasyBansCommand` and go through `PunishmentService`
  (`punishment/PunishmentService.java`) for every mutation - never call a DAO directly from a
  command, since the service is what enforces anti-overwrite, cache invalidation, sync
  propagation, and Discord notification consistently.
- Messages are MiniMessage strings loaded by `MessageService` (`message/`) from
  `messages_<locale>.yml`; dynamic values are injected via `Placeholder.unparsed` (never raw
  string substitution before parsing) - see ARCHITECTURE.md for why that matters.
- The Velocity API dependency is `compileOnly` (plus `testImplementation` for unit tests that
  touch Adventure/MiniMessage types) - it's provided by the proxy at runtime and must never be
  shaded into the jar.
- Chat-based mute enforcement requires the separate SignedVelocity plugin on 1.19.1+ clients -
  see README.md's installation note and ARCHITECTURE.md.
