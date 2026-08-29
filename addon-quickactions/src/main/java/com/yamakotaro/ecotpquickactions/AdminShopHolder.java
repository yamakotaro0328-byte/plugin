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
 * 価格設定用。価格設定もチャット入力(ChatInputManager)経由でGUI内で完結する。
 * ロア(説明文)はすべてconfig.ymlのmessages.<language>から取得し、&カラーコードにも対応する。
 */
public class AdminShopHolder implements InventoryHolder {

    public enum Mode {
        SHOP,
        ADMIN
    }

    private final Mode mode;
    private final Messages messages;
    private final Inventory inventory;

    public AdminShopHolder(Mode mode, AdminShopManager manager, Messages messages, Component title) {
        this.mode = mode;
        this.messages = messages;
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
                List<Component> lore = new ArrayList<>();
                lore.add(item.getBuyPrice() != null
                        ? messages.get("adminshop.lore.buy", Map.of("price", String.valueOf(item.getBuyPrice())))
                        : messages.get("adminshop.lore.buy-disabled", Map.of()));
                lore.add(item.getSellPrice() != null
                        ? messages.get("adminshop.lore.sell", Map.of("price", String.valueOf(item.getSellPrice())))
                        : messages.get("adminshop.lore.sell-disabled", Map.of()));
                if (mode == Mode.SHOP) {
                    lore.add(messages.get("adminshop.lore.shop-hint-1", Map.of()));
                    lore.add(messages.get("adminshop.lore.shop-hint-2", Map.of()));
                } else {
                    lore.add(messages.get("adminshop.lore.admin-hint-1", Map.of()));
                    lore.add(messages.get("adminshop.lore.admin-hint-2", Map.of()));
                    lore.add(messages.get("adminshop.lore.admin-hint-3", Map.of()));
                }
                meta.lore(lore);
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
