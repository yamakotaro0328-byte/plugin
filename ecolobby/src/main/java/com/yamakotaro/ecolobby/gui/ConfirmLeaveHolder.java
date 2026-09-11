package com.yamakotaro.ecolobby.gui;

import com.yamakotaro.ecolobby.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;

/** Yes/no confirmation opened from the leave-server item, so a single misclick can't disconnect
 * a player from the whole network. */
public class ConfirmLeaveHolder implements InventoryHolder {

    public static final int SLOT_CONFIRM = 2;
    public static final int SLOT_CANCEL = 6;

    private final Inventory inventory;

    public ConfirmLeaveHolder(Messages messages) {
        this.inventory = Bukkit.createInventory(this, 9, messages.get("menu.leave-title", Map.of()));

        ItemStack confirm = new ItemStack(Material.LIME_WOOL);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.displayName(messages.get("leave.confirm", Map.of()));
        confirm.setItemMeta(confirmMeta);
        inventory.setItem(SLOT_CONFIRM, confirm);

        ItemStack cancel = new ItemStack(Material.RED_WOOL);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.displayName(messages.get("leave.cancel", Map.of()));
        cancel.setItemMeta(cancelMeta);
        inventory.setItem(SLOT_CANCEL, cancel);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
