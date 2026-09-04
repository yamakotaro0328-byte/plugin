package com.yamakotaro.ecopotions.gui;

import com.yamakotaro.ecopotions.EcoPotionsPlugin;
import com.yamakotaro.ecopotions.PotionDefinition;
import com.yamakotaro.ecopotions.PotionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public class GuiListener implements Listener {

    private final EcoPotionsPlugin plugin;

    public GuiListener(EcoPotionsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PotionShopHolder holder)) {
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
        if (slot == PotionShopHolder.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        PotionDefinition definition = holder.potionAt(slot);
        if (definition == null) {
            return;
        }
        PotionManager manager = plugin.getPotionManager();
        PotionManager.BuyResult result = manager.buy(player, definition.id(), 1);
        switch (result) {
            case SUCCESS -> player.sendMessage(plugin.getMessages().get("shop.bought", "name", definition.displayName(), "price", definition.price()));
            case INSUFFICIENT_FUNDS -> player.sendMessage(plugin.getMessages().get("shop.insufficient-funds", "price", definition.price()));
            case NO_ECONOMY -> player.sendMessage(plugin.getMessages().get("general.no-economy"));
            default -> {
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof PotionShopHolder) {
            event.setCancelled(true);
        }
    }
}
