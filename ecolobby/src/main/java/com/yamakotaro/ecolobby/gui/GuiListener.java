package com.yamakotaro.ecolobby.gui;

import com.yamakotaro.ecolobby.BungeeConnector;
import com.yamakotaro.ecolobby.EcoLobbyPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

public class GuiListener implements Listener {

    private final EcoLobbyPlugin plugin;

    public GuiListener(EcoLobbyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ServerMenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof ServerMenuHolder serverMenu)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getInventory()) {
            return; // Ignore clicks in the player's own inventory below the menu.
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        String serverName = serverMenu.serverNameAt(event.getSlot());
        if (serverName == null) {
            return;
        }
        player.closeInventory();
        BungeeConnector.connect(plugin, player, serverName);
    }
}
