# EcoTP

Minecraft 用の**単独で動作する経済プラグイン** (Spigot / Paper)。
Essentials など外部の経済プラグインは不要です。所持金はこのプラグイン自身が管理し、
Home / Spawn / TPA / TPHere をお金で行う機能に加えて、`/balance`・`/pay`・`/eco`・`/baltop`・GUIメニューも内蔵しています。

**Vault は必須**です (このプラグインの経済を他プラグインに公開するために使われ、
入っていないとEcoTP自体が起動しません)。PlaceholderAPI は任意 (ソフト依存) で、
入っていなくてもこのプラグインの全機能は動作します。

[![Build](https://github.com/yamakotaro0328-byte/plugin/actions/workflows/build.yml/badge.svg)](https://github.com/yamakotaro0328-byte/plugin/actions/workflows/build.yml)

## 特徴

- 所持金はこのプラグインが独自に保持 (YAML または MySQL)。Essentials 不要。
  `economy.enabled: false` にすれば、代わりに Vault 経由で外部の経済プラグイン (Essentials 等)
  をそのまま使うこともできる (デフォルトは独自経済が ON)。
- **Essentials からの自動移行**: プレイヤーが初めてこのプラグインに登録されるとき、
  `plugins/Essentials/userdata/<uuid>.yml` に残高が見つかればそれを初期所持金として引き継ぐ
  (Essentials プラグイン自体が入っていなくても、旧データフォルダが残っていれば移行可能)。
- Vault (必須) にこの経済を `Economy` サービスとして公開し、ショップ等の
  他プラグインからも利用可能にする (`ServicePriority.Highest` で登録)。
- PlaceholderAPI が導入されていれば `%ecotp_balance%` 等のプレースホルダーを自動登録。
- テレポート前に安全確認・詠唱時間があり、悪用 (戦闘中の逃げテレポ等) を防止する。詠唱中は
  ボスバーでカウントダウンを表示し、成功時には効果音とパーティクルが鳴る (どちらも無効化可能)。
- `/home`・`/sethome`・`/spawn`・`/tpa`・`/tphere`・`/pay`・`/baltop`・`/menu` は
  `config.yml` の `features.*` で個別にオン/オフできる (デフォルトすべて ON)。
- `/tpa`・`/tphere`・`/pay`・`/eco` のプレイヤー名、`/home`・`/sethome`・`/delhome` の
  ホーム名はタブ補完に対応。
- 全メッセージ・通貨単位は `messages.yml` で自由にカスタマイズ可能。
  デフォルトは英語 (`config.yml` の `language: en`) で、日本語版も同梱 (`language: ja`)。
- `/menu` から、コマンドを覚えていなくてもGUIで一通りの操作ができる。
- GitHub Actions で push のたびに自動ビルドし、jar を Artifact として保存する。
- Modrinth 掲載用の説明文 (英語・日本語) を `docs/modrinth-en.md` / `docs/modrinth-ja.md` に同梱。

## テレポートコマンドと安全条件

`/home`・`/spawn`・`/tpa`・`/tphere` でテレポートするには、次をすべて満たす必要があります
(`config.yml` の `teleport-safety` で変更可能)。

1. 設定範囲内 (デフォルト10ブロック) に敵対Mobがいない
2. PvPクールダウン中ではない (デフォルト、直近の対人戦闘から15秒間)
3. 詠唱時間 (デフォルト5秒) の間、開始地点から動かない (視点移動だけでは解除されない)
4. テレポート先と同じディメンションにいる

待機中に移動した場合や、敵対Mob・PvP状態を検知した場合はテレポートをキャンセルします
(この場合、課金もされません)。`/tpa`・`/tphere` の詠唱は、リクエストと料金の両方が
承認されたあとに始まります。

## テレポート料金

初期設定では100ブロックにつき¥1、最低料金は¥100です。3次元の直線距離を使い、端数を切り上げます。

```
料金 = max(100, ceil(距離 ÷ 100))
```

| 距離 | 料金 |
| --- | --- |
| 320ブロック | ¥100 |
| 10,000ブロック | ¥100 |
| 420,000ブロック | ¥4,200 |

`/home`・`/spawn`・`/tpa` は**テレポートするプレイヤー**が支払います。
`/tphere` は**着払い**となり、**呼ばれたプレイヤー**が支払います (実際に移動する側が常に支払う、という点は共通です)。

料金の確認は、チャットのボタン・**同じコマンドの再実行**・`/ok` または `/accept` のいずれでも承認できます。
初期設定の確認期限は30秒です。テレポートに失敗した場合は請求されず、実行処理で失敗した場合
(相手がオフラインになった等) は課金前なので返金の必要もなく、万一入金側の処理だけ失敗した場合
(`/pay`) は自動で払い戻します。

`/sethome` は移動を伴わないため、上記の安全条件の対象外です (料金は距離ではなく使用回数で決まります:
1回目1000円、以降1000円ずつ上昇)。

## ホーム

- ホーム名は数字・日本語・英字のみ、16文字以内で指定できます (例: `/sethome 拠点`, `/home base2`)。
- `/home [名前]` `/sethome [名前]` : 名前を省略すると `home` という名前として扱われます。
- `/delhome <名前>` : 自分のホームを削除します (無料。`/sethome` の使用回数カウントはリセットされません)。
- `/homes` : 自分が設定しているホームの一覧を表示します (無料)。
- プレイヤーごとのホーム上限数は `config.yml` の `homes.max-per-player` で変更できます (デフォルト3個)。
  上限に達していても、既存の名前を上書きすることはできます。
- `storage.type: mysql` にすると、残高だけでなくホームも MySQL に保存され、複数サーバー間で共有できます。

## 経済コマンド

- `/balance` (`/bal`, `/money`) : 自分の所持金を確認 (`/balance <プレイヤー名>` で他人も確認可能、`ecotp.balance.others` 権限が必要)
- `/pay <プレイヤー名> <金額>` : 送金 (支払い承認が必要)
- `/eco give|take|set <プレイヤー名> <金額>` : 管理者用の所持金操作 (`ecotp.admin` 権限、デフォルトOP)
- `/baltop` : 所持金ランキングを表示 (人数は `config.yml` の `baltop-limit`)
- `/ecotp reload` : `config.yml` と `messages.yml` を再読み込み (`ecotp.admin` 権限、デフォルトOP)。
  `storage.type` と `economy.enabled` はサーバー起動時にしか決まらないため、これらを変更した場合は
  再起動が必要です。`language` を変更した場合は、このコマンド (または再起動) で `messages.yml` が
  その言語のテンプレートから再生成されます (それまでの編集内容は上書きされます)。

## GUIメニュー

`/menu` (エイリアス `/ecomenu`) を実行すると、以下をクリックだけで操作できるメニューが開きます。

- ホームへワープ / ホームを設定 / スポーンへワープ
- テレポートリクエスト送信 (`/tpa`) / 相手を呼び出す (`/tphere`、オンラインプレイヤーの頭アイテムから選択)
- 所持金確認 / 所持金ランキング
- 送金 (プレイヤーを選んだ後、金額はチャットで入力。`cancel` と入力すると中断できる)

GUIから選んだ操作も、通常のコマンドと全く同じ確認・安全条件・権限チェックを経由するので、
挙動や支払い承認は `/home` 等を直接打った場合と変わりません。

## 支払いの承認 (チャットクリック / 再実行 / 統合版対応)

料金が発生する操作を実行すると、チャットに `[承諾する]` / `[キャンセル]` のクリック可能なボタンが表示されます。
次のいずれの方法でも承認できます。

- チャットのボタンをクリックする
- 同じコマンドをもう一度実行する (例: `/home base` を2回実行する)
- `/accept` または `/ok` と入力する (`/accept cancel` でキャンセル)

クリックできない統合版 (Bedrock) プレイヤーは `/accept` (または `/ok`) を使ってください。
確認は `config.yml` の `confirmation-timeout-seconds` (デフォルト30秒) で自動的にタイムアウトします。

`/tpa`・`/tphere` は2段階の承諾になっています。まずリクエストを受け取った相手が
`[承諾する]` / `[拒否する]` ボタン (または `/tpaccept` / `/tpdeny`、これらは元々統合版でも
入力可能です) でリクエスト自体を承諾します。相手が承諾すると、今度は**実際に移動して
支払う側**に「本当にテレポートしますか？(概算金額)」という支払い承認が改めて表示されます
(`/tpa` なら要求した側、`/tphere` なら呼ばれた側)。支払いの確認待ちが無ければ `/accept` は
テレポートリクエストの承諾としても扱われます。相手が拒否・タイムアウト・支払い承認の
キャンセル・詠唱中のキャンセルをした場合は課金されません。
リクエストを送った側は `/tpacancel` で、相手の応答を待たずに自分から取り消せます。

## 残高の保存先 (YAML / MySQL)

`config.yml` の `storage.type` で切り替えられます。

```yaml
storage:
  type: yaml   # または mysql
  mysql:
    host: localhost
    port: 3306
    database: ecotp
    username: root
    password: ""
    table-prefix: "ecotp_"
```

複数サーバー間で残高を共有したい場合は `mysql` を使ってください。MySQL の JDBC ドライバーは
プラグインの jar に同梱されているため、別途ドライバーを用意する必要はありません
(`storage.type: mysql` にすると、残高とホームの両方が MySQL に保存されます)。

## 独自経済のオン/オフ

`config.yml` の `economy.enabled` (デフォルト `true`) で切り替えられます。

```yaml
economy:
  enabled: true   # false にすると、Vault 経由で外部の経済プラグインを使う
```

`false` にすると、このプラグインは自前で残高を持たず、Vault に登録されている他の経済プラグイン
(EssentialsX 等) の `Economy` をそのまま利用します。この場合 Vault と経済プラグインの両方の導入が
必須で、`/baltop` は使用できません (Vault の API には「全員のランキングを取得する」手段が無いため)。

## 機能ごとのオン/オフ

`config.yml` の `features.*` で、`/home`・`/sethome`・`/spawn`・`/tpa`・`/tphere`・`/pay`・`/baltop`・`/menu`
を個別に無効化できます (デフォルトすべて `true`)。無効化されたコマンドは実行時にその旨のメッセージを返します。

## 言語 (messages.yml)

`config.yml` の `language` (デフォルト `en`) で、初回生成される `messages.yml` の言語を選べます
(`en` または `ja`)。同梱の `messages_en.yml` / `messages_ja.yml` がそれぞれのベースになり、
一度 `plugins/EcoTP/messages.yml` が生成された後は自由に編集・翻訳できます。

## 導入方法

1. **Vault を導入する (必須)**。入っていないとEcoTPはロードされません。
2. 本プラグインをビルドし (`mvn package`)、生成された `target/ecotp-plugin-1.0.0.jar` を `plugins/` に入れる。
   (GitHub Actions が push のたびに自動でビルドし、Artifact として jar を公開しています)
3. サーバーを再起動する。`plugins/EcoTP/config.yml` で料金や初期所持金、`plugins/EcoTP/messages.yml`
   で文言・通貨単位を調整できる。
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

`ecotp.player` (デフォルト全員) を持っていれば、基本的なプレイヤー機能
(home/sethome/spawn/tpa/tphere/pay/menu) がまとめて使えます。個別に制限したい場合は、
`ecotp.player` を外したうえで必要な個別権限だけを付与してください。

| 権限 | デフォルト | 説明 |
| --- | --- | --- |
| `ecotp.player` | true | 基本プレイヤー機能をまとめて許可する親権限 |
| `ecotp.home` | (親経由) | `/home`, `/homes` を使用できる |
| `ecotp.sethome` | (親経由) | `/sethome` を使用できる |
| `ecotp.spawn` | (親経由) | `/spawn` を使用できる |
| `ecotp.setspawn` | op | `/setspawn` を使用できる |
| `ecotp.tpa` | (親経由) | `/tpa` を使用できる |
| `ecotp.tphere` | (親経由) | `/tphere` を使用できる |
| `ecotp.pay` | (親経由) | `/pay` を使用できる |
| `ecotp.menu` | (親経由) | `/menu` を使用できる |
| `ecotp.balance.others` | op | 他人の `/balance` を確認できる |
| `ecotp.admin` | op | `/eco` と `/ecotp reload` を使用できる |

## CI (GitHub Actions)

`.github/workflows/build.yml` が push / PR のたびに JDK 25 で `mvn package` を実行し、
成果物 (`ecotp-plugin-*.jar`) を Actions の Artifact としてアップロードします。
ローカルにビルド環境が無くても、GitHub 上でコンパイルが通るか確認できます。
