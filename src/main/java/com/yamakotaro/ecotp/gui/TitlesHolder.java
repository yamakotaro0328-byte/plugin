package com.yamakotaro.ecotp.gui;

import com.yamakotaro.ecotp.ChatUtil;
import com.yamakotaro.ecotp.EcoTpPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** /titles : 持っている称号から1つ選ぶ (または外す)。 */
public class TitlesHolder implements EcoTpMenu {

    private static final int SIZE = 54;
    private static final int CONTENT = 45;
    public static final int SLOT_NONE = 49;
    public static final int SLOT_BACK = 45;

    private final Inventory inventory;
    private final List<String> owned;

    public TitlesHolder(EcoTpPlugin plugin, Player viewer) {
        this.owned = plugin.getTitleManager().getOwned(viewer.getUniqueId());
        String selected = plugin.getTitleManager().getSelected(viewer.getUniqueId());
        this.inventory = Bukkit.createInventory(this, SIZE, plugin.getMessages().get("event.titles-title"));
        for (int slot = CONTENT; slot < SIZE; slot++) {
            inventory.setItem(slot, MenuItems.borderPane(slot));
        }
        Map<Integer, ItemStack> tiles = new HashMap<>();
        for (int i = 0; i < owned.size() && i < CONTENT; i++) {
            String title = owned.get(i);
            boolean on = title.equals(selected);
            tiles.put(i, MenuItems.item(Material.NAME_TAG, ChatUtil.color(title),
                    plugin.getMessages().getList(on ? "event.titles-selected" : "event.titles-click"), on));
        }
        if (owned.isEmpty()) {
            tiles.put(22, MenuItems.item(Material.BARRIER, plugin.getMessages().get("event.titles-empty"), null));
        }
        tiles.put(SLOT_NONE, MenuItems.item(Material.GLASS_BOTTLE, plugin.getMessages().get("event.titles-none"), null));
        tiles.put(SLOT_BACK, MenuItems.item(Material.ARROW, plugin.getMessages().get("menu.back"), null));
        MenuItems.playOpenSound(viewer);
        MenuFx.open(plugin, viewer, this, inventory, tiles, false);
    }

    public String titleAt(int slot) {
        return slot >= 0 && slot < owned.size() && slot < CONTENT ? owned.get(slot) : null;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
