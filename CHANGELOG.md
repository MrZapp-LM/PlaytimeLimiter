# Changelog

## 1.2.0 (2026-10-27)

### Added
- `default-limit` config option: a daily limit in minutes for every player without an individual limit. `0` turns it off (default).
- `/playtime set <player> 0` (or `unlimited`) exempts a player from all limits, including the default limit.

### Changed
- Individual limits set with `/playtime set` now override the default limit.
- `/playtime remove <player>` falls back to the default limit instead of removing all tracking; today's used time is kept.
- `/playtime list` shows the default limit plus all individual and exempt players.

### Upgrading from 1.1.0
- Replace the jar and restart. Your existing `config.yml` is not overwritten: add `default-limit: 60` (or any value) yourself and run `/playtime reload`.
- Existing player limits are kept as individual limits.
- The default limit also applies to ops. Exempt yourself with `/playtime set <you> 0` before turning it on.

## 1.1.0 (2026-10-25)

### Added
- `config.yml` with reset time, timezone, weekend bonus, warning times and custom messages (MiniMessage, placeholders `<limit>`, `<left>`, `<reset>`).
- `/playtime reload` to apply config changes without a restart.

## 1.0.0 (2026-10-25)

- First release: per-player daily limits via `/playtime set|remove|give|reset|info|list`, chat warnings, kick when time is up, rejoin blocked until the next day, data saved across restarts.
