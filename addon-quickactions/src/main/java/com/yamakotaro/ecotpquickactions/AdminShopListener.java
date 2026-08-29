package com.yamakotaro.ecotpquickactions;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class AdminShopListener implements Listener {

    private final AdminShopManager manager;
    private final Messages messages;

    public AdminShopListener(AdminShopManager manager, Messages messages) {
        this.manager = manager;
        this.messages = messages;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AdminShopHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() != event.getInventory()) {
            return; // プレイヤー側のインベントリのクリックは無視 (アイテムを取られないように)
        }
        int slot = event.getSlot();

        if (holder.getMode() == AdminShopHolder.Mode.ADMIN) {
            handleAdminClick(player, holder, slot);
            return;
        }

        if (event.isRightClick()) {
            manager.sell(player, slot, event.isShiftClick());
        } else {
            manager.buy(player, slot, event.isShiftClick());
        }
    }

    private void handleAdminClick(Player player, AdminShopHolder holder, int slot) {
        if (manager.get(slot) != null) {
            manager.remove(slot);
            player.sendMessage(messages.get("adminshop.removed", Map.of("slot", String.valueOf(slot))));
            holder.render(manager);
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            player.sendMessage(messages.get("adminshop.hold-item-to-add", Map.of()));
            return;
        }
        manager.setItem(slot, hand.getType());
        player.sendMessage(messages.get("adminshop.added", Map.of(
                "material", hand.getType().name(),
                "slot", String.valueOf(slot))));
        holder.render(manager);
    }
}
