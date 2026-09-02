package com.yamakotaro.ecoboss.boss;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * One entry in a boss's loot table. "guaranteed" rewards always drop once (in addition to the
 * weighted picks handed to participants); "broadcast" rewards are announced to the whole server.
 */
public record Reward(Material material, int amount, String name, List<String> lore, int weight,
                      boolean guaranteed, boolean broadcast) {

    public ItemStack toItemStack() {
        ItemStack item = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(name));
        if (!lore.isEmpty()) {
            meta.lore(lore.stream().map(line -> LegacyComponentSerializer.legacyAmpersand().deserialize(line)).toList());
        }
        item.setItemMeta(meta);
        return item;
    }
}
