package com.yamakotaro.ecojobs.menu;

import com.yamakotaro.ecojobs.Messages;
import com.yamakotaro.ecojobs.PlayerJobManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

/** Per-player preferences: click sounds and the earnings action-bar, toggled by clicking a dye. */
public class SettingsMenuHolder implements InventoryHolder {

    public static final int SOUND_SLOT = 11;
    public static final int ACTIONBAR_SLOT = 15;
    public static final int BACK_SLOT = 18;
    public static final int CLOSE_SLOT = 22;

    private final Messages messages;
    private final Inventory inventory;

    public SettingsMenuHolder(Messages messages) {
        this.messages = messages;
        this.inventory = Bukkit.createInventory(this, 27, messages.get("menu.settings-title", Map.of()));
    }

    public void render(PlayerJobManager playerJobManager, Player viewer) {
        inventory.clear();
        inventory.setItem(SOUND_SLOT, toggleItem("menu.settings-sound-title", playerJobManager.isSoundEnabled(viewer)));
        inventory.setItem(ACTIONBAR_SLOT, toggleItem("menu.settings-actionbar-title", playerJobManager.isActionBarEnabled(viewer)));
        inventory.setItem(BACK_SLOT, MenuUtil.backItem(messages));
        inventory.setItem(CLOSE_SLOT, MenuUtil.closeItem(messages));
    }

    private ItemStack toggleItem(String titleKey, boolean enabled) {
        ItemStack stack = new ItemStack(enabled ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(messages.get(titleKey, Map.of()));
            List<Component> lore = List.of(messages.get(enabled ? "menu.settings-on" : "menu.settings-off", Map.of()));
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
