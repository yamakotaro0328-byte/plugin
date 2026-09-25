[SIZE=5][B]EcoTP 1.6.0 — Warps, Vote Leaderboard, Private Messages & AFK[/B][/SIZE]

[B]New[/B]
[LIST]
[*][B]Warps[/B] — [I]/setwarp <name>[/I] and [I]/delwarp <name>[/I] (admin) manage shared warp points; players use [I]/warp <name>[/I] ([I]/warp[/I] alone lists them) or the new Warps tile in [I]/menu[/I]. Priced by distance with the same confirmation and teleport safety checks as [I]/spawn[/I].
[*][B]/votetop[/B] — every vote is now counted; shows the top voters and your own total.
[*][B]Vote milestones[/B] — reaching a configured total (default 10 / 50 / 100 votes) pays an extra one-time bonus on top of the normal reward, announced server-wide. Configure under [I]vote-reward.milestones[/I].
[*][B]/msg and /reply[/B] — private messages (aliases /tell, /w, /pm, /r). /reply answers whoever you were last talking with, from either side.
[*][B]AFK[/B] — [I]/afk[/I], or automatically after [I]afk.auto-seconds[/I] (default 300) of no activity. [AFK] tab-list tag, a heads-up when you /msg someone AFK, and an optional AFK kick ([I]afk.kick-seconds[/I], [I]ecotp.afk.kickexempt[/I] to exempt).
[*][B]Built-in chat format[/B] (optional, off by default) — prefix + name + message, meant to replace a separate chat-formatting plugin.
[*][B]/roma[/B] — toggle automatic romaji → Japanese conversion of your own chat.
[*]New placeholders: [I]%ecotp_votes%[/I], [I]%ecotp_afk%[/I].
[/LIST]

[B]Fixes[/B]
[LIST]
[*]Voting on several sites while offline only paid [I]one[/I] reward — every queued vote is now paid on next login (existing pending votes carry over).
[*]Voting on two different sites within 60 seconds had the second vote thrown away as a "retry" — the retry guard is now per site.
[*]Offline votes are now logged to the console when they arrive.
[*]EcoTP failed to load on 26.2 servers ("Unsupported API version 26.3") — api-version is back to 26.2.
[*]Chat links could lose their click action when another chat plugin reformatted the message.
[/LIST]

[B]Upgrading:[/B] config.yml is only generated on a fresh install, so on an existing server copy the new [I]afk:[/I] section and the [I]vote-reward.top-limit[/I] / [I]vote-reward.milestones[/I] keys from the bundled config.yml if you want to change them. Milestone bonuses stay [B]off[/B] until you add [I]milestones[/I] yourself; everything else works with built-in defaults.
