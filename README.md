<img src="assets/icon.png" alt="PlaytimeLimiter icon" width="128" align="right">

# PlaytimeLimiter

A small Paper plugin that limits how long individual players can play per day.
Limits are set by command, no permission plugin is needed, and everything survives restarts.

Built for **Paper 26.3** (Java 25).

## Features

- Per-player daily limits, set with one command
- Optional default limit for everyone; individual limits override it, and single players can be exempted
- Chat warnings before time runs out, then a kick; rejoining is blocked until the reset
- Configurable reset time, timezone, weekend bonus, warnings and messages
- Data saved to `plugins/PlaytimeLimiter/data.yml` every minute, after every command and on shutdown

## Commands

All subcommands are for ops and the console (`playtimelimiter.admin`, default: op).
Every player can run `/playtime` to see their own time left. `/pt` is an alias.

| Command | Description |
|---|---|
| `/playtime` | Show your own playtime |
| `/playtime set <player> <time>` | Set an individual limit: `90`, `90m`, `2h`, `1h30m`, or `unlimited` to exempt the player |
| `/playtime remove <player>` | Remove the individual limit (the default limit applies again) |
| `/playtime give <player> <time>` | Extra time for today only |
| `/playtime reset <player>` | Reset today's used time |
| `/playtime info <player>` | Show a player's status |
| `/playtime list` | Show the default limit and all individual limits |
| `/playtime reload` | Reload `config.yml` |

A player must have joined the server once before you can give them a limit.

When `default-limit` is on, it applies to ops too. Exempt yourself with `/playtime set <you> unlimited`.

## Config

`plugins/PlaytimeLimiter/config.yml`:

```yaml
reset-time: "00:00"      # when used time goes back to zero (HH:mm)
timezone: ""             # e.g. "Europe/Berlin"; empty = server timezone
default-limit: 0         # minutes for everyone without an individual limit; 0 = off
weekend-bonus: 0         # extra minutes on Saturday and Sunday
warn-at: [15, 10, 5, 1]  # minutes left at which players are warned
messages:                # MiniMessage format; placeholders <limit>, <left>, <reset>
  kick: "<red>Your daily playtime of <limit> is used up.<newline><gray>Come back after <reset>!"
  warning: "<yellow>You have <gold><left></gold> of playtime left today."
```

## Building

```bash
./gradlew build
```

The jar ends up in `build/libs/`.

## License

MIT, see [LICENSE](LICENSE).
