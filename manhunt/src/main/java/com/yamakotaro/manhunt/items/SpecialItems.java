package com.yamakotaro.manhunt.items;

import com.yamakotaro.manhunt.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;

/**
 * Builds and identifies every role item: the hunter's Locator Orb, Tracking Dart and Snare Trap,
 * and the runner's Smoke Bomb, Flashbang and Blink Shard. Locator Orb/Smoke Bomb are handed out
 * free at game start; the rest are craftable at any time via registerRecipes().
 */
public class SpecialItems {

    private final Messages messages;
    private final NamespacedKey locatorOrbKey;
    private final NamespacedKey smokeBombKey;
    private final NamespacedKey trackingDartKey;
    private final NamespacedKey flashbangKey;
    private final NamespacedKey snareTrapKey;
    private final NamespacedKey blinkShardKey;

    public SpecialItems(JavaPlugin plugin, Messages messages) {
        this.messages = messages;
        this.locatorOrbKey = new NamespacedKey(plugin, "locator_orb");
        this.smokeBombKey = new NamespacedKey(plugin, "smoke_bomb");
        this.trackingDartKey = new NamespacedKey(plugin, "tracking_dart");
        this.flashbangKey = new NamespacedKey(plugin, "flashbang");
        this.snareTrapKey = new NamespacedKey(plugin, "snare_trap");
        this.blinkShardKey = new NamespacedKey(plugin, "blink_shard");
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

    public ItemStack createTrackingDart() {
        ItemStack item = new ItemStack(Material.SNOWBALL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(messages.get("item.tracking-dart-name", Map.of()));
        meta.lore(List.of(messages.get("item.tracking-dart-lore", Map.of())));
        meta.getPersistentDataContainer().set(trackingDartKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createFlashbang() {
        ItemStack item = new ItemStack(Material.SNOWBALL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(messages.get("item.flashbang-name", Map.of()));
        meta.lore(List.of(messages.get("item.flashbang-lore", Map.of())));
        meta.getPersistentDataContainer().set(flashbangKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createSnareTrap() {
        ItemStack item = new ItemStack(Material.SNOWBALL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(messages.get("item.snare-name", Map.of()));
        meta.lore(List.of(messages.get("item.snare-lore", Map.of())));
        meta.getPersistentDataContainer().set(snareTrapKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createBlinkShard() {
        ItemStack item = new ItemStack(Material.FEATHER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(messages.get("item.blink-name", Map.of()));
        meta.lore(List.of(messages.get("item.blink-lore", Map.of())));
        meta.getPersistentDataContainer().set(blinkShardKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    /** Registers the crafting-table recipes for the craftable items. Safe to call once per plugin enable. */
    public void registerRecipes(JavaPlugin plugin) {
        NamespacedKey trackingDartRecipeKey = new NamespacedKey(plugin, "tracking_dart_recipe");
        Bukkit.removeRecipe(trackingDartRecipeKey);
        ShapelessRecipe trackingDartRecipe = new ShapelessRecipe(trackingDartRecipeKey, createTrackingDart());
        trackingDartRecipe.addIngredient(Material.SNOWBALL);
        trackingDartRecipe.addIngredient(Material.ENDER_EYE);
        trackingDartRecipe.addIngredient(Material.REDSTONE);
        Bukkit.addRecipe(trackingDartRecipe);

        NamespacedKey flashbangRecipeKey = new NamespacedKey(plugin, "flashbang_recipe");
        Bukkit.removeRecipe(flashbangRecipeKey);
        ShapelessRecipe flashbangRecipe = new ShapelessRecipe(flashbangRecipeKey, createFlashbang());
        flashbangRecipe.addIngredient(Material.SNOWBALL);
        flashbangRecipe.addIngredient(Material.GUNPOWDER);
        flashbangRecipe.addIngredient(Material.GLOWSTONE_DUST);
        Bukkit.addRecipe(flashbangRecipe);

        NamespacedKey snareTrapRecipeKey = new NamespacedKey(plugin, "snare_trap_recipe");
        Bukkit.removeRecipe(snareTrapRecipeKey);
        ShapelessRecipe snareTrapRecipe = new ShapelessRecipe(snareTrapRecipeKey, createSnareTrap());
        snareTrapRecipe.addIngredient(Material.SNOWBALL);
        snareTrapRecipe.addIngredient(Material.STRING);
        snareTrapRecipe.addIngredient(Material.IRON_NUGGET);
        Bukkit.addRecipe(snareTrapRecipe);

        NamespacedKey blinkShardRecipeKey = new NamespacedKey(plugin, "blink_shard_recipe");
        Bukkit.removeRecipe(blinkShardRecipeKey);
        ShapelessRecipe blinkShardRecipe = new ShapelessRecipe(blinkShardRecipeKey, createBlinkShard());
        blinkShardRecipe.addIngredient(Material.SNOWBALL);
        blinkShardRecipe.addIngredient(Material.ENDER_PEARL);
        blinkShardRecipe.addIngredient(Material.FEATHER);
        Bukkit.addRecipe(blinkShardRecipe);
    }

    public boolean isLocatorOrb(ItemStack item) {
        return hasKey(item, locatorOrbKey);
    }

    public boolean isSmokeBomb(ItemStack item) {
        return hasKey(item, smokeBombKey);
    }

    public boolean isTrackingDart(ItemStack item) {
        return hasKey(item, trackingDartKey);
    }

    public boolean isFlashbang(ItemStack item) {
        return hasKey(item, flashbangKey);
    }

    public boolean isSnareTrap(ItemStack item) {
        return hasKey(item, snareTrapKey);
    }

    public boolean isBlinkShard(ItemStack item) {
        return hasKey(item, blinkShardKey);
    }

    /** Strips any leftover special items from a player's inventory - called when a game ends. */
    public void clearFrom(Player player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (isLocatorOrb(stack) || isSmokeBomb(stack) || isTrackingDart(stack) || isFlashbang(stack)
                    || isSnareTrap(stack) || isBlinkShard(stack)) {
                inventory.setItem(i, null);
            }
        }
    }

    private boolean hasKey(ItemStack item, NamespacedKey key) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BOOLEAN);
    }
}
