package com.yamakotaro.ecotp.gui;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * EcoTP の GUI 全体で使い回す、装飾用フィラーとアイコン生成の共通処理。
 */
final class MenuItems {

    private MenuItems() {
    }

    /** 枠を埋める、名前無し(実際は半角スペース)のガラス板。クリックしても何も起きない。 */
    static ItemStack filler() {
        return item(Material.GRAY_STAINED_GLASS_PANE, " ", null);
    }

    static ItemStack item(Material material, String displayName, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
