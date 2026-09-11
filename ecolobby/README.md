# EcoLobby

Minecraft (Paper) 用の**ネットワークハブ/ロビープラグイン**。
Velocity・BungeeCord のような複数サーバー構成(プロキシ+バックエンド)を前提に、
ロビーサーバー1台分の「よくある機能一式」をこれ1つでまかないます。

[![Build](https://github.com/yamakotaro0328-byte/plugin/actions/workflows/build.yml/badge.svg)](https://github.com/yamakotaro0328-byte/plugin/actions/workflows/build.yml)

## 特徴

- **スポーン保護**: PvP・ブロック破壊/設置・アイテムドロップ/拾得・満腹度減少・落下ダメージを
  それぞれ個別にON/OFFできる (デフォルトすべて無効化=保護ON)。ロビーを荒らされないようにする。
- **二段ジャンプ**: 空中でジャンプキーをもう一度押すと前方+上方向にブーストする、よくあるロビー
  演出。クールダウンあり (デフォルト1秒)。地上での誤爆(誤って本物の飛行になる)は起きない作り。
- **サーバー選択メニュー**: 参加時に配られるコンパスを右クリックするとGUIが開き、
  クリックした先のサーバーへ自動的に転送される。
- **リンクメニュー**: 参加時に配られる本を右クリックすると、Discord・Webサイト等の
  クリック可能なリンクがチャットに表示される。
- **`/hub` (エイリアス `/lobby`)**: どのサーバーにいても実行でき、ロビーサーバー自身では
  スポーンへテレポート、それ以外のサーバーではロビーサーバーへ自動転送する。
  プロキシ側に追加のプラグインは不要 (Velocity・BungeeCordどちらも標準対応している
  `"BungeeCord"` プラグインメッセージチャンネル経由で送るだけのため)。
- **奈落(void)対策**: 落下してVOIDダメージを受けた場合、ダメージをキャンセルして
  スポーンへ戻す。
- **参加時リセット**: インベントリ・体力・満腹度・状態異常をクリアしてから
  スポーンへテレポートする (任意でON/OFF可能)。
- 1つの jar をロビーサーバーにも他のバックエンドサーバーにも導入できる。
  `is-lobby-server: false` にしたサーバーでは上記のロビー専用機能はすべて無効化され、
  `/hub` でロビーへ戻れる機能だけが動く。

## 導入方法

1. Velocity または BungeeCord でプロキシ構成を組んでいることが前提 (単体サーバーでは
   `/hub` のサーバー間転送機能が使えない)。
2. ロビーにしたいサーバーに本プラグインを導入し、`plugins/EcoLobby/config.yml` を編集する。
3. `is-lobby-server: true` のまま (デフォルト) にし、`/ecolobby setspawn` で
   ロビーのスポーン地点を設定する。
4. `config.yml` の `servers:` にプロキシ側の設定と同じサーバー名を、`links:` に
   表示したいリンクを記入する。
5. 他のバックエンドサーバー(サバイバル鯖など)にも同じプラグインを導入する場合は、
   そのサーバーの `config.yml` だけ `is-lobby-server: false` にする
   (これで `/hub` だけが動き、保護・二段ジャンプ等はそのサーバーでは何もしない)。

## コマンド

| コマンド | 説明 | 権限 |
| --- | --- | --- |
| `/hub` (`/lobby`) | ロビーへ戻る (ロビー本体ならテレポート、他サーバーなら転送) | `ecolobby.use` (デフォルト全員) |
| `/ecolobby reload` | `config.yml` を再読み込み | `ecolobby.admin` (デフォルトOP) |
| `/ecolobby setspawn` | 現在地をロビーのスポーン地点として設定 | `ecolobby.admin` (デフォルトOP) |

## 設定 (config.yml)

- `language`: メッセージの言語 (`en` / `ja`)
- `lobby-server-name`: プロキシ側で設定したロビーサーバーの名前と完全一致させる
- `is-lobby-server`: このサーバー自体がロビーかどうか (上記参照)
- `features.*`: 保護/二段ジャンプ/サーバーメニュー/リンクメニュー/奈落対策/参加時リセットを個別にON/OFF
- `protection.*`: スポーン保護の詳細な内訳
- `double-jump.velocity` / `double-jump.cooldown-seconds`: 二段ジャンプの強さと連発防止間隔
- `items.*`: サーバーメニュー(コンパス)・リンクメニュー(本)を配るホットバースロットとアイテム種類
- `servers:` / `links:`: メニューに表示するサーバー一覧・リンク一覧 (`/ecolobby reload` で再読込)
- `messages.en` / `messages.ja`: 全メッセージ文言

## 権限

| 権限 | デフォルト | 説明 |
| --- | --- | --- |
| `ecolobby.use` | true | `/hub`、サーバー/リンクメニュー、二段ジャンプを使用できる |
| `ecolobby.admin` | op | ロビーのスポーン設定・config再読込ができる |

## CI (GitHub Actions)

`.github/workflows/build.yml` の `build-ecolobby` ジョブが push のたびに
JDK 25 で `mvn package` を実行し、成果物 (`ecolobby-*.jar`) を Actions の
Artifact としてアップロードします。他のプラグインとは独立したMavenプロジェクトなので、
このプラグインのビルド失敗が他のプラグインのビルドに影響することはありません。
