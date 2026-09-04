package com.yamakotaro.ecocosmetics.gui;

import com.yamakotaro.ecocosmetics.Category;
import com.yamakotaro.ecocosmetics.CosmeticDefinition;
import com.yamakotaro.ecocosmetics.CosmeticManager;
import com.yamakotaro.ecocosmetics.EcoCosmeticsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public class GuiListener implements Listener {

    private final EcoCosmeticsPlugin plugin;

    public GuiListener(EcoCosmeticsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }
        int slot = event.getSlot();

        if (holder.isRoot()) {
            if (slot == ShopHolder.SLOT_CATEGORY_PARTICLE) {
                open(player, Category.PARTICLE);
            } else if (slot == ShopHolder.SLOT_CATEGORY_JOIN) {
                open(player, Category.JOIN_EFFECT);
            } else if (slot == ShopHolder.SLOT_CATEGORY_TITLE) {
                open(player, Category.TITLE);
            } else if (slot == ShopHolder.SLOT_CLOSE_ROOT) {
                player.closeInventory();
            }
            return;
        }

        if (slot == ShopHolder.SLOT_BACK) {
            open(player, null);
            return;
        }
        if (slot == ShopHolder.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }

        CosmeticDefinition definition = holder.cosmeticAt(slot);
        if (definition == null) {
            return;
        }
        handleCosmeticClick(player, definition);
        open(player, holder.getCategory());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ShopHolder) {
            event.setCancelled(true);
        }
    }

    private void handleCosmeticClick(Player player, CosmeticDefinition definition) {
        CosmeticManager manager = plugin.getCosmeticManager();
        if (manager.owns(player.getUniqueId(), definition.id())) {
            manager.toggleEquip(player.getUniqueId(), definition.id());
            boolean equipped = definition.id().equals(manager.getEquipped(player.getUniqueId(), definition.category()));
            player.sendMessage(plugin.messages().get(equipped ? "shop.equipped-message" : "shop.unequipped-message", "name", definition.displayName()));
            return;
        }
        CosmeticManager.BuyResult result = manager.buy(player, definition.id());
        switch (result) {
            case SUCCESS -> player.sendMessage(plugin.messages().get("shop.bought", "name", definition.displayName(), "price", definition.price()));
            case INSUFFICIENT_FUNDS -> player.sendMessage(plugin.messages().get("shop.insufficient-funds", "price", definition.price()));
            case NO_ECONOMY -> player.sendMessage(plugin.messages().get("general.no-economy"));
            default -> {
            }
        }
    }

    private void open(Player player, Category category) {
        ShopHolder holder = new ShopHolder(plugin, player.getUniqueId(), category);
        player.openInventory(holder.getInventory());
    }
}
