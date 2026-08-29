package com.yamakotaro.ecotpquickactions;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * プレイヤー間ショップのチェストGUI。BROWSEモードは全プレイヤーの出品を並べて購入するための
 * 一覧、MYモードは自分の出品だけを表示し、空きスロットに持っているアイテムを持った状態で
 * クリックすると出品フローが始まる(数量・価格はチャット入力)。
 */
public class PlayerShopHolder implements InventoryHolder {

    public enum Mode {
        BROWSE,
        MY
    }

    private final Mode mode;
    private final Messages messages;
    private final Inventory inventory;
    private final Map<Integer, Integer> slotToListingId = new HashMap<>();

    public PlayerShopHolder(Mode mode, PlayerShopManager manager, Messages messages, Component title, UUID viewer) {
        this.mode = mode;
        this.messages = messages;
        this.inventory = Bukkit.createInventory(this, manager.size(), title);
        render(manager, viewer);
    }

    public Mode getMode() {
        return mode;
    }

    public Integer listingIdAt(int slot) {
        return slotToListingId.get(slot);
    }

    public void render(PlayerShopManager manager, UUID viewer) {
        inventory.clear();
        slotToListingId.clear();
        List<PlayerShopManager.Listing> source = mode == Mode.BROWSE
                ? manager.activeListingsForBrowse()
                : manager.listingsForSeller(viewer);

        int slot = 0;
        for (PlayerShopManager.Listing listing : source) {
            if (slot >= inventory.getSize()) {
                break;
            }
            ItemStack stack = new ItemStack(listing.material(), Math.min(listing.amount(), listing.material().getMaxStackSize()));
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                List<Component> lore = new ArrayList<>();
                lore.add(messages.get("playershop.lore.price", Map.of("price", PlayerShopManager.formatMoney(listing.pricePerUnit()))));
                lore.add(messages.get("playershop.lore.stock", Map.of("amount", String.valueOf(listing.amount()))));
                if (mode == Mode.BROWSE) {
                    lore.add(messages.get("playershop.lore.seller", Map.of("player", listing.sellerName())));
                    lore.add(messages.get("playershop.lore.browse-hint", Map.of()));
                } else {
                    lore.add(messages.get("playershop.lore.my-hint", Map.of()));
                }
                meta.lore(lore);
                stack.setItemMeta(meta);
            }
            inventory.setItem(slot, stack);
            slotToListingId.put(slot, listing.id());
            slot++;
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
