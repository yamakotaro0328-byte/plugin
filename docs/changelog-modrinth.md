## Update: Donations & Vote Rewards

### New: `/donate`
- `/donate <player> <amount>` — send money to another player as a donation. Works just like `/pay` (same confirmation step, refunded automatically if the deposit somehow fails), but on success it broadcasts a thank-you message to the whole server.
- The **recipient** controls what that broadcast says: `/donatemessage <text>` sets your own personal thank-you message (placeholders: `{player}` = the donor's name, `{amount}` = the amount). `/donatemessage reset` goes back to the default template in messages.yml.
- Toggle the whole feature with `features.donate: true/false` in config.yml, same as any other command.

### New: Vote Rewards — no extra plugin required
- EcoTP now ships its own built-in Votifier (V1)-protocol-compatible listener, so voting sites can point directly at your server — **no NuVotifier or any other vote plugin needed**. On first startup it generates an RSA keypair under `plugins/EcoTP/votifier-rsa/`; give voting sites the contents of `public.key` plus your server's host and port (`votifier.port`, default **8192**).
- Already running NuVotifier for other plugins? EcoTP also auto-detects it (via `vote-reward`) so you don't have to change anything — just set `votifier.enabled: false` in config.yml to avoid a port conflict with it.
- Either way, when a player votes they're rewarded with currency (default **1000**, set via `vote-reward.amount`) and a server-wide broadcast announces it.
- Voted while offline? The reward is queued and paid out automatically the next time that player joins — nothing is lost.
- Turn off rewards entirely with `vote-reward.enabled: false`, or just the built-in listener with `votifier.enabled: false`.

Both features work out of the box with the defaults above; nothing needs to be enabled manually beyond what's already set in config.yml.

---

## アップデート内容: 寄付・投票報酬

### 新機能: `/donate`(寄付)
- `/donate <プレイヤー名> <金額>` — 他のプレイヤーへ「寄付」としてお金を送るコマンドです。`/pay` と同じ仕組み(確認ステップあり、入金に失敗した場合は自動で払い戻し)ですが、成功するとサーバー全体にお礼メッセージが流れます。
- このお礼メッセージの内容は**受け取る側**が決められます。`/donatemessage <メッセージ>` で自分専用の文言を設定できます(使えるプレースホルダー: `{player}` = 寄付した人の名前、`{amount}` = 金額)。`/donatemessage reset` で messages.yml のデフォルト文言に戻せます。
- 他の機能と同様、config.yml の `features.donate: true/false` で機能全体のオン/オフができます。

### 新機能: 投票報酬 — 別プラグイン不要
- EcoTP に Votifier(V1)プロトコル互換のリスナーを内蔵したため、**NuVotifier等の別プラグインを一切導入せず**に投票サイトから直接この鯖に投票を送れます。初回起動時に `plugins/EcoTP/votifier-rsa/` 以下にRSA鍵ペアが自動生成されるので、`public.key` の中身とサーバーのホスト・ポート(`votifier.port`、デフォルト**8192**)を投票サイト側に設定してください。
- 既に他プラグインのためにNuVotifierを使っている場合も、EcoTPが自動検出します(`vote-reward` 設定で共通)。特に変更は不要ですが、ポート競合を避けるため `votifier.enabled: false` にしておくと安全です。
- どちらの方式でも、プレイヤーが投票すると通貨報酬(デフォルト**1000**、`vote-reward.amount` で変更可)が付与され、サーバー全体に通知が流れます。
- オフライン中に投票された場合も報酬は保留され、次回ログイン時に自動で付与されます(取りこぼしはありません)。
- 報酬自体は `vote-reward.enabled: false`、内蔵リスナーだけなら `votifier.enabled: false` で無効化できます。

どちらの機能も上記のデフォルト設定でそのまま動作します。config.ymlで既に有効になっている以上、追加の作業は不要です。
