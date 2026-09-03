package com.yamakotaro.ecotp.gui;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * EcoTP の GUI 全体で使い回す、装飾用フィラーとアイコン生成の共通処理。
 */
final class MenuItems {

    private MenuItems() {
    }

    /**
     * 枠を埋める、名前無し(実際は半角スペース)の市松模様のガラス板。クリックしても何も起きない。
     * @param slot このアイテムを置くインベントリのスロット番号 (行/列から模様の濃淡を決める)。
     */
    static ItemStack borderPane(int slot) {
        boolean alt = ((slot / 9) + (slot % 9)) % 2 == 0;
        Material material = alt ? Material.GRAY_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE;
        return item(material, " ", null);
    }

    static ItemStack item(Material material, String displayName, List<String> lore) {
        return item(material, displayName, lore, false);
    }

    /** @param glint true なら (実際には何もエンチャントせずに) エンチャントの輝きだけを付与する。 */
    static ItemStack item(Material material, String displayName, List<String> lore, boolean glint) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            if (glint) {
                meta.setEnchantmentGlintOverride(true);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** GUIを開いたときの効果音。ただのチェスト開閉ではなく専用の演出にすることで、他の追加要素より安っぽく見えないようにする。 */
    static void playOpenSound(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
    }
}
