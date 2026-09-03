package com.yamakotaro.ecotp.gui;

import com.yamakotaro.ecotp.EcoTpPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * /tpa や /pay, /donate の相手を選ぶための、オンラインプレイヤーの頭アイテム一覧GUI。
 * アイテムの表示名 = プレイヤー名 (色無し) にしておき、クリック時にそのまま
 * Bukkit.getPlayerExact に渡せるようにしている。
 * 最下段は枠 (ガラス板) と、メインメニューへ戻るボタン。
 */
public class PlayerSelectHolder implements InventoryHolder {

    public enum Purpose {
        TPA, TPHERE, PAY, DONATE
    }

    /** 頭アイテムを並べられる枠 (最下段は戻る/枠専用)。 */
    public static final int CONTENT_SIZE = 45;
    private static final int SIZE = 54;
    public static final int SLOT_BACK = 49;

    private final Inventory inventory;
    private final Purpose purpose;

    public PlayerSelectHolder(EcoTpPlugin plugin, Player viewer, Purpose purpose) {
        this.purpose = purpose;
        this.inventory = Bukkit.createInventory(this, SIZE, plugin.getMessages().get("menu.select-player-title"));

        ItemStack filler = MenuItems.filler();
        for (int slot = CONTENT_SIZE; slot < SIZE; slot++) {
            inventory.setItem(slot, filler);
        }
        inventory.setItem(SLOT_BACK, MenuItems.item(Material.ARROW, plugin.getMessages().get("menu.back"), null));

        int slot = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(viewer.getUniqueId())) {
                continue;
            }
            if (slot >= CONTENT_SIZE) {
                break; // 表示しきれない分は諦める (54人以上同時オンラインは想定外)
            }
            inventory.setItem(slot, headOf(online));
            slot++;
        }
    }

    private static ItemStack headOf(Player player) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(player);
            skullMeta.setDisplayName(player.getName());
            stack.setItemMeta(skullMeta);
        }
        return stack;
    }

    public Purpose getPurpose() {
        return purpose;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
