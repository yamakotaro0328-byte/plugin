# Velodicord-Bridge

VelodicordはVelocityプロキシ専用のプラグインで、Fabricサーバー側には[Fabdicord](https://github.com/Nekozuki0509/Fabdicord)というMODが必要でしたが、これは**Paper/Spigotサーバー用の代わり**です。

Fabdicordと違い、独自のDiscord botは持ちません。**Discord botは1個(Velodicord側)だけ**で済むように、死亡・実績達成通知や`/pos`をWebSocket経由でVelodicordに送るだけの軽量な作りになっています。

## 導入手順

1. Paper/Spigotサーバーの`plugins`フォルダにこのプラグインを入れて再起動
2. `plugins/VelodicordBridge/config.yml`を編集
   - `server-name`: velocity.tomlに登録してあるこのサーバーの名前と完全に一致させる
   - `port-increment`: Velodicord側`config.json`の`WebSocketPortIncrement`と同じ値にする
3. Velodicord側の`config.json`で`PMType`を`2`(WebSocket)に設定する
   - **`PMType: 1`(Discord経由)はFabdicordの独自botが前提の方式なので、このプラグインでは使えません**
4. 両方を再起動すれば、Velodicordが自動でこのプラグインに接続します

## 提供する機能

- プレイヤー死亡通知(Discordの通知チャンネルへ + 他サーバーへのチャット中継)
- 実績/進捗達成通知(同上)
- `/pos [名前]` : 現在地をDiscordに共有

## Fabdicordから意図的に省いた機能

- 独自のDiscord bot・スラッシュコマンド(`/ch`, `/commandrole`, `/server`, `/admincommand`, `/ignorecommand`など) → Velodicord側の同名コマンドで代用してください
- プレイヤーのコマンド実行通知(ignorecommand/mineadmincommand) → Velodicord側と同様に廃止済み
- VOICEVOX読み上げ連携 → Velodicord側と同様に廃止済み
- Fake player(bot)のタブリスト追跡 → 対象がニッチなため省略
- Discordから特定のバックエンドサーバーへの任意コマンド実行 → 1 bot構成にする都合上、今回は未対応(必要であれば別途相談してください)
