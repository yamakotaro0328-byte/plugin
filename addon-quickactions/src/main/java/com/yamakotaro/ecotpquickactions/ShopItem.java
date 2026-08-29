package com.yamakotaro.ecotpquickactions;

import org.bukkit.Material;

/**
 * アドミンショップの1スロット分の設定。buyPrice/sellPrice が null ならその売買方向は無効。
 * アドミンショップは在庫を持たない(サーバーが無限に売買する、いわゆる"admin shop")ため、
 * 数量の管理は不要。
 */
public final class ShopItem {

    private final Material material;
    private Double buyPrice;
    private Double sellPrice;

    public ShopItem(Material material, Double buyPrice, Double sellPrice) {
        this.material = material;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
    }

    public Material getMaterial() {
        return material;
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
