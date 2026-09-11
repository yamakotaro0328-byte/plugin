package com.yamakotaro.ecolobby.gui;

import com.yamakotaro.ecolobby.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.Material;

import java.util.Map;

/** Display-only list of currently online players (name + a head each) - clicking does nothing. */
public class PlayerListHolder implements InventoryHolder {

    private final Inventory inventory;

    public PlayerListHolder(Messages messages) {
        int onlineCount = Bukkit.getOnlinePlayers().size();
        int size = Math.max(9, (int) (Math.ceil(onlineCount / 9.0) * 9));
        this.inventory = Bukkit.createInventory(this, size, messages.get("menu.playerlist-title", Map.of()));

        int slot = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (slot >= size) {
                break;
            }
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = head.getItemMeta();
            if (meta instanceof SkullMeta skullMeta) {
                skullMeta.setOwningPlayer(online);
                skullMeta.displayName(messages.color("&f" + online.getName()));
                head.setItemMeta(skullMeta);
            }
            inventory.setItem(slot, head);
            slot++;
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
