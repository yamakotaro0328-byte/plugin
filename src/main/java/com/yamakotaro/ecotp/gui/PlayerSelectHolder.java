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
 * /tpa や /pay の相手を選ぶための、オンラインプレイヤーの頭アイテム一覧GUI。
 * アイテムの表示名 = プレイヤー名 (色無し) にしておき、クリック時にそのまま
 * Bukkit.getPlayerExact に渡せるようにしている。
 */
public class PlayerSelectHolder implements InventoryHolder {

    public enum Purpose {
        TPA, TPHERE, PAY
    }

    private final Inventory inventory;
    private final Purpose purpose;

    public PlayerSelectHolder(EcoTpPlugin plugin, Player viewer, Purpose purpose) {
        this.purpose = purpose;
        this.inventory = Bukkit.createInventory(this, 54, plugin.getMessages().get("menu.select-player-title"));

        int slot = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(viewer.getUniqueId())) {
                continue;
            }
            if (slot >= inventory.getSize()) {
                break;
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
