package com.yamakotaro.quickshopnovacompat;

import com.ghostchu.quickshop.api.QuickShopAPI;
import com.ghostchu.quickshop.api.registry.BuiltInRegistry;
import com.ghostchu.quickshop.api.registry.Registry;
import com.ghostchu.quickshop.api.registry.builtin.itemexpression.ItemExpressionHandler;
import com.ghostchu.quickshop.api.registry.builtin.itemexpression.ItemExpressionRegistry;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.xenondevs.nova.api.Nova;
import xyz.xenondevs.nova.api.item.NovaItem;

/**
 * QuickShop-Hikariの「アイテム表現」機能(価格ルールやアイテムブラックリスト等で
 * "nova:addon:item_id" のような文字列でアイテムを指定できる仕組み)にNovaのカスタムアイテムを
 * 対応させるだけの小さな橋渡しプラグイン。ItemsAdder用の公式互換アドオンと同じ仕組み。
 *
 * なお、チェストショップ本体の売買一致判定(QuickShopのItemMatcher)はデフォルトで
 * ItemStack#isSimilar() による比較のため、Novaのカスタムアイテムをそのままチェストショップで
 * 売買すること自体はこのプラグインが無くても動作する。
 */
public class QuickShopNovaCompatPlugin extends JavaPlugin implements ItemExpressionHandler {

    @Override
    public void onEnable() {
        Registry registry = QuickShopAPI.getInstance().getRegistry().getRegistry(BuiltInRegistry.ITEM_EXPRESSION);
        if (registry instanceof ItemExpressionRegistry itemExpressionRegistry) {
            if (itemExpressionRegistry.registerHandlerSafely(this)) {
                getLogger().info("Registered the Nova item-expression handler with QuickShop-Hikari.");
            } else {
                getLogger().warning("Could not register the Nova item-expression handler (prefix \"nova\" already taken?).");
            }
        } else {
            getLogger().warning("QuickShop-Hikari's item-expression registry is unavailable; Nova item expressions won't work.");
        }
    }

    @Override
    public Plugin getPlugin() {
        return this;
    }

    @Override
    public String getPrefix() {
        return "nova";
    }

    @Override
    public boolean match(ItemStack stack, String expression) {
        NovaItem novaItem = Nova.getNova().getItemRegistry().getOrNull(stack);
        if (novaItem == null) {
            return false;
        }
        return expression.equals(novaItem.getId().toNamespacedKey().toString());
    }
}
