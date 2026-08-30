[SIZE=5][B]Update: Player Shop & Colorized Admin Shop[/B][/SIZE]

[B]New: Player-to-player shop[/B]
[LIST]
[*][I]/pshop browse[/I] (alias [I]/ps[/I]) opens a GUI listing every player's active listings — left-click buys 1, shift-left buys the whole remaining stock. Payment goes straight from buyer to seller through Vault, no admin markup.
[*][I]/pshop my[/I] manages your own listings: hold the item you want to sell and click an empty slot, then set the quantity and the price per item with +/- buttons in a GUI (no chat typing involved).
[*]Unlike the admin shop, stock here is real: it's exactly what sellers put up, and a listing disappears once it's sold out.
[*][I]/pshop history[/I] shows your own last trades, both as buyer and seller — not a public log, just your own.
[*]Two new Quick Actions (G key) buttons: [I]Player Shop[/I] and [I]My Shop[/I].
[/LIST]

[B]Fixed: admin shop GUI text was hardcoded English[/B]
[LIST]
[*]The admin shop's item lore (Buy:/Sell: lines and the click hints) was plain hardcoded English text with no color codes at all, completely ignoring [I]language: en/ja[/I] in config.yml. It now goes through the same messages.<language> system as everything else, so it colorizes and localizes correctly.
[/LIST]

[B]Fixed: setting a price could fail with "not a number"[/B]
[LIST]
[*]Both the admin shop and the player shop used to ask for prices/quantities in chat. On servers with another chat-formatting plugin installed (LunaChat, DiscordSRV, etc.), that plugin could rewrite the message before it was read, so a perfectly valid number was sometimes rejected. Chat input has been removed entirely for prices and quantities — you now set them with +1/+10/+100/+1000/+10000 (and matching -) buttons in a GUI, so this class of bug can no longer happen.
[/LIST]

[B]Fixed: custom items (Nova, ItemsAdder, Oraxen, ...) turned back into their base item[/B]
[LIST]
[*]Both shops used to remember and hand back items by their vanilla Material only, throwing away everything else. A custom item built on top of a vanilla base item via ItemMeta (Nova's is the example that got reported — a custom food item that used a shulker box as its base) came back as a plain vanilla item of that base type once listed or bought/sold. Both shops now store and match the real ItemStack (comparing full ItemMeta, not just Material), so the exact custom item is preserved — no plugin-specific code was needed for this, so it isn't limited to Nova. Existing shop.yml/playershop.yml data from before this update still loads fine.
[/LIST]

Both are on by default; toggle the player shop with [I]player-shop.enabled: false[/I] in config.yml if you'd rather not have it.
