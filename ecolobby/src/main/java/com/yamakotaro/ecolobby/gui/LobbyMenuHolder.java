package com.yamakotaro.ecolobby.gui;

import com.yamakotaro.ecolobby.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;

/**
 * The "everything else" hub opened from the nether-star lobby-menu item - one icon per secondary
 * lobby feature (the ones not important enough to deserve their own hotbar slot). Each icon's
 * slot maps to an action key that GuiListener dispatches on click.
 */
public class LobbyMenuHolder implements InventoryHolder {

    private static final int SIZE = 27;

    private final Inventory inventory;
    private final Map<Integer, String> actions = new HashMap<>();

    public LobbyMenuHolder(Messages messages) {
        this.inventory = Bukkit.createInventory(this, SIZE, messages.get("menu.lobby-title", Map.of()));

        place(10, Material.FIREWORK_ROCKET, messages, "lobby-menu.particle-trail", "particle-trail");
        place(11, Material.ENDER_PEARL, messages, "lobby-menu.random-teleport", "random-teleport");
        place(12, Material.WRITTEN_BOOK, messages, "lobby-menu.rules-book", "rules-book");
        place(13, Material.PAPER, messages, "lobby-menu.server-info", "server-info");
        place(14, Material.PLAYER_HEAD, messages, "lobby-menu.player-list", "player-list");
        place(15, Material.FEATHER, messages, "lobby-menu.double-jump-toggle", "double-jump-toggle");
        place(16, Material.GOLDEN_CARROT, messages, "lobby-menu.night-vision-toggle", "night-vision-toggle");
        place(20, Material.EXPERIENCE_BOTTLE, messages, "lobby-menu.vote-menu", "vote-menu");
        place(21, Material.RED_BED, messages, "lobby-menu.leave-server", "leave-server");
    }

    private void place(int slot, Material material, Messages messages, String titlePath, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(messages.get(titlePath, Map.of()));
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
        actions.put(slot, action);
    }

    public String actionAt(int slot) {
        return actions.get(slot);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
