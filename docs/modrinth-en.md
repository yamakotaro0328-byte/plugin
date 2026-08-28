# EcoTP

**A standalone economy plugin with distance-priced teleportation.** No Essentials or any other economy plugin required — EcoTP manages balances itself, while still hooking into Vault (optional) so shops and other plugins can use its economy too.

## Highlights

- **Self-contained economy** — balances are stored by EcoTP (YAML by default, or MySQL to share them across multiple servers). Vault is optional, purely for interoperability with other plugins.
- **Import from Essentials** — switching away from Essentials? The first time a player is seen, their existing Essentials balance is imported automatically.
- **Distance-based teleport pricing** — `/home`, `/spawn`, `/tpa`, and `/tphere` are priced by real 3D distance: `max(min fee, ceil(distance / blocks-per-unit))`. Fully configurable.
- **/tphere (cash on delivery)** — summon another player to you; *they* pay, since they're the one who moves. `/tpa` works the other way around — you request to go to them, and you pay.
- **Anti-abuse teleport safety** — before any teleport completes, the plugin checks: no hostile mobs nearby, no recent PvP, a few seconds standing still (looking around doesn't cancel it), and matching dimensions. Interrupt any of these and nothing is charged.
- **Multiple named homes** — set as many homes as your server allows (`/sethome name`, `/home name`, `/delhome name`, `/homes` to list).
- **Flexible payment confirmation** — approve a charge via a clickable chat button, by re-running the same command, or with `/accept` / `/ok` (Bedrock-friendly).
- **GUI menu** — `/menu` opens a click-through menu for players who'd rather not memorize commands.
- **Fully configurable** — every message and the currency name/unit live in `messages.yml` (ships in English by default; a Japanese translation is bundled too). Individual features (home, sethome, spawn, tpa, tphere, pay, baltop, menu) and the built-in economy itself can each be toggled on/off.
- **PlaceholderAPI support** — `%ecotp_balance%`, `%ecotp_balance_formatted%`, `%ecotp_sethome_cost%`.

## Commands

`/home`, `/sethome`, `/delhome`, `/homes`, `/spawn`, `/setspawn`, `/tpa`, `/tphere`, `/tpaccept`, `/tpdeny`, `/tpacancel`, `/accept` (alias `/ok`), `/balance`, `/pay`, `/eco`, `/baltop`, `/menu`, `/ecotp reload`

## Requirements

- Paper/Spigot 26.2, Java 25
- Optional: Vault (economy interoperability), PlaceholderAPI (placeholders)

## Configuration

See `config.yml` for prices, teleport-safety tuning (countdown, mob radius, PvP cooldown), storage backend (YAML/MySQL), feature toggles, and the `economy.enabled` switch (turn it off to use an external economy plugin via Vault instead). See `messages.yml` for all player-facing text and the currency name.
