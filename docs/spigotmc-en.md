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
[*][B]GUI menu[/B] — [I]/menu[/I] (or a physical menu item that's automatically handed out and opens the same menu on right-click) opens a click-through menu covering home/spawn/tpa/balance/pay/leaderboard/daily/donate/vote, so players never have to memorize commands.
[*][B]Daily reward[/B] — [I]/daily[/I] pays out once every 24 hours, with a streak bonus for logging in consecutive days.
[*][B]Physical currency item ([I]/ecoitem[/I])[/B] — mint a stack of a configurable value per item; it can be dropped, traded, or stored in a chest like any other item, and right-clicking redeems the whole stack.
[*][B]Optional web dashboard[/B] — a lightweight built-in web UI (off by default) to look up and adjust balances from a browser, with support for multiple named staff logins.
[*][B]Fully configurable[/B] — every message and the currency name/unit live in messages.yml (English by default; a Japanese translation is bundled too). Individual features and the built-in economy itself can each be toggled on/off.
[*][B]PlaceholderAPI support[/B] — %ecotp_balance%, %ecotp_balance_formatted%, %ecotp_sethome_cost%.
[*][B]/donate[/B] — send money to another player framed as a donation; on success it broadcasts a server-wide thank-you message. Recipients can personalize their own message with [I]/donatemessage <text>[/I] (placeholders: {player}, {amount}), or [I]/donatemessage reset[/I] to go back to the default.
[*][B]Vote rewards, no extra plugin needed[/B] — EcoTP ships its own built-in Votifier listener supporting [B]both[/B] the classic v1 (RSA key) protocol and NuVotifier's protocol v2 (token-based) — most modern voting sites only support v2, and it's fully covered out of the box. Already running NuVotifier for other plugins? EcoTP detects that too. Either way, voters get currency (1000 by default, configurable) plus a server-wide broadcast; a vote for a currently-offline player is queued and paid out the next time they join. List your voting sites in config.yml and the Vote tile in /menu shows them as clickable links in chat.
[*][B]Clickable chat links[/B] — any http(s):// or www. URL a player types in chat is automatically turned into a clickable, hoverable link, regardless of each player's own client-side "Chat Links" setting.
[/LIST]

[SIZE=5][B]Commands[/B][/SIZE]
[CODE]/home, /sethome, /delhome, /homes, /spawn, /setspawn, /tpa, /tphere,
/tpaccept, /tpdeny, /tpacancel, /accept (alias /ok), /balance, /pay,
/eco, /baltop, /menu, /ecotp reload, /donate, /donatemessage, /daily,
/ecoitem[/CODE]

[SIZE=5][B]Requirements[/B][/SIZE]
[LIST]
[*]Paper/Spigot 26.2+, Java 25
[*][B]Required:[/B] Vault (economy interoperability)
[*][B]Optional:[/B] PlaceholderAPI (placeholders)
[*]Vote rewards work out of the box (built-in v1 + v2 listener) — NuVotifier is only needed if you'd rather keep using an existing install of it instead
[/LIST]

[SIZE=5][B]Configuration[/B][/SIZE]
See [I]config.yml[/I] for prices, teleport-safety tuning (countdown, mob radius, PvP cooldown), storage backend (YAML/MySQL), feature toggles, the [I]economy.enabled[/I] switch (turn it off to use an external economy plugin via Vault instead), voting site links, and the web dashboard. See [I]messages.yml[/I] for all player-facing text and the currency name.
