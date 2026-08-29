[CENTER][SIZE=6][B]EcoTP[/B][/SIZE][/CENTER]
[CENTER][B]A standalone economy plugin with distance-priced teleportation.[/B][/CENTER]

No Essentials or any other economy plugin required — EcoTP manages balances itself, while hooking into Vault ([B]required[/B]) so shops and other plugins can use its economy too.

[SIZE=5][B]Highlights[/B][/SIZE]
[LIST]
[*][B]Self-contained economy[/B] — balances are stored by EcoTP (YAML by default, or MySQL to share them across multiple servers). Vault is required, purely for interoperability with other plugins.
[*][B]Import from Essentials[/B] — switching away from Essentials? The first time a player is seen, their existing Essentials balance is imported automatically.
[*][B]Distance-based teleport pricing[/B] — [I]/home[/I], [I]/spawn[/I], [I]/tpa[/I], and [I]/tphere[/I] are priced by real 3D distance: [CODE]max(min fee, ceil(distance / blocks-per-unit))[/CODE]. Fully configurable.
[*][B]/tphere (cash on delivery)[/B] — summon another player to you; [I]they[/I] pay, since they're the one who moves. [I]/tpa[/I] works the other way around — you request to go to them, and you pay.
[*][B]Anti-abuse teleport safety[/B] — before any teleport completes: no hostile mobs nearby, no recent PvP, a few seconds standing still (looking around doesn't cancel it), and matching dimensions. Interrupt any of these and nothing is charged.
[*][B]Multiple named homes[/B] — set as many homes as your server allows ([I]/sethome name[/I], [I]/home name[/I], [I]/delhome name[/I], [I]/homes[/I] to list).
[*][B]Flexible payment confirmation[/B] — approve a charge via a clickable chat button, by re-running the same command, or with [I]/accept[/I] / [I]/ok[/I] (Bedrock-friendly).
[*][B]GUI menu[/B] — [I]/menu[/I] opens a click-through menu for players who'd rather not memorize commands.
[*][B]Fully configurable[/B] — every message and the currency name/unit live in messages.yml (English by default; a Japanese translation is bundled too). Individual features and the built-in economy itself can each be toggled on/off.
[*][B]PlaceholderAPI support[/B] — %ecotp_balance%, %ecotp_balance_formatted%, %ecotp_sethome_cost%.
[/LIST]

[SIZE=5][B]Commands[/B][/SIZE]
[CODE]/home, /sethome, /delhome, /homes, /spawn, /setspawn, /tpa, /tphere,
/tpaccept, /tpdeny, /tpacancel, /accept (alias /ok), /balance, /pay,
/eco, /baltop, /menu, /ecotp reload[/CODE]

[SIZE=5][B]Requirements[/B][/SIZE]
[LIST]
[*]Paper/Spigot 26.2, Java 25
[*][B]Required:[/B] Vault (economy interoperability)
[*][B]Optional:[/B] PlaceholderAPI (placeholders)
[/LIST]

[SIZE=5][B]Configuration[/B][/SIZE]
See [I]config.yml[/I] for prices, teleport-safety tuning (countdown, mob radius, PvP cooldown), storage backend (YAML/MySQL), feature toggles, and the [I]economy.enabled[/I] switch (turn it off to use an external economy plugin via Vault instead). See [I]messages.yml[/I] for all player-facing text and the currency name.
