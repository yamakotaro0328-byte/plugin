# EcoTP-QuickActions

**A vanilla Quick Actions (G key) menu, a weather vote, and a GUI admin shop.**

Standalone add-on for **EcoTP** (not required to install it, but designed to pair with it). Paper-only — uses the newer vanilla Dialog feature (1.21.7+), so it doesn't run on Spigot/Bukkit.

---

## Highlights

- **Quick Actions (G key)** — press G in-game to open a dialog with one-click buttons: Home, Set Home, Spawn, Balance, Ranking, a Menu shortcut (TPA/TPHere/Pay), weather votes, and the admin shop. `/quickmenu` opens the same dialog manually.
- **Weather vote** — `/weathervote clear` or `/weathervote rain` (alias `/wv`) starts a vote; once enough of that world's online players agree (configurable ratio, default 50%), the weather changes. Votes expire after a configurable timeout, and there's a per-player cooldown between votes.
- **Admin shop, fully GUI-driven** — `/adminshop` opens a chest GUI where players buy items (left-click) or sell them (right-click), with shift-click for a full stack/all matching items. `/adminshop admin` opens edit mode: hold an item and click an empty slot to list it, left-click a listed item to set its buy price, right-click for its sell price (typed into an anvil, no commands needed), shift-click to remove it. No stock tracking — the shop always has infinite supply and infinite funds, like a typical admin shop.
- **Fully bilingual** — every message is in `config.yml` (English by default, Japanese bundled), same `language: en/ja` convention as EcoTP.

---

## Commands

`/quickmenu`, `/weathervote <clear|rain>` (alias `/wv`), `/adminshop`, `/adminshop admin`

---

## Permissions

- `ecotpqa.quickmenu` (default: true)
- `ecotpqa.weathervote` (default: true)
- `ecotpqa.adminshop` (default: true)
- `ecotpqa.adminshop.admin` (default: op)

---

## Requirements

- Paper 26.2+ (Paper-only; the Dialog API isn't in Spigot/Bukkit)
- Optional: Vault + an economy plugin such as EcoTP (buying/selling reports "no economy available" without it)
- Optional: EcoTP (the Quick Actions dialog's Home/Spawn/Menu/etc. buttons just run EcoTP's commands, so they no-op without it)

---

## Configuration

See `config.yml` for the weather vote's duration/required ratio/cooldown, the admin shop's on/off switch and GUI size, and all player-facing messages in English and Japanese.
