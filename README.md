# EcoTP

Home / Spawn / TP / TPA をお金で行うエコノミープラグイン (Spigot / Paper 用)。
[Vault](https://www.spigotmc.org/resources/vault.34315/) 経由で経済プラグイン (EssentialsX など) と連携します。

## 料金

| コマンド | 料金 |
| --- | --- |
| `/home` | 100円 / 回 |
| `/sethome` | 1000円 (1回目)、以降 1000円ずつ上昇 (2回目 2000円, 3回目 3000円 ...) |
| `/spawn` | 100円 / 回 |
| `/setspawn` | 無料 (`ecotp.setspawn` 権限が必要、デフォルトOPのみ) |
| `/tp <プレイヤー>` | 1ブロックあたり1円 (相手の場所へ即座にテレポート) |
| `/tpa <プレイヤー>` | 1ブロックあたり1円 (相手が承諾した場合のみ課金・テレポート) |

金額は `config.yml` の `costs` セクションで変更できます。

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

1. サーバーに [Vault](https://www.spigotmc.org/resources/vault.34315/) と、EssentialsX などの経済プラグインを導入する。
2. 本プラグインをビルドし (`mvn package`)、生成された `target/ecotp-plugin-1.0.0.jar` を `plugins/` に入れる。
3. サーバーを再起動する。`plugins/EcoTP/config.yml` で料金などを調整できる。

## 権限

| 権限 | デフォルト | 説明 |
| --- | --- | --- |
| `ecotp.home` | true | `/home` を使用できる |
| `ecotp.sethome` | true | `/sethome` を使用できる |
| `ecotp.spawn` | true | `/spawn` を使用できる |
| `ecotp.setspawn` | op | `/setspawn` を使用できる |
| `ecotp.tp` | true | `/tp` を使用できる |
| `ecotp.tpa` | true | `/tpa` を使用できる |
