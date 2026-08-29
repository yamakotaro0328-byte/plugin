package com.yamakotaro.ecotpquickactions;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * アドミンショップのチェストGUI。SHOPモードは購入/売却用、ADMINモードは商品の追加・削除・
 * 価格設定用。価格設定もPriceInputHolder(金床のリネーム欄)経由でGUI内で完結する。
 */
public class AdminShopHolder implements InventoryHolder {

    public enum Mode {
        SHOP,
        ADMIN
    }

    private final Mode mode;
    private final Inventory inventory;

    public AdminShopHolder(Mode mode, AdminShopManager manager, Component title) {
        this.mode = mode;
        this.inventory = Bukkit.createInventory(this, manager.size(), title);
        render(manager);
    }

    public Mode getMode() {
        return mode;
    }

    public void render(AdminShopManager manager) {
        inventory.clear();
        for (Map.Entry<Integer, ShopItem> entry : manager.items().entrySet()) {
            int slot = entry.getKey();
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            ShopItem item = entry.getValue();
            ItemStack stack = new ItemStack(item.getMaterial());
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                List<String> lore = new ArrayList<>();
                lore.add(item.getBuyPrice() != null ? "Buy: " + item.getBuyPrice() : "Buy: -");
                lore.add(item.getSellPrice() != null ? "Sell: " + item.getSellPrice() : "Sell: -");
                if (mode == Mode.SHOP) {
                    lore.add("Left-click: buy 1 / Shift-left: buy stack");
                    lore.add("Right-click: sell 1 / Shift-right: sell all");
                } else {
                    lore.add("Left-click: set buy price");
                    lore.add("Right-click: set sell price");
                    lore.add("Shift-click: remove this slot");
                }
                meta.setLore(lore);
                stack.setItemMeta(meta);
            }
            inventory.setItem(slot, stack);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
