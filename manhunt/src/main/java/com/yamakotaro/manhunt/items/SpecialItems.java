package com.yamakotaro.manhunt.items;

import com.yamakotaro.manhunt.Messages;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;

/** Builds and identifies the two role items given out at game start: the hunter's Locator Orb and the runner's Smoke Bomb. */
public class SpecialItems {

    private final Messages messages;
    private final NamespacedKey locatorOrbKey;
    private final NamespacedKey smokeBombKey;

    public SpecialItems(JavaPlugin plugin, Messages messages) {
        this.messages = messages;
        this.locatorOrbKey = new NamespacedKey(plugin, "locator_orb");
        this.smokeBombKey = new NamespacedKey(plugin, "smoke_bomb");
    }

    public ItemStack createLocatorOrb(int charges) {
        ItemStack item = new ItemStack(Material.CLOCK, charges);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(messages.get("item.locator-name", Map.of()));
        meta.lore(List.of(messages.get("item.locator-lore", Map.of())));
        meta.getPersistentDataContainer().set(locatorOrbKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createSmokeBomb(int charges, long durationSeconds) {
        ItemStack item = new ItemStack(Material.GUNPOWDER, charges);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(messages.get("item.smoke-name", Map.of()));
        meta.lore(List.of(messages.get("item.smoke-lore", Map.of("seconds", String.valueOf(durationSeconds)))));
        meta.getPersistentDataContainer().set(smokeBombKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isLocatorOrb(ItemStack item) {
        return hasKey(item, locatorOrbKey);
    }

    public boolean isSmokeBomb(ItemStack item) {
        return hasKey(item, smokeBombKey);
    }

    /** Strips any leftover special items from a player's inventory - called when a game ends. */
    public void clearFrom(Player player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (isLocatorOrb(stack) || isSmokeBomb(stack)) {
                inventory.setItem(i, null);
            }
        }
    }

    private boolean hasKey(ItemStack item, NamespacedKey key) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BOOLEAN);
    }
}
