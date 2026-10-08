# EcoTP

**A standalone economy plugin with distance-priced teleportation.** No Essentials or any other economy plugin required — EcoTP manages balances itself, while hooking into Vault (required) so shops and other plugins can use its economy too.

---

## Highlights

- **Self-contained economy** — balances are stored by EcoTP (YAML by default, or MySQL to share them across multiple servers). Vault is required, purely for interoperability with other plugins.
- **Import from Essentials** — switching away from Essentials? The first time a player is seen, their existing Essentials balance is imported automatically.
- **Distance-based teleport pricing** — `/home`, `/spawn`, `/warp`, `/tpa`, and `/tphere` are priced by real 3D distance: `max(min fee, ceil(distance / blocks-per-unit))`. Fully configurable.
- **/tphere (cash on delivery)** — summon another player to you; *they* pay, since they're the one who moves. `/tpa` works the other way around — you request to go to them, and you pay.
- **Anti-abuse teleport safety** — before any teleport completes: no hostile mobs nearby, no recent PvP, a few seconds standing still (looking around doesn't cancel it), and matching dimensions. Interrupt any of these and nothing is charged.
- **Multiple named homes** — `/sethome name`, `/home name`, `/delhome name`, `/homes` to list.
- **Warps** — admins set shared warp points with `/setwarp <name>`; players travel with `/warp <name>` or pick one from the GUI menu.
- **Flexible payment confirmation** — approve a charge via a clickable chat button, by re-running the same command, or with `/accept` / `/ok` (Bedrock-friendly).
- **GUI menu** — `/menu` (or a menu item handed out automatically — right-click it) covers home / spawn / warps / tpa / balance / pay / leaderboard / daily / donate / vote, so players never have to memorize commands.
- **Daily reward** — `/daily` pays out once every 24 hours, with a streak bonus for consecutive days.
- **Physical currency item** — `/ecoitem give` (admin) hands out money as an item that can be dropped, traded or stored in a chest; right-click to redeem the whole stack.
- **/donate** — send money as a donation with a server-wide thank-you broadcast. Recipients can personalize it with `/donatemessage <text>`.
- **Vote rewards, no extra plugin needed** — a built-in Votifier listener supporting **both** v1 (RSA key) and NuVotifier's v2 (token) protocol. Existing NuVotifier installs are detected too. Offline votes are queued and paid on next login, and voting-site links appear as clickable links from `/menu`.
- **Vote leaderboard & milestones** — `/votetop` shows the ranking; reaching configurable totals (e.g. 10 / 50 / 100 votes) pays a one-time bonus.
- **Private messages** — `/msg <player> <message>` (aliases `/tell`, `/w`, `/pm`) and `/reply` (`/r`).
- **AFK** — `/afk` or automatic after an idle time, with an [AFK] tab-list tag and an optional AFK kick.
- **Built-in chat format (optional)** — prefix (from LuckPerms via Vault) + name + message, meant to replace a separate chat-formatting plugin.
- **Romaji → Japanese (`/roma`)** — players can toggle automatic conversion of their own romaji chat into Japanese.
- **Clickable chat links** — URLs typed in chat become clickable links, regardless of each player's client settings.
- **Optional web dashboard** — a lightweight built-in web UI (off by default) to look up and adjust balances, with multiple staff logins.
- **Fully configurable** — every message and the currency name/unit live in `messages.yml` (English by default; Japanese bundled). Each feature and the built-in economy itself can be toggled on/off.
- **PlaceholderAPI support** — `%ecotp_balance%`, `%ecotp_balance_formatted%`, `%ecotp_sethome_cost%`, `%ecotp_votes%`, `%ecotp_afk%`.

---

## Commands

`/home`, `/sethome`, `/delhome`, `/homes`, `/spawn`, `/setspawn`, `/warp`, `/setwarp`, `/delwarp`, `/tpa`, `/tphere`, `/tpaccept`, `/tpdeny`, `/tpacancel`, `/accept` (alias `/ok`), `/balance`, `/pay`, `/eco`, `/baltop`, `/menu`, `/daily`, `/ecoitem`, `/donate`, `/donatemessage`, `/votetop`, `/msg`, `/reply`, `/afk`, `/roma`, `/ecotp reload`

---

## Requirements

- Paper/Spigot 26.2+, Java 25
- Required: Vault (economy interoperability)
- Optional: PlaceholderAPI (placeholders)
- Vote rewards work out of the box (built-in v1 + v2 listener) — NuVotifier is only needed if you'd rather keep using an existing install

---

## Configuration

See `config.yml` for prices, teleport-safety tuning, storage backend (YAML/MySQL), feature toggles, the `economy.enabled` switch (turn it off to use an external economy plugin via Vault instead), vote rewards and voting-site links, AFK, and the web dashboard. See `messages.yml` for all player-facing text and the currency name.
