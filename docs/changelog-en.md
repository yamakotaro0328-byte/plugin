[SIZE=5][B]Update: Donations & Vote Rewards[/B][/SIZE]

[B]New: /donate[/B]
[LIST]
[*][I]/donate <player> <amount>[/I] — send money to another player as a donation. Works just like [I]/pay[/I] (same confirmation step, refunded automatically if the deposit somehow fails), but on success it broadcasts a thank-you message to the whole server.
[*]The [I]recipient[/I] controls what that broadcast says: [I]/donatemessage <text>[/I] sets your own personal thank-you message (placeholders: [I]{player}[/I] = the donor's name, [I]{amount}[/I] = the amount). [I]/donatemessage reset[/I] goes back to the default template in messages.yml.
[*]Toggle the whole feature with [I]features.donate: true/false[/I] in config.yml, same as any other command.
[/LIST]

[B]New: Vote Rewards[/B]
[LIST]
[*]If you run [B]NuVotifier[/B] (or any Votifier-API-compatible plugin), EcoTP now detects it automatically — no extra dependency to install, nothing to configure beyond what you already have pointing your voting sites at Votifier.
[*]When a player votes, they're rewarded with currency (default [B]1000[/B], set via [I]vote-reward.amount[/I] in config.yml) and a server-wide broadcast announces it.
[*]Voted while offline? The reward is queued and paid out automatically the next time that player joins — nothing is lost.
[*]Turn it off entirely with [I]vote-reward.enabled: false[/I].
[/LIST]

Both features work out of the box with the defaults above; nothing needs to be enabled manually beyond what's already set in config.yml.
