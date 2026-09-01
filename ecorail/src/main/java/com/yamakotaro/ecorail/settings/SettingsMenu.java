package com.yamakotaro.ecorail.settings;

import com.yamakotaro.ecorail.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SettingsMenu {

    public static final int ANTI_REVERSE_SLOT = 3;
    public static final int PLAYER_COLLISION_SLOT = 5;
    private static final int SIZE = 9;

    private SettingsMenu() {
    }

    public static void open(Player player, PlayerSettingsManager settingsManager, Messages messages) {
        SettingsMenuHolder holder = new SettingsMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE, messages.get("settings.title", Map.of()));
        holder.setInventory(inventory);
        fill(inventory, settingsManager.get(player.getUniqueId()), messages);
        player.openInventory(inventory);
    }

    /** Called after a click toggles a setting, so the open menu reflects the new state immediately. */
    public static void refresh(Inventory inventory, UUID playerId, PlayerSettingsManager settingsManager, Messages messages) {
        fill(inventory, settingsManager.get(playerId), messages);
    }

    private static void fill(Inventory inventory, PlayerSettings settings, Messages messages) {
        inventory.setItem(ANTI_REVERSE_SLOT, toggleItem(messages, "settings.anti-reverse-name", settings.antiReverse()));
        inventory.setItem(PLAYER_COLLISION_SLOT, toggleItem(messages, "settings.player-collision-name", settings.playerCollision()));
    }

    private static ItemStack toggleItem(Messages messages, String nameKey, boolean state) {
        ItemStack item = new ItemStack(state ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(messages.get(nameKey, Map.of()));
        Component stateLine = messages.get(state ? "settings.state-on" : "settings.state-off", Map.of());
        meta.lore(List.of(stateLine));
        item.setItemMeta(meta);
        return item;
    }
}
