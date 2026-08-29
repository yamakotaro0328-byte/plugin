[CENTER][SIZE=6][B]EcoTP[/B][/SIZE][/CENTER]
[CENTER][B]距離制の料金でテレポートする、単独動作の経済プラグイン。[/B][/CENTER]

Essentials など他の経済プラグインは不要です。EcoTPが自前で残高を管理しつつ、Vault([B]必須[/B])経由でショップ等の他プラグインとも連携できます。

[SIZE=5][B]主な特徴[/B][/SIZE]
[LIST]
[*][B]独立した経済システム[/B] — 残高はEcoTP自身が保持します(デフォルトはYAML、複数サーバーで共有したい場合はMySQLも選択可)。Vaultは他プラグインとの連携のために必須です。
[*][B]Essentialsからの残高引き継ぎ[/B] — Essentialsから乗り換える際、プレイヤーが初めて確認されたタイミングで既存の残高を自動的に引き継ぎます。
[*][B]距離制のテレポート料金[/B] — [I]/home[/I]・[I]/spawn[/I]・[I]/tpa[/I]・[I]/tphere[/I]は実際の3次元距離に応じて課金されます: [CODE]max(最低料金, ceil(距離 ÷ ブロック単価))[/CODE]。すべて設定変更可能です。
[*][B]/tphere(着払い)[/B] — 相手を自分のもとへ呼び出すコマンド。実際に移動するのは相手なので、料金は相手が支払います。[I]/tpa[/I]はその逆で、自分が相手のもとへ移動し、自分が支払います。
[*][B]悪用防止のテレポート安全条件[/B] — テレポート完了前に、敵対Mobが近くにいないか・直近にPvPをしていないか・数秒間その場から動いていないか(視点移動のみなら中断されません)・目的地と同じディメンションにいるかを確認します。いずれかに違反すると中断され、課金もされません。
[*][B]複数の名前付きホーム[/B] — サーバー設定で許可された数だけホームを設定できます([I]/sethome 名前[/I]、[I]/home 名前[/I]、[I]/delhome 名前[/I]、一覧は[I]/homes[/I])。
[*][B]柔軟な支払い承認[/B] — チャットのクリックボタン、同じコマンドの再実行、[I]/accept[/I]または[I]/ok[/I](統合版対応)のいずれでも承認できます。
[*][B]GUIメニュー[/B] — [I]/menu[/I]でコマンドを覚えなくても操作できるクリック式メニューを開けます。
[*][B]細かくカスタマイズ可能[/B] — メッセージ文言や通貨単位はすべてmessages.ymlにまとまっており(デフォルトは英語、日本語版も同梱)、各機能や独自経済そのもののオン/オフも個別に設定できます。
[*][B]PlaceholderAPI対応[/B] — %ecotp_balance%、%ecotp_balance_formatted%、%ecotp_sethome_cost%。
[*][B]/donate(寄付)[/B] — 他のプレイヤーへ「寄付」としてお金を送るコマンド。成功するとサーバー全体にお礼メッセージが流れます。受け取る側は[I]/donatemessage <メッセージ>[/I](使えるプレースホルダー: {player}, {amount})で自分専用の文言に変更でき、[I]/donatemessage reset[/I]でデフォルトに戻せます。
[*][B]投票報酬[/B] — NuVotifier(または同等のVotifier互換プラグイン)を自動検出し、投票サイトで投票してくれたプレイヤーに報酬(デフォルト1000円、設定変更可)とサーバー全体への通知を付与します。投票時にオフラインだった場合は保留され、次回ログイン時に付与されます。
[/LIST]

[SIZE=5][B]コマンド一覧[/B][/SIZE]
[CODE]/home, /sethome, /delhome, /homes, /spawn, /setspawn, /tpa, /tphere,
/tpaccept, /tpdeny, /tpacancel, /accept (エイリアス /ok), /balance, /pay,
/eco, /baltop, /menu, /ecotp reload, /donate, /donatemessage[/CODE]

[SIZE=5][B]動作環境[/B][/SIZE]
[LIST]
[*]Paper/Spigot 26.2、Java 25
[*][B]必須:[/B] Vault(他プラグインとの経済連携)
[*][B]任意:[/B] PlaceholderAPI(プレースホルダー)
[*][B]任意:[/B] NuVotifierまたは同等のVotifier互換プラグイン(投票報酬) — 無くても他の機能には影響せず、投票報酬が発生しないだけです
[/LIST]

[SIZE=5][B]設定[/B][/SIZE]
料金・テレポート安全条件(待機時間、Mob検知半径、PvPクールダウン)・保存先(YAML/MySQL)・機能ごとのオン/オフ・独自経済のオン/オフ([I]economy.enabled[/I]をfalseにするとVault経由で外部の経済プラグインを利用)は[I]config.yml[/I]で、プレイヤー向けの文言と通貨単位は[I]messages.yml[/I]で設定できます。
