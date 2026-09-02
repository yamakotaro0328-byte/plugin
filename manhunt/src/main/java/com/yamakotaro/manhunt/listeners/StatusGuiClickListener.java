package com.yamakotaro.manhunt.listeners;

import com.yamakotaro.manhunt.gui.StatusGuiHolder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/** The status GUI is a read-only display - block every click while it's the open view, top and bottom alike. */
public class StatusGuiClickListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof StatusGuiHolder) {
            event.setCancelled(true);
        }
    }
}
