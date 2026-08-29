[SIZE=5][B]Update: Donations & Vote Rewards[/B][/SIZE]

[B]New: /donate[/B]
[LIST]
[*][I]/donate <player> <amount>[/I] — send money to another player as a donation. Works just like [I]/pay[/I] (same confirmation step, refunded automatically if the deposit somehow fails), but on success it broadcasts a thank-you message to the whole server.
[*]The [I]recipient[/I] controls what that broadcast says: [I]/donatemessage <text>[/I] sets your own personal thank-you message (placeholders: [I]{player}[/I] = the donor's name, [I]{amount}[/I] = the amount). [I]/donatemessage reset[/I] goes back to the default template in messages.yml.
[*]Toggle the whole feature with [I]features.donate: true/false[/I] in config.yml, same as any other command.
[/LIST]

[B]New: Vote Rewards — no extra plugin required[/B]
[LIST]
[*]EcoTP now ships its own built-in Votifier(V1)-protocol-compatible listener, so voting sites can point directly at your server — [B]no NuVotifier or any other vote plugin needed[/B]. On first startup it generates an RSA keypair under [I]plugins/EcoTP/votifier-rsa/[/I]; give voting sites the contents of [I]public.key[/I] plus your server's host and port ([I]votifier.port[/I], default [B]8192[/B]).
[*]Already running NuVotifier for other plugins? EcoTP also auto-detects it (via [I]vote-reward[/I]) so you don't have to change anything — just set [I]votifier.enabled: false[/I] in config.yml to avoid a port conflict with it.
[*]Either way, when a player votes they're rewarded with currency (default [B]1000[/B], set via [I]vote-reward.amount[/I]) and a server-wide broadcast announces it.
[*]Voted while offline? The reward is queued and paid out automatically the next time that player joins — nothing is lost.
[*]Turn off rewards entirely with [I]vote-reward.enabled: false[/I], or just the built-in listener with [I]votifier.enabled: false[/I].
[/LIST]

Both features work out of the box with the defaults above; nothing needs to be enabled manually beyond what's already set in config.yml.
