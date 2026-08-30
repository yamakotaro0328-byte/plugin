package com.yamakotaro.ecotpquickactions;

import org.bukkit.inventory.ItemStack;

/**
 * アドミンショップの1スロット分の設定。buyPrice/sellPrice が null ならその売買方向は無効。
 * アドミンショップは在庫を持たない(サーバーが無限に売買する、いわゆる"admin shop")ため、
 * 数量の管理は不要。
 * バニラのMaterialだけでなく、NovaのようにItemMeta(カスタムモデルデータ等)で見た目や
 * 中身を変えるプラグインのアイテムもそのまま売買できるよう、Materialではなく実際の
 * ItemStack(常に数量1のテンプレート)を保持する。
 */
public final class ShopItem {

    private final ItemStack template;
    private Double buyPrice;
    private Double sellPrice;

    public ShopItem(ItemStack template, Double buyPrice, Double sellPrice) {
        this.template = template.clone();
        this.template.setAmount(1);
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
    }

    public ItemStack getTemplate() {
        return template.clone();
    }

    public Double getBuyPrice() {
        return buyPrice;
    }

    public void setBuyPrice(Double buyPrice) {
        this.buyPrice = buyPrice;
    }

    public Double getSellPrice() {
        return sellPrice;
    }

    public void setSellPrice(Double sellPrice) {
        this.sellPrice = sellPrice;
    }
}
