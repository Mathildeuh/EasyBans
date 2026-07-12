# PERMISSIONS.md

Every permission node EasyBans checks, defined in `fr.mathilde.easybans.command.Permissions`.
None are granted by default (`op`-only via Velocity's default permission behaviour) unless
your permission plugin says otherwise.

## Punishment commands

| Permission          | Grants                                                    |
|----------------------|------------------------------------------------------------|
| `easybans.ban`       | `/ban`                                                      |
| `easybans.banip`     | `/banip`                                                    |
| `easybans.unban`     | `/unban`                                                    |
| `easybans.mute`      | `/mute`                                                     |
| `easybans.unmute`    | `/unmute`                                                   |
| `easybans.warn`      | `/warn`                                                     |
| `easybans.kick`      | `/kick`                                                     |
| `easybans.note`      | `/note`                                                     |
| `easybans.history`   | `/history <player>`                                         |
| `easybans.staffhistory` | `/staffhistory <staff>`                                  |
| `easybans.rollback`  | `/easybans rollback <staff>` and `/easybans rollback confirm` |
| `easybans.import`    | `/easybans import <source> <location>`                      |
| `easybans.allow`     | `/easybans allow <player> <ip>`                              |
| `easybans.reload`    | `/easybans reload`                                          |
| `easybans.template`  | `/easybans template` (lists configured templates/categories) |

`/easybans language <code>` requires no permission - every player may set their own language.
`/easybans version` requires no permission.

## Behavioural / modifier permissions

| Permission                  | Effect                                                                                   |
|-------------------------------|-------------------------------------------------------------------------------------------|
| `easybans.override`         | Bypasses anti-overwrite: allowed to replace an already-active ban/mute on a target.        |
| `easybans.exempt`           | Holder can never be the *target* of a punishment (checked when the target is online).      |
| `easybans.notify.linked`    | Receives the chat notification when a player connects sharing an IP with a banned account. |
| `easybans.notify.broadcast` | Receives the public broadcast message every non-silent punishment sends.                   |
| `easybans.silent`           | Required in addition to the `-s` flag for a staff member's punishment to actually be silent (not just requested silent). |

## Notes

- `easybans.exempt` only protects a player while they are **online**, since Velocity has no
  reliable way to query an offline player's permissions without a permission plugin's own
  offline API (which varies by implementation). Punishing an offline exempt player is not
  blocked - grant it to trusted staff and treat it as a courtesy safeguard, not a hard
  guarantee.
- There is no `easybans.admin` umbrella node on purpose - grant the specific nodes each staff
  rank actually needs. Wildcard permission plugins (e.g. `easybans.*`) work fine if your
  permission plugin supports node globbing; EasyBans itself does not implement globbing.
