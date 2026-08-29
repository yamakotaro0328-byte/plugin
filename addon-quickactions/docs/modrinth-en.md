# EcoTP-QuickActions

**A vanilla Quick Actions (G key) menu, a weather vote, and a GUI admin shop.**

Standalone add-on for **EcoTP** (not required to install it, but designed to pair with it). Paper-only — uses the newer vanilla Dialog feature (1.21.7+), so it doesn't run on Spigot/Bukkit.

---

## Highlights

- **Quick Actions (G key)** — press G in-game to open a dialog with one-click buttons: Home, Set Home, Spawn, Balance, Ranking, a Menu shortcut (TPA/TPHere/Pay), weather votes, the admin shop, and the player shop (browse + your own listings). `/quickmenu` opens the same dialog manually.
- **Weather vote** — `/weathervote clear` or `/weathervote rain` (alias `/wv`) starts a vote; once enough of that world's online players agree (configurable ratio, default 50%), the weather changes. Votes expire after a configurable timeout, and there's a per-player cooldown between votes.
- **Admin shop, fully GUI-driven** — `/adminshop` opens a chest GUI where players buy items (left-click) or sell them (right-click), with shift-click for a full stack/all matching items. `/adminshop admin` opens edit mode: hold an item and click an empty slot to list it, left-click a listed item to set its buy price, right-click for its sell price (just type the number in chat when prompted), shift-click to remove it. No stock tracking — the shop always has infinite supply and infinite funds, like a typical admin shop.
- **Player-to-player shop** — `/pshop browse` (alias `/ps`) shows every player's active listings in one GUI; left-click buys 1, shift-left buys the whole remaining stock, and payment goes straight from buyer to seller. `/pshop my` manages your own listings: hold an item and click an empty slot, then type the quantity and price per item in chat when prompted; click a listing to take it down and get your items back. Unlike the admin shop, stock is real and limited to what sellers actually listed. `/pshop history` shows your own last trades (as buyer and seller).
- **Fully bilingual** — every message is in `config.yml` (English by default, Japanese bundled), same `language: en/ja` convention as EcoTP.

---

## Commands

`/quickmenu`, `/weathervote <clear|rain>` (alias `/wv`), `/adminshop`, `/adminshop admin`, `/pshop browse|my|history` (alias `/ps`)

---

## Permissions

- `ecotpqa.quickmenu` (default: true)
- `ecotpqa.weathervote` (default: true)
- `ecotpqa.adminshop` (default: true)
- `ecotpqa.adminshop.admin` (default: op)
- `ecotpqa.playershop` (default: true)

---

## Requirements

- Paper 26.2+ (Paper-only; the Dialog API isn't in Spigot/Bukkit)
- Optional: Vault + an economy plugin such as EcoTP (buying/selling reports "no economy available" without it)
- Optional: EcoTP (the Quick Actions dialog's Home/Spawn/Menu/etc. buttons just run EcoTP's commands, so they no-op without it)

---

## Configuration

See `config.yml` for the weather vote's duration/required ratio/cooldown, the admin shop's on/off switch and GUI size, and all player-facing messages in English and Japanese.
