package com.yamakotaro.ecocosmetics.gui;

import com.yamakotaro.ecocosmetics.Category;
import com.yamakotaro.ecocosmetics.CosmeticDefinition;
import com.yamakotaro.ecocosmetics.EcoCosmeticsPlugin;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.UUID;

/**
 * category == null: カテゴリ選択のルート画面。category != null: そのカテゴリのコスメティック一覧画面。
 * 各カテゴリの項目数(最大7)が ITEM_SLOTS に収まるため、EcoTPのような複数ページ対応は不要。
 */
public class ShopHolder implements InventoryHolder {

    public static final int SLOT_CATEGORY_PARTICLE = 11;
    public static final int SLOT_CATEGORY_JOIN = 13;
    public static final int SLOT_CATEGORY_TITLE = 15;
    public static final int SLOT_CLOSE_ROOT = 22;

    public static final int[] ITEM_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    public static final int SLOT_BACK = 18;
    public static final int SLOT_CLOSE = 22;

    private final EcoCosmeticsPlugin plugin;
    private final UUID viewerId;
    private final Category category;
    private final Inventory inventory;

    public ShopHolder(EcoCosmeticsPlugin plugin, UUID viewerId, Category category) {
        this.plugin = plugin;
        this.viewerId = viewerId;
        this.category = category;
        this.inventory = category == null ? buildRoot() : buildCategory(category);
    }

    public Category getCategory() {
        return category;
    }

    public boolean isRoot() {
        return category == null;
    }

    private Inventory buildRoot() {
        Inventory inv = plugin.getServer().createInventory(this, 27, plugin.messages().get("shop.title"));
        fillBorder(inv);
        inv.setItem(SLOT_CATEGORY_PARTICLE, categoryIcon(Material.BLAZE_POWDER, plugin.messages().get("shop.category-particle")));
        inv.setItem(SLOT_CATEGORY_JOIN, categoryIcon(Material.FIREWORK_ROCKET, plugin.messages().get("shop.category-join")));
        inv.setItem(SLOT_CATEGORY_TITLE, categoryIcon(Material.NAME_TAG, plugin.messages().get("shop.category-title")));
        inv.setItem(SLOT_CLOSE_ROOT, closeIcon());
        return inv;
    }

    private Inventory buildCategory(Category category) {
        Inventory inv = plugin.getServer().createInventory(this, 27, categoryTitle(category));
        fillBorder(inv);
        List<CosmeticDefinition> cosmetics = plugin.getCosmeticManager().getByCategory(category);
        for (int i = 0; i < cosmetics.size() && i < ITEM_SLOTS.length; i++) {
            inv.setItem(ITEM_SLOTS[i], buildCosmeticItem(cosmetics.get(i)));
        }
        inv.setItem(SLOT_BACK, backIcon());
        inv.setItem(SLOT_CLOSE, closeIcon());
        return inv;
    }

    private String categoryTitle(Category category) {
        return switch (category) {
            case PARTICLE -> plugin.messages().get("shop.category-particle");
            case JOIN_EFFECT -> plugin.messages().get("shop.category-join");
            case TITLE -> plugin.messages().get("shop.category-title");
        };
    }

    public CosmeticDefinition cosmeticAt(int slot) {
        if (category == null) {
            return null;
        }
        List<CosmeticDefinition> cosmetics = plugin.getCosmeticManager().getByCategory(category);
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            if (ITEM_SLOTS[i] == slot && i < cosmetics.size()) {
                return cosmetics.get(i);
            }
        }
        return null;
    }

    private ItemStack buildCosmeticItem(CosmeticDefinition definition) {
        ItemStack item = new ItemStack(definition.icon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(definition.displayName());
        boolean owned = plugin.getCosmeticManager().owns(viewerId, definition.id());
        boolean equipped = definition.id().equals(plugin.getCosmeticManager().getEquipped(viewerId, definition.category()));
        List<String> lore;
        if (equipped) {
            lore = List.of(plugin.messages().get("shop.owned"), plugin.messages().get("shop.equipped"));
        } else if (owned) {
            lore = List.of(plugin.messages().get("shop.owned"), plugin.messages().get("shop.equip-hint"));
        } else {
            lore = List.of(plugin.messages().get("shop.not-owned", "price", definition.price()), plugin.messages().get("shop.buy-hint"));
        }
        meta.setLore(lore);
        if (equipped) {
            meta.setEnchantmentGlintOverride(true);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack categoryIcon(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack backIcon() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(plugin.messages().get("shop.back"));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack closeIcon() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(plugin.messages().get("shop.close"));
        item.setItemMeta(meta);
        return item;
    }

    private void fillBorder(Inventory inv) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, pane);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
