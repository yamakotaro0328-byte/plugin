package com.yamakotaro.ecopotions.gui;

import com.yamakotaro.ecopotions.EcoPotionsPlugin;
import com.yamakotaro.ecopotions.PotionDefinition;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** カタログの全ポーション(最大7種、ITEM_SLOTSに収まる)を一覧表示する単一画面のショップ。 */
public class PotionShopHolder implements InventoryHolder {

    public static final int[] ITEM_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    public static final int SLOT_CLOSE = 22;

    private final EcoPotionsPlugin plugin;
    private final Inventory inventory;
    private final List<PotionDefinition> potions;

    public PotionShopHolder(EcoPotionsPlugin plugin) {
        this.plugin = plugin;
        this.potions = new ArrayList<>(plugin.getPotionManager().getCatalog().values());
        this.inventory = build();
    }

    private Inventory build() {
        Inventory inv = plugin.getServer().createInventory(this, 27, plugin.getMessages().get("shop.title"));
        fillBorder(inv);
        for (int i = 0; i < potions.size() && i < ITEM_SLOTS.length; i++) {
            inv.setItem(ITEM_SLOTS[i], buildIcon(potions.get(i)));
        }
        inv.setItem(SLOT_CLOSE, closeIcon());
        return inv;
    }

    public PotionDefinition potionAt(int slot) {
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            if (ITEM_SLOTS[i] == slot && i < potions.size()) {
                return potions.get(i);
            }
        }
        return null;
    }

    private ItemStack buildIcon(PotionDefinition definition) {
        // 実際に渡されるアイテムそのものをアイコンとして使い、価格と購入案内だけ追加する。
        ItemStack item = plugin.getPotionManager().createItem(definition);
        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>(meta.getLore() == null ? List.of() : meta.getLore());
        lore.add("");
        lore.add(plugin.getMessages().get("shop.price-line", "price", definition.price()));
        lore.add(plugin.getMessages().get("shop.buy-hint"));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack closeIcon() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(plugin.getMessages().get("shop.close"));
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
