# EcoTP

Minecraft 用の**単独で動作する経済プラグイン** (Spigot / Paper)。
Essentials など外部の経済プラグインは不要です。所持金はこのプラグイン自身が管理し、
Home / Spawn / TP / TPA をお金で行う機能に加えて、`/balance`・`/pay`・`/eco` も内蔵しています。

Vault と PlaceholderAPI は**両方とも任意**(ソフト依存)です。導入されていれば追加で連携しますが、
入っていなくてもこのプラグインの全機能は動作します。

## 特徴

- 所持金はこのプラグインが独自に保持 (`plugins/EcoTP/balances.yml`)。Essentials 不要。
- **Essentials からの自動移行**: プレイヤーが初めてこのプラグインに登録されるとき、
  `plugins/Essentials/userdata/<uuid>.yml` に残高が見つかればそれを初期所持金として引き継ぐ
  (Essentials プラグイン自体が入っていなくても、旧データフォルダが残っていれば移行可能)。
- Vault が導入されていれば、この経済を `Economy` サービスとして公開し、ショップ等の
  他プラグインからも利用可能にする (`ServicePriority.Highest` で登録)。
- PlaceholderAPI が導入されていれば `%ecotp_balance%` 等のプレースホルダーを自動登録。

## 料金

| コマンド | 料金 |
| --- | --- |
| `/home` | 100円 / 回 |
| `/sethome` | 1000円 (1回目)、以降 1000円ずつ上昇 (2回目 2000円, 3回目 3000円 ...) |
| `/spawn` | 100円 / 回 |
| `/setspawn` | 無料 (`ecotp.setspawn` 権限が必要、デフォルトOPのみ) |
| `/tp <プレイヤー>` | 1ブロックあたり1円 (相手の場所へ即座にテレポート) |
| `/tpa <プレイヤー>` | 1ブロックあたり1円 (相手が承諾した場合のみ課金・テレポート) |
| `/pay <プレイヤー> <金額>` | 送金する金額そのもの |

金額は `config.yml` の `costs` セクションで変更できます。

## 経済コマンド

- `/balance` (`/bal`, `/money`) : 自分の所持金を確認 (`/balance <プレイヤー名>` で他人も確認可能、`ecotp.balance.others` 権限が必要)
- `/pay <プレイヤー名> <金額>` : 送金 (チャットクリック承諾が必要)
- `/eco give|take|set <プレイヤー名> <金額>` : 管理者用の所持金操作 (`ecotp.admin` 権限、デフォルトOP)

## 支払いの承諾 (チャットクリック / 統合版対応)

料金が発生する操作を実行すると、チャットに `[承諾する]` / `[キャンセル]` のクリック可能なボタンが表示されます。
クリックできない統合版 (Bedrock) プレイヤーのために、同じ操作をコマンドでも行えます。

- `/accept` : 保留中の支払いを承諾して実行する
- `/accept cancel` : 保留中の支払いをキャンセルする

確認は `config.yml` の `confirmation-timeout-seconds` (デフォルト30秒) で自動的にタイムアウトします。

`/tpa` はさらに相手プレイヤーの承諾も必要です。相手には `[承諾する]` / `[拒否する]` のボタンに加えて、
通常どおり `/tpaccept` / `/tpdeny` コマンドが使えます (これらは元々統合版でも入力可能です)。
相手が拒否・タイムアウトした場合は課金されません。

## 導入方法

1. 本プラグインをビルドし (`mvn package`)、生成された `target/ecotp-plugin-1.0.0.jar` を `plugins/` に入れる。
2. サーバーを再起動する。`plugins/EcoTP/config.yml` で料金や初期所持金を調整できる。
3. (任意) Vault を導入すると、他のプラグインからもこの経済を利用できるようになる。
4. (任意) PlaceholderAPI を導入すると、プレースホルダーが自動で使えるようになる。
5. Essentials から乗り換える場合、`plugins/Essentials/userdata` フォルダを残したまま
   このプラグインを導入すれば、プレイヤーが最初にサーバーへ参加したタイミングで
   自動的に残高が引き継がれる (`essentials-import.enabled: false` で無効化可能)。

## プレースホルダー (PlaceholderAPI)

| プレースホルダー | 内容 |
| --- | --- |
| `%ecotp_balance%` | 所持金 (数値のみ) |
| `%ecotp_balance_formatted%` | 所持金 (「1000円」のように整形) |
| `%ecotp_sethome_cost%` | 次に `/sethome` を使ったときの料金 |

## 権限

| 権限 | デフォルト | 説明 |
| --- | --- | --- |
| `ecotp.home` | true | `/home` を使用できる |
| `ecotp.sethome` | true | `/sethome` を使用できる |
| `ecotp.spawn` | true | `/spawn` を使用できる |
| `ecotp.setspawn` | op | `/setspawn` を使用できる |
| `ecotp.tp` | true | `/tp` を使用できる |
| `ecotp.tpa` | true | `/tpa` を使用できる |
| `ecotp.pay` | true | `/pay` を使用できる |
| `ecotp.balance.others` | op | 他人の `/balance` を確認できる |
| `ecotp.admin` | op | `/eco` を使用できる |
