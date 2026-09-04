package com.yamakotaro.ecojobs.menu;

import com.yamakotaro.ecojobs.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Small building blocks shared by this package's several inventory-GUI holders. */
final class MenuUtil {

    private MenuUtil() {
    }

    static ItemStack closeItem(Messages messages) {
        ItemStack stack = new ItemStack(Material.BARRIER);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(messages.get("menu.close", Map.of()));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** Every sub-menu but the hub itself needs one of these - see each holder's BACK_SLOT. */
    static ItemStack backItem(Messages messages) {
        ItemStack stack = new ItemStack(Material.ARROW);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(messages.get("menu.back", Map.of()));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    static ItemStack prevPageItem(Messages messages) {
        ItemStack stack = new ItemStack(Material.SPECTRAL_ARROW);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(messages.get("menu.page-prev", Map.of()));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    static ItemStack nextPageItem(Messages messages) {
        ItemStack stack = new ItemStack(Material.SPECTRAL_ARROW);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(messages.get("menu.page-next", Map.of()));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /**
     * Fills the border (row 0, the last row, column 0, and column 8) of a bordered inventory whose
     * size is a multiple of 9, with a blank glass pane. Callers overwrite specific border slots
     * afterwards (back/close/pagination buttons) - see each holder's render().
     */
    static void fillBorder(Inventory inventory) {
        ItemStack filler = fillerItem();
        int lastRow = inventory.getSize() / 9 - 1;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            int row = slot / 9;
            int col = slot % 9;
            if (row == 0 || row == lastRow || col == 0 || col == 8) {
                inventory.setItem(slot, filler);
            }
        }
    }

    private static ItemStack fillerItem() {
        ItemStack stack = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /**
     * The 28 interior slots of a bordered 54-slot (6x9) inventory - rows 1-4, columns 1-7 (columns
     * 0/8 and rows 0/5 are the border from {@link #fillBorder}).
     */
    static List<Integer> interiorSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                slots.add(row * 9 + col);
            }
        }
        return slots;
    }

    /** Same formatting rule as JobsCommand's private helper: per-level rewards show a rate, not a flat number. */
    static String formatReward(double flat, double perLevel) {
        if (perLevel > 0) {
            return String.format("%.2f/enchant-level", perLevel);
        }
        return String.format("%.2f", flat);
    }
}
