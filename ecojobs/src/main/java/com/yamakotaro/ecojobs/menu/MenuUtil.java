package com.yamakotaro.ecojobs.menu;

import com.yamakotaro.ecojobs.Messages;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

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
}
