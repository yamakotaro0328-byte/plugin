package com.yamakotaro.ecolobby.gui;

import com.yamakotaro.ecolobby.LobbyMenuConfig;
import com.yamakotaro.ecolobby.Messages;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The server-select menu opened from the compass given on join - one item per entry in config.yml's
 * servers: list, in the same order. GuiListener maps a click back to the server name via that same
 * index, so this class only needs to build the display, not track click behavior itself.
 */
public class ServerMenuHolder implements InventoryHolder {

    private final Inventory inventory;
    private final List<String> serverNames = new ArrayList<>();

    public ServerMenuHolder(Messages messages, List<LobbyMenuConfig.ServerEntry> servers) {
        int size = Math.max(9, (int) (Math.ceil(servers.size() / 9.0) * 9));
        this.inventory = Bukkit.createInventory(this, size, messages.get("menu.server-title", Map.of()));

        int slot = 0;
        for (LobbyMenuConfig.ServerEntry server : servers) {
            ItemStack item = new ItemStack(server.material());
            ItemMeta meta = item.getItemMeta();
            meta.displayName(messages.color(server.displayName()));
            if (!server.lore().isEmpty()) {
                meta.lore(server.lore().stream().map(messages::color).toList());
            }
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
            serverNames.add(server.name());
            slot++;
        }
    }

    /** @return the configured server name for a clicked slot, or null if that slot is empty/unused. */
    public String serverNameAt(int slot) {
        return slot >= 0 && slot < serverNames.size() ? serverNames.get(slot) : null;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
