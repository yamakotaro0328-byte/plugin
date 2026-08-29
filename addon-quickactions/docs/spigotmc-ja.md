[CENTER][SIZE=6][B]EcoTP-QuickActions[/B][/SIZE][/CENTER]
[CENTER][B]バニラのクイックアクション(Gキー)メニュー、天気投票、GUIアドミンショップを追加。[/B][/CENTER]

[B]EcoTP[/B]用の単独動作アドオン(EcoTPが無くても導入できますが、EcoTPと組み合わせて使うことを想定しています)。Paper専用 — 新しいバニラのDialog機能(1.21.7以降)を使用しているため、Spigot/Bukkitでは動作しません。

[SIZE=5][B]主な特徴[/B][/SIZE]
[LIST]
[*][B]クイックアクション(Gキー)[/B] — ゲーム内でGキーを押すと、ワンクリックのボタンが並んだダイアログが開きます: Home、Set Home、Spawn、Balance、ランキング、Menuへのショートカット(TPA/TPHere/Pay)、天気投票、アドミンショップ、プレイヤーショップ(一覧・自分の出品管理)。[I]/quickmenu[/I]で同じダイアログを手動で開くこともできます。
[*][B]天気投票[/B] — [I]/weathervote clear[/I]または[I]/weathervote rain[/I](エイリアス[I]/wv[/I])で投票を開始。そのワールドのオンライン人数に対して十分な賛成(割合は設定可能、デフォルト50%)が集まると天気が変わります。制限時間(設定可能)以内に集まらなければ不成立。投票ごとにプレイヤーへのクールダウンもあります。
[*][B]完全GUI駆動のアドミンショップ[/B] — [I]/adminshop[/I]でチェストGUIを開き、プレイヤーは左クリックで購入・右クリックで売却(シフトクリックでスタック/所持している全個数)できます。[I]/adminshop admin[/I]で編集モードを開き、アイテムを手に持って空きスロットをクリックすると出品、出品済みのアイテムを左クリックで購入価格、右クリックで売却価格を設定(チャットに数値を入力するだけ)、シフトクリックで削除できます。在庫管理は無く、一般的なアドミンショップと同様に常に無限の在庫・資金として扱われます。
[*][B]プレイヤー間ショップ[/B] — [I]/pshop browse[/I](エイリアス[I]/ps[/I])で全プレイヤーの出品を1つのGUIにまとめて表示。左クリックで1個購入、シフト+左クリックで残り在庫を全部購入でき、支払いは購入者から出品者へ直接渡ります。[I]/pshop my[/I]では自分の出品を管理: アイテムを手に持って空きスロットをクリックし、チャットで数量と単価を入力するだけで出品完了。出品済みのアイテムはクリックで取り下げ、アイテムが返却されます。アドミンショップと違い、在庫は出品された分だけの有限在庫です。[I]/pshop history[/I]で自分の取引履歴(購入・売却どちらも)を確認できます。
[*][B]完全日英対応[/B] — すべてのメッセージは[I]config.yml[/I]にまとまっており(デフォルトは英語、日本語版も同梱)、EcoTP本体と同じ[I]language: en/ja[/I]の設定方式です。
[/LIST]

[SIZE=5][B]コマンド一覧[/B][/SIZE]
[CODE]/quickmenu
/weathervote <clear|rain> (エイリアス /wv)
/adminshop
/adminshop admin
/pshop browse|my|history (エイリアス /ps)[/CODE]

[SIZE=5][B]権限[/B][/SIZE]
[LIST]
[*]ecotpqa.quickmenu (デフォルト: true)
[*]ecotpqa.weathervote (デフォルト: true)
[*]ecotpqa.adminshop (デフォルト: true)
[*]ecotpqa.adminshop.admin (デフォルト: op)
[*]ecotpqa.playershop (デフォルト: true)
[/LIST]

[SIZE=5][B]動作環境[/B][/SIZE]
[LIST]
[*]Paper 26.2以降(Paper専用。Dialog APIはSpigot/Bukkitには存在しません)
[*][B]任意:[/B] Vault + EcoTPなどの経済プラグイン(無い場合、購入/売却は「経済が利用できません」と表示されるだけになります)
[*][B]任意:[/B] EcoTP(クイックアクションダイアログのHome/Spawn/Menu等のボタンは単にEcoTPのコマンドを実行するだけなので、無い場合は何も起きません)
[/LIST]

[SIZE=5][B]設定[/B][/SIZE]
天気投票の制限時間・必要割合・クールダウン、アドミンショップのオン/オフとGUIサイズ、プレイヤー向けの全メッセージ(英語・日本語)は[I]config.yml[/I]で設定できます。
