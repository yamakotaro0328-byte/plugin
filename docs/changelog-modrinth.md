## Update: Warps, Daily Reward, Vote Upgrades, Private Messages & AFK (1.6.0)

Everything added since the Donations & Vote Rewards update.

**New**
- **Warps** — `/setwarp <name>` / `/delwarp <name>` (admin) manage shared warp points; players use `/warp <name>` (`/warp` alone lists them) or the Warps tile in `/menu`. Priced by distance, with the same confirmation and teleport safety checks as `/spawn`.
- **Daily reward** — `/daily` pays out once every 24 hours, with a streak bonus for claiming on consecutive days (`daily-reward` in config.yml).
- **Money item** — `/ecoitem give <player> <amount> [quantity]` (admin) hands out money as a physical item that can be dropped, traded or stored in a chest; right-clicking redeems the whole stack.
- **Bigger GUI menu** — `/menu` now covers home / spawn / warps / tpa / balance / pay / leaderboard / daily / donate / vote, and shows an incoming tpa request so it can be answered with a click. A menu item is handed out automatically and opens the menu on right-click (`features.menu-item`).
- **Votifier protocol v2 (token)** — the built-in listener now accepts both v1 (RSA key) and NuVotifier's v2. Most voting sites only support v2: give them the `tokens.default` value from `plugins/EcoTP/votifier-tokens.yml`.
- **Voting-site links** — list your sites under `vote-reward.sites` and the Vote tile in `/menu` shows them as clickable links in chat.
- **/votetop** — every vote is now counted; shows the top voters and your own total.
- **Vote milestones** — reaching a configured total (e.g. 10 / 50 / 100 votes) pays an extra one-time bonus, announced server-wide (`vote-reward.milestones`).
- **/msg and /reply** — private messages (aliases /tell, /w, /pm, /r). /reply answers whoever you were last talking with, from either side.
- **AFK** — `/afk`, or automatically after `afk.auto-seconds` (default 300) of no activity. [AFK] tab-list tag, a heads-up when you /msg someone AFK, and an optional AFK kick (`afk.kick-seconds`; exempt with `ecotp.afk.kickexempt`).
- **Clickable chat links** — http(s):// and www. URLs typed in chat become clickable links, regardless of each player's client settings.
- **Built-in chat format** (optional, off by default) — prefix + name + message, meant to replace a separate chat-formatting plugin.
- **/roma** — toggle automatic romaji → Japanese conversion of your own chat.
- **Web dashboard** (optional, off by default) — look up and adjust balances from a browser, with multiple staff logins (`web` in config.yml).
- New placeholders: `%ecotp_votes%`, `%ecotp_afk%`.

**Fixes**
- Daily and vote rewards could silently fail to pay out.
- A vote the site retried after a timeout could be paid twice.
- Voting on several sites while offline only paid one reward — every queued vote is now paid on next login.
- Voting on two different sites within 60 seconds had the second vote discarded as a "retry" — the retry guard is now per site.
- Offline votes are now logged to the console when they arrive.
- Some messages ignored the bundled defaults when missing from an older messages.yml.

**Upgrading:** config.yml is only generated on a fresh install. On an existing server, copy the new sections (`afk`, `daily-reward`, `menu-item`, `web`, `vote-reward.sites` / `top-limit` / `milestones`) from the bundled config.yml if you want to change them. Everything works with built-in defaults; milestone bonuses stay **off** until you add `milestones` yourself.

---

## アップデート内容: ワープ・デイリー・投票強化・個人チャット・放置(AFK)(1.6.0)

寄付・投票報酬のアップデート以降に追加されたもの全部です。

**新機能**
- **ワープ** — 管理者が`/setwarp <名前>`・`/delwarp <名前>`で全員共通のワープ地点を管理し、プレイヤーは`/warp <名前>`(`/warp`だけで一覧)や`/menu`の「ワープ」から移動できます。料金は距離制で、確認・テレポート安全条件は`/spawn`と同じです。
- **デイリーボーナス** — `/daily`で24時間に1回報酬を受け取れ、連続で受け取ると連続日数ボーナスが付きます(config.ymlの`daily-reward`)。
- **お金アイテム** — `/ecoitem give <プレイヤー名> <金額> [個数]`(管理者用)でお金をアイテムとして配布。ドロップ・トレード・チェスト保管ができ、右クリックでスタックごと換金できます。
- **GUIメニューの拡張** — `/menu`でホーム・スポーン・ワープ・tpa・所持金・送金・ランキング・デイリー・寄付・投票を操作でき、届いているtpaリクエストにもクリックで応答できます。メニューアイテムが自動配布され、右クリックでメニューが開きます(`features.menu-item`)。
- **Votifierプロトコルv2(トークン方式)** — 内蔵リスナーがv1(鍵方式)に加えてNuVotifierのv2にも対応。v2のみ対応の投票サイトが多いので、`plugins/EcoTP/votifier-tokens.yml`の`tokens.default`の値をサイト側に設定してください。
- **投票サイトのリンク** — `vote-reward.sites`に投票サイトを登録すると、`/menu`の投票タイルからクリックで開けるリンクがチャットに表示されます。
- **/votetop** — 投票回数を記録するようにし、上位の投票者と自分の累計投票数を表示します。
- **累計投票ボーナス** — 設定した累計回数(例: 10回・50回・100回)に達すると追加ボーナスが付与され、全体に通知されます(`vote-reward.milestones`)。
- **/msg・/reply** — 個人チャット(エイリアス /tell・/w・/pm・/r)。/replyは送った側・送られた側どちらからでも直前の相手に返信できます。
- **放置(AFK)** — `/afk`、または`afk.auto-seconds`(デフォルト300秒)操作が無いと自動で放置状態に。タブリストに[AFK]表示、放置中の人に/msgすると送信者に通知、放置キック(`afk.kick-seconds`、対象外にする権限`ecotp.afk.kickexempt`)にも対応。
- **チャットURLの自動リンク化** — チャットに書かれたhttp(s)://やwww.のURLを、クライアント設定に関係なくクリックで開けるリンクにします。
- **チャット整形**(任意・デフォルト無効) — 接頭辞+名前+本文。別のチャット装飾プラグインの置き換え用です。
- **/roma** — 自分のチャットのローマ字→日本語自動変換を切り替えられます。
- **Webダッシュボード**(任意・デフォルト無効) — ブラウザから残高を確認・変更できます。スタッフごとのログインにも対応(config.ymlの`web`)。
- 新しいプレースホルダー: `%ecotp_votes%`、`%ecotp_afk%`。

**修正**
- デイリー報酬・投票報酬が支払われないことがあった不具合を修正。
- 投票サイトがタイムアウト後に再送した投票が二重に支払われることがあった不具合を修正。
- オフライン中に複数のサイトで投票しても報酬が1回分しか付与されなかった不具合を修正(次回ログイン時に全部まとめて付与)。
- 60秒以内に別々のサイトで投票すると、2つ目が「再送」とみなされて捨てられていた不具合を修正(再送判定をサイトごとに)。
- オフライン中に届いた投票がコンソールに記録されるようにしました。
- 古いmessages.ymlに無い項目で、同梱のデフォルト文言が使われないことがあった不具合を修正。

**アップデート時の注意:** config.ymlが生成されるのは新規導入時のみです。既存のサーバーで設定を変えたい場合は、同梱のconfig.ymlから新しい項目(`afk`、`daily-reward`、`menu-item`、`web`、`vote-reward.sites`・`top-limit`・`milestones`)をコピーしてください。追加しなくても既定値で動作しますが、累計投票ボーナスは`milestones`を自分で追加するまで**無効**のままです。
