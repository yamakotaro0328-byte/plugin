[CENTER][SIZE=6][B]EcoJobs[/B][/SIZE][/CENTER]
[CENTER][B]20 jobs that pay players for everyday survival actions, with levels, prestige, and fully GUI-driven menus.[/B][/CENTER]

Standalone plugin (doesn't require EcoTP, but pays out through Vault the same way everything in this line does). Works with any Vault-compatible economy plugin.

[SIZE=5][B]Highlights[/B][/SIZE]
[LIST]
[*][B]20 jobs[/B] — Miner, Digger, Woodcutter, Farmer, Builder, Fisherman, Treasure Hunter, Hunter, Archer, Slayer, Warrior, Breeder, Tamer, Shearer, Beekeeper, Enchanter, Smelter, Crafter, Merchant, and Explorer — each with its own configurable action-reward table in [I]config.yml[/I]. Join up to [I]max-concurrent-jobs[/I] at once (default 3).
[*][B]Levels & prestige[/B] — every job levels up as you earn XP (the curve is fully configurable), with pay increasing per level. Once a job hits max level, [I]/jobs prestige <job>[/I] resets it to level 1 for a permanent extra pay bonus, announced server-wide.
[*][B]Level milestones[/B] — every job pays a one-time bonus (configurable levels, default 10/25/50/75/100) with a server-wide announcement and its own sound cue — and it triggers again each time you relevel through prestige.
[*][B]Anti-abuse block tracking[/B] — placing then instantly re-breaking a block never pays out for Miner/Digger/Woodcutter or Farmer's tall-plant harvesting; ordinary crops are exempt, since growing them back always takes real time anyway.
[*][B]Boosters & events[/B] — [I]/jobs booster start <job|all> <money multiplier> <xp multiplier> <minutes>[/I] runs a timed payout event, global or targeted at one job, and multiple boosters stack multiplicatively. One-click quick actions for the same thing live in the admin GUI too.
[*][B]Polished GUIs, not just chat[/B] — [I]/jobs menu[/I] opens a hub (My Jobs / Leaderboards / Settings, plus an Admin Panel shortcut for staff): a bordered job list with a player summary tile and a visual XP progress bar per job, a leaderboard browser with real player heads, and a personal Settings menu to toggle click sounds and the earnings action bar.
[*][B]Admin GUI[/B] — [I]/jobs admin[/I] lets you enable/disable any job and live-tune its pay multiplier per job, without ever touching config.yml, plus the same booster quick actions as the command.
[*][B]MySQL or YAML storage[/B] — share job progress across multiple servers pointed at the same database, or just use the zero-setup YAML default.
[*][B]PlaceholderAPI support[/B] — %ecojobs_level_<job>%, %ecojobs_xp_<job>%, %ecojobs_prestige_<job>%, %ecojobs_joined_<job>%, %ecojobs_total_level%, %ecojobs_top_<job>_<rank>_name% and more.
[*][B]Fully bilingual[/B] — every message lives in [I]config.yml[/I] (English by default, Japanese bundled), same [I]language: en/ja[/I] convention as EcoTP.
[/LIST]

[SIZE=5][B]Commands[/B][/SIZE]
[CODE]/jobs join|leave|list|stats|top|menu|info|prestige <job>
/jobs admin
/jobs booster start|stop|list
/jobs reload (alias /job)[/CODE]

[SIZE=5][B]Permissions[/B][/SIZE]
[LIST]
[*]ecojobs.use (default: true) — join/leave/prestige jobs and see your own stats
[*]ecojobs.top (default: true) — view job leaderboards
[*]ecojobs.bypass.maxjobs (default: false) — ignore max-concurrent-jobs
[*]ecojobs.admin (default: op) — /jobs reload, /jobs admin, /jobs booster
[/LIST]

[SIZE=5][B]Requirements[/B][/SIZE]
[LIST]
[*]Paper 26.2+ (Paper-only — the admin GUI's item-glint effect and the earnings action bar both use Paper-specific API)
[*][B]Optional:[/B] Vault + an economy plugin such as EcoTP (job levels/XP still work without it; money just isn't paid out)
[*][B]Optional:[/B] PlaceholderAPI (placeholders)
[/LIST]

[SIZE=5][B]Configuration[/B][/SIZE]
See [I]config.yml[/I] for every job's action-reward table, the leveling curve and prestige/milestone bonuses, max concurrent jobs, the storage backend (YAML/MySQL), and all player-facing messages in English and Japanese.
