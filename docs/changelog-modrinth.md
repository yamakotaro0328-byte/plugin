## EcoTP 1.6.0 — Warps, Vote Leaderboard, Private Messages & AFK

**New**
- **Warps** — `/setwarp <name>` and `/delwarp <name>` (admin) manage shared warp points; players use `/warp <name>` (`/warp` alone lists them) or the new Warps tile in `/menu`. Priced by distance with the same confirmation and teleport safety checks as `/spawn`.
- **/votetop** — every vote is now counted; shows the top voters and your own total.
- **Vote milestones** — reaching a configured total (default 10 / 50 / 100 votes) pays an extra one-time bonus on top of the normal reward, announced server-wide. Configure under `vote-reward.milestones`.
- **/msg and /reply** — private messages (aliases /tell, /w, /pm, /r). /reply answers whoever you were last talking with, from either side.
- **AFK** — `/afk`, or automatically after `afk.auto-seconds` (default 300) of no activity. [AFK] tab-list tag, a heads-up when you /msg someone AFK, and an optional AFK kick (`afk.kick-seconds`, `ecotp.afk.kickexempt` to exempt).
- **Built-in chat format** (optional, off by default) — prefix + name + message, meant to replace a separate chat-formatting plugin.
- **/roma** — toggle automatic romaji → Japanese conversion of your own chat.
- New placeholders: `%ecotp_votes%`, `%ecotp_afk%`.

**Fixes**
- Voting on several sites while offline only paid `one` reward — every queued vote is now paid on next login (existing pending votes carry over).
- Voting on two different sites within 60 seconds had the second vote thrown away as a "retry" — the retry guard is now per site.
- Offline votes are now logged to the console when they arrive.
- EcoTP failed to load on 26.2 servers ("Unsupported API version 26.3") — api-version is back to 26.2.
- Chat links could lose their click action when another chat plugin reformatted the message.

**Upgrading:** config.yml is only generated on a fresh install, so on an existing server copy the new `afk:` section and the `vote-reward.top-limit` / `vote-reward.milestones` keys from the bundled config.yml if you want to change them. Milestone bonuses stay **off** until you add `milestones` yourself; everything else works with built-in defaults.

---

## EcoTP 1.6.0 — ワープ・投票ランキング・個人チャット・放置(AFK)

**新機能**
- **ワープ** — 管理者が`/setwarp <名前>`・`/delwarp <名前>`で全員共通のワープ地点を管理し、プレイヤーは`/warp <名前>`(`/warp`だけで一覧)や`/menu`の新しい「ワープ」から移動できます。料金は距離制で、確認・テレポート安全条件は`/spawn`と同じです。
- **/votetop** — 投票回数を記録するようにし、上位の投票者と自分の累計投票数を表示します。
- **累計投票ボーナス** — 設定した累計回数(デフォルト10回・50回・100回)に達すると、通常の報酬に追加ボーナスが上乗せされ、全体に通知されます。`vote-reward.milestones`で設定できます。
- **/msg・/reply** — 個人チャット(エイリアス /tell・/w・/pm・/r)。/replyは送った側・送られた側どちらからでも直前の相手に返信できます。
- **放置(AFK)** — `/afk`、または`afk.auto-seconds`(デフォルト300秒)操作が無いと自動で放置状態に。タブリストに[AFK]表示、放置中の人に/msgすると送信者に通知、放置キック(`afk.kick-seconds`、対象外にする権限`ecotp.afk.kickexempt`)にも対応。
- **チャット整形**(任意・デフォルト無効) — 接頭辞+名前+本文。別のチャット装飾プラグインの置き換え用です。
- **/roma** — 自分のチャットのローマ字→日本語自動変換を切り替えられます。
- 新しいプレースホルダー: `%ecotp_votes%`、`%ecotp_afk%`。

**修正**
- オフライン中に複数のサイトで投票しても報酬が1回分しか付与されなかった不具合を修正(次回ログイン時に全部まとめて付与。既に保留中の投票も引き継がれます)。
- 60秒以内に別々のサイトで投票すると、2つ目が「再送」とみなされて捨てられていた不具合を修正(再送判定をサイトごとに)。
- オフライン中に届いた投票がコンソールに記録されるようにしました。
- 26.2のサーバーで「Unsupported API version 26.3」となりEcoTPが起動しなかった不具合を修正。
- 他のチャットプラグインがメッセージを整形し直すと、チャット内リンクのクリックが効かなくなることがあった不具合を修正。

**アップデート時の注意:** config.ymlが生成されるのは新規導入時のみです。既存のサーバーで設定を変えたい場合は、同梱のconfig.ymlから`afk:`セクションと`vote-reward.top-limit`・`vote-reward.milestones`をコピーしてください。累計投票ボーナスは`milestones`を自分で追加するまで**無効**のままです(それ以外は追加しなくても既定値で動作します)。
