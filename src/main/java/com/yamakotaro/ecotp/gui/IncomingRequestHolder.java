package com.yamakotaro.ecotp.gui;

import com.yamakotaro.ecotp.ChatUtil;
import com.yamakotaro.ecotp.EcoTpPlugin;
import com.yamakotaro.ecotp.TpaManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

/**
 * メインメニューの「保留中のリクエスト」通知アイテムから開く、承諾/拒否用のGUI。
 * 実際の処理は /tpaccept, /tpdeny をそのまま実行するだけ (TpaManager 参照)。
 */
public class IncomingRequestHolder implements InventoryHolder {

    private static final int SIZE = 27;
    public static final int SLOT_ACCEPT = 11;
    public static final int SLOT_REQUESTER = 13;
    public static final int SLOT_DENY = 15;
    public static final int SLOT_BACK = 22;

    private final Inventory inventory;

    public IncomingRequestHolder(EcoTpPlugin plugin, Player viewer, TpaManager.Type type, String requesterName) {
        this.inventory = Bukkit.createInventory(this, SIZE, plugin.getMessages().get("menu.incoming-request-title"));

        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, MenuItems.borderPane(slot));
        }

        String typeLabel = plugin.getMessages().get(typeLabelKey(type));
        List<String> requesterLore = plugin.getMessages().getList("menu.lore.incoming-request-head", "type", typeLabel);
        inventory.setItem(SLOT_REQUESTER, headOf(requesterName, requesterLore));

        inventory.setItem(SLOT_ACCEPT, MenuItems.item(Material.LIME_CONCRETE,
                plugin.getMessages().get("menu.accept"), plugin.getMessages().getList("menu.lore.accept")));
        inventory.setItem(SLOT_DENY, MenuItems.item(Material.RED_CONCRETE,
                plugin.getMessages().get("menu.deny"), plugin.getMessages().getList("menu.lore.deny")));
        inventory.setItem(SLOT_BACK, MenuItems.item(Material.ARROW, plugin.getMessages().get("menu.back"), null));

        MenuItems.playOpenSound(viewer);
    }

    static String typeLabelKey(TpaManager.Type type) {
        return type == TpaManager.Type.TPA ? "menu.request-type-tpa" : "menu.request-type-tphere";
    }

    private static ItemStack headOf(String name, List<String> lore) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(name));
            skullMeta.setDisplayName(ChatUtil.color("&e" + name));
            skullMeta.setLore(lore);
            stack.setItemMeta(skullMeta);
        }
        return stack;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
