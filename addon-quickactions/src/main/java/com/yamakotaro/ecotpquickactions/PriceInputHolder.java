package com.yamakotaro.ecotpquickactions;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 金床(Anvil)のリネーム欄を使って、価格をテキストで入力してもらうためのGUI。
 * コマンドを一切使わず、アドミンショップの出品・価格設定をすべてGUI内で完結させるための仕組み。
 * プレイヤーがリネーム欄に入力した文字列を、結果スロット(スロット2)クリック時に読み取る。
 * 実際のリネーム操作(経験値消費・アイテム変更)は行わない — クリックは常にキャンセルし、
 * 入力されたテキストの取得だけに利用する。
 */
public class PriceInputHolder implements InventoryHolder {

    private final Inventory inventory;
    private final int shopSlot;
    private final boolean buy;

    public PriceInputHolder(int shopSlot, boolean buy, Component title, Material icon, String currentValue) {
        this.shopSlot = shopSlot;
        this.buy = buy;
        this.inventory = Bukkit.createInventory(this, InventoryType.ANVIL, title);
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(currentValue);
            item.setItemMeta(meta);
        }
        inventory.setItem(0, item);
    }

    public int getShopSlot() {
        return shopSlot;
    }

    public boolean isBuy() {
        return buy;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
