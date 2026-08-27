# PeekHealth

**See any mob's or player's health in your action bar — just look at it, or hit it. Paper 1.21+ and 26.x.**

---

## Why this plugin exists

The popular free health-display plugin in this niche (677k downloads) stopped being updated in June 2023.
The best-maintained alternative on Modrinth has 4.5k downloads. It is a small, universally useful feature
that simply needs someone to keep it building against current Paper.

## Features

- **Look mode** — a crosshair raytrace shows the health of whatever you are aiming at, and it is wall-aware
  (no seeing through blocks)
- **Damage mode** — shows health when you hit something, melee or projectile
- **Both at once**, if you want
- **Hearts bar** (`❤❤❤❤❤`) or plain numbers, your choice
- **MiniMessage and legacy `&` formats** for every string
- **Per-player toggle**, persisted across restarts
- **Entity blacklist and world blacklist**
- Works on **all of 1.21.x**: the max-health attribute moved in 1.21.3, and PeekHealth handles both spellings
- No dependencies, one tiny jar

## Commands

| Command | What it does |
|---|---|
| `/peekhealth` (alias `/ph`) | Toggle the display for yourself |
| `/peekhealth reload` | Reload the config |

| Permission | Default |
|---|---|
| `peekhealth.use` | true |
| `peekhealth.reload` | op |

## Compatibility

Built for the Paper API 1.21 and up. Every release is started on a **live Paper 1.21.11 server and a live
Paper 26.2 server** and the actual behaviour is checked — not just "the plugin loads".

## Source & licence

MIT licensed, source on [GitHub](https://github.com/arvidhqx-commits/peekhealth).

## Development note

This project is **AI-assisted**: the code is written with Claude under the direction, testing and release
approval of the maintainer. Every release is run against a live Paper server before it ships.
