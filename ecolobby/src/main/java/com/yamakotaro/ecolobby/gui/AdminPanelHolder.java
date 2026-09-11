package com.yamakotaro.ecolobby.gui;

import com.yamakotaro.ecolobby.EcoLobbyPlugin;
import com.yamakotaro.ecolobby.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The admin-panel item's GUI: one on/off toggle per major feature flag (backed directly by
 * config.yml's features.*, saved immediately on click) plus set-spawn/reload/close buttons.
 * The finer-grained per-item toggles (particle trail, vote menu, etc.) are config-only for now -
 * this panel covers the flags an admin is most likely to want to flip on the fly.
 */
public class AdminPanelHolder implements InventoryHolder {

    public static final int SLOT_SET_SPAWN = 29;
    public static final int SLOT_RELOAD = 31;
    public static final int SLOT_CLOSE = 33;

    private static final List<String> TOGGLE_KEYS = List.of(
            "protection", "double-jump", "jump-pads", "void-teleport",
            "clear-inventory-on-join", "server-menu", "links-menu", "lobby-menu");

    private final EcoLobbyPlugin plugin;
    private final Inventory inventory;
    private final Map<Integer, String> toggleSlots = new LinkedHashMap<>();

    public AdminPanelHolder(EcoLobbyPlugin plugin) {
        this.plugin = plugin;
        Messages messages = plugin.getMessages();
        this.inventory = Bukkit.createInventory(this, 36, messages.get("admin.panel-title", Map.of()));

        int slot = 10;
        for (String key : TOGGLE_KEYS) {
            toggleSlots.put(slot, key);
            renderToggle(slot, key);
            slot++;
        }

        placeButton(SLOT_SET_SPAWN, Material.NETHER_STAR, "admin.set-spawn");
        placeButton(SLOT_RELOAD, Material.SUNFLOWER, "admin.reload");
        placeButton(SLOT_CLOSE, Material.BARRIER, "admin.close");
    }

    private void placeButton(int slot, Material material, String titlePath) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.getMessages().get(titlePath, Map.of()));
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }

    private void renderToggle(int slot, String key) {
        boolean enabled = plugin.isFeatureEnabled(key);
        Messages messages = plugin.getMessages();
        ItemStack item = new ItemStack(enabled ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(messages.get("admin.features." + key, Map.of()));
        meta.lore(List.of(messages.get(enabled ? "admin.toggle-on-lore" : "admin.toggle-off-lore", Map.of())));
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }

    /** @return true if the click was on a recognized toggle slot (already handled), false otherwise. */
    public boolean toggleIfApplicable(int slot) {
        String key = toggleSlots.get(slot);
        if (key == null) {
            return false;
        }
        boolean newValue = !plugin.isFeatureEnabled(key);
        plugin.getConfig().set("features." + key, newValue);
        plugin.saveConfig();
        renderToggle(slot, key);
        return true;
    }

    public void setSpawnHere(Player player) {
        plugin.getSpawnManager().setSpawn(player.getLocation());
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
