package com.yamakotaro.ecorail.settings;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marks an inventory as EcoRail's settings menu so the click listener can identify it reliably,
 * instead of matching on the (translatable, spoofable) inventory title.
 */
public class SettingsMenuHolder implements InventoryHolder {

    private Inventory inventory;

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
