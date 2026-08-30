package com.yamakotaro.ecotpquickactions;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Map;

/**
 * アドミンショップのGUI操作。出品(アイテムの追加・削除)も価格の設定も、チャット入力を
 * 一切使わずGUI内のクリックだけで完結する(+/-ボタンで金額を決めるNumberInputHolder経由)。
 */
public class AdminShopListener implements Listener {

    private final Plugin plugin;
    private final AdminShopManager manager;
    private final Messages messages;

    public AdminShopListener(Plugin plugin, AdminShopManager manager, Messages messages) {
        this.plugin = plugin;
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
            handleAdminClick(player, holder, event);
            return;
        }

        if (event.isRightClick()) {
            manager.sell(player, slot, event.isShiftClick());
        } else {
            manager.buy(player, slot, event.isShiftClick());
        }
    }

    private void handleAdminClick(Player player, AdminShopHolder holder, InventoryClickEvent event) {
        int slot = event.getSlot();
        ShopItem existing = manager.get(slot);
        if (existing == null) {
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
            return;
        }

        if (event.isShiftClick()) {
            manager.remove(slot);
            player.sendMessage(messages.get("adminshop.removed", Map.of("slot", String.valueOf(slot))));
            holder.render(manager);
            return;
        }

        boolean buy = !event.isRightClick();
        Double current = buy ? existing.getBuyPrice() : existing.getSellPrice();
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(new NumberInputHolder(
                messages,
                messages.get(buy ? "adminshop.buy-price-title" : "adminshop.sell-price-title", Map.of()),
                current != null ? current : 0.0,
                0.0, null, true,
                value -> applyPrice(player, slot, buy, value),
                () -> applyPrice(player, slot, buy, null),
                () -> reopenAdmin(player)
        ).getInventory()));
    }

    private void applyPrice(Player player, int slot, boolean buy, Double price) {
        boolean updated = buy ? manager.setBuyPrice(slot, price) : manager.setSellPrice(slot, price);
        if (updated) {
            String messageKey;
            Map<String, String> placeholders;
            if (price == null) {
                messageKey = buy ? "adminshop.buy-price-disabled" : "adminshop.sell-price-disabled";
                placeholders = Map.of();
            } else {
                messageKey = buy ? "adminshop.buy-price-set" : "adminshop.sell-price-set";
                placeholders = Map.of("price", String.valueOf(price));
            }
            player.sendMessage(messages.get(messageKey, placeholders));
        }
        reopenAdmin(player);
    }

    private void reopenAdmin(Player player) {
        // 直前のインベントリを閉じた直後に開くと不具合が起きることがあるため、次のtickにずらす。
        Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(
                new AdminShopHolder(AdminShopHolder.Mode.ADMIN, manager, messages,
                        messages.get("adminshop.admin-title", Map.of())).getInventory()));
    }
}
