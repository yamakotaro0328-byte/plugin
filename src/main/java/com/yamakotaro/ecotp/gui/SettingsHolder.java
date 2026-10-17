package com.yamakotaro.ecotp.gui;

import com.yamakotaro.ecotp.EcoTpPlugin;
import com.yamakotaro.ecotp.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * 個人設定: ローマ字変換と放置(AFK)をクリックで切り替えられる。
 */
public class SettingsHolder implements EcoTpMenu {

    private static final int SIZE = 27;
    public static final int SLOT_ROMAJI = 11;
    public static final int SLOT_AFK = 15;
    public static final int SLOT_BACK = 22;

    private final Inventory inventory;

    public SettingsHolder(EcoTpPlugin plugin, Player viewer) {
        Messages messages = plugin.getMessages();
        this.inventory = Bukkit.createInventory(this, SIZE, messages.get("menu.settings-title"));
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, MenuItems.borderPane(slot));
        }
        Map<Integer, ItemStack> tiles = new HashMap<>();
        if (plugin.isFeatureEnabled("romaji-conversion") && viewer.hasPermission("ecotp.romaji")) {
            tiles.put(SLOT_ROMAJI, toggle(messages, "romaji", Material.WRITABLE_BOOK,
                    plugin.getRomajiConversionManager().isEnabled(viewer.getUniqueId())));
        }
        if (plugin.isFeatureEnabled("afk") && viewer.hasPermission("ecotp.afk")) {
            tiles.put(SLOT_AFK, toggle(messages, "afk", Material.WHITE_BED,
                    plugin.getAfkManager().isAfk(viewer.getUniqueId())));
        }
        tiles.put(SLOT_BACK, MenuItems.item(Material.ARROW, messages.get("menu.back"), null));
        MenuFx.open(plugin, viewer, this, inventory, tiles, false);
    }

    private static ItemStack toggle(Messages messages, String key, Material icon, boolean on) {
        String state = messages.get(on ? "menu.toggle-on" : "menu.toggle-off");
        return MenuItems.item(on ? icon : Material.GRAY_DYE, messages.get("menu.setting-" + key),
                messages.getList("menu.lore.setting-" + key, "state", state), on);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
