[CENTER][SIZE=6][B]EcoTP-QuickActions[/B][/SIZE][/CENTER]
[CENTER][B]A vanilla Quick Actions (G key) menu, a weather vote, and a GUI admin shop.[/B][/CENTER]

Standalone add-on for [B]EcoTP[/B] (not required to install it, but designed to pair with it). Paper-only — uses the newer vanilla Dialog feature (1.21.7+), so it doesn't run on Spigot/Bukkit.

[SIZE=5][B]Highlights[/B][/SIZE]
[LIST]
[*][B]Quick Actions (G key)[/B] — press G in-game to open a dialog with one-click buttons: Home, Set Home, Spawn, Balance, Ranking, a Menu shortcut (TPA/TPHere/Pay), weather votes, and the admin shop. [I]/quickmenu[/I] opens the same dialog manually.
[*][B]Weather vote[/B] — [I]/weathervote clear[/I] or [I]/weathervote rain[/I] (alias [I]/wv[/I]) starts a vote; once enough of that world's online players agree (configurable ratio, default 50%), the weather changes. Votes expire after a configurable timeout, and there's a per-player cooldown between votes.
[*][B]Admin shop, fully GUI-driven[/B] — [I]/adminshop[/I] opens a chest GUI where players buy items (left-click) or sell them (right-click), with shift-click for a full stack/all matching items. [I]/adminshop admin[/I] opens edit mode: hold an item and click an empty slot to list it, left-click a listed item to set its buy price, right-click for its sell price (just type the number in chat when prompted), shift-click to remove it. No stock tracking — the shop always has infinite supply and infinite funds, like a typical admin shop.
[*][B]Fully bilingual[/B] — every message is in [I]config.yml[/I] (English by default, Japanese bundled), same [I]language: en/ja[/I] convention as EcoTP.
[/LIST]

[SIZE=5][B]Commands[/B][/SIZE]
[CODE]/quickmenu
/weathervote <clear|rain> (alias /wv)
/adminshop
/adminshop admin[/CODE]

[SIZE=5][B]Permissions[/B][/SIZE]
[LIST]
[*]ecotpqa.quickmenu (default: true)
[*]ecotpqa.weathervote (default: true)
[*]ecotpqa.adminshop (default: true)
[*]ecotpqa.adminshop.admin (default: op)
[/LIST]

[SIZE=5][B]Requirements[/B][/SIZE]
[LIST]
[*]Paper 26.2+ (Paper-only; the Dialog API isn't in Spigot/Bukkit)
[*][B]Optional:[/B] Vault + an economy plugin such as EcoTP (buying/selling reports "no economy available" without it)
[*][B]Optional:[/B] EcoTP (the Quick Actions dialog's Home/Spawn/Menu/etc. buttons just run EcoTP's commands, so they no-op without it)
[/LIST]

[SIZE=5][B]Configuration[/B][/SIZE]
See [I]config.yml[/I] for the weather vote's duration/required ratio/cooldown, the admin shop's on/off switch and GUI size, and all player-facing messages in English and Japanese.
