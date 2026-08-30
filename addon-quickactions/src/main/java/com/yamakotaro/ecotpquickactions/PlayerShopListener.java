package com.yamakotaro.ecotpquickactions;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

import java.util.Map;

/**
 * プレイヤー間ショップのGUI操作。出品(数量・価格)も購入もチャット入力を一切使わず、
 * GUI内のクリックだけで完結する(数量・価格は+/-ボタンで決めるNumberInputHolder経由)。
 * 出品するアイテムはMaterialではなく実際のItemStack(ItemMeta込み)として扱うため、
 * Novaのような見た目や中身をItemMetaで変えるプラグインのアイテムも正しく出品できる。
 */
public class PlayerShopListener implements Listener {

    private final Plugin plugin;
    private final PlayerShopManager manager;
    private final Messages messages;

    public PlayerShopListener(Plugin plugin, PlayerShopManager manager, Messages messages) {
        this.plugin = plugin;
        this.manager = manager;
        this.messages = messages;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PlayerShopHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() != event.getInventory()) {
            return;
        }
        Integer listingId = holder.listingIdAt(event.getSlot());

        if (holder.getMode() == PlayerShopHolder.Mode.MY) {
            if (listingId != null) {
                handleRemove(player, holder, listingId);
            } else {
                handleStartListing(player);
            }
            return;
        }

        if (listingId != null) {
            handleBuy(player, holder, listingId, event.isShiftClick());
        }
    }

    private void handleRemove(Player player, PlayerShopHolder holder, int listingId) {
        PlayerShopManager.RemoveResult result = manager.removeListing(player, listingId);
        if (result == PlayerShopManager.RemoveResult.SUCCESS) {
            player.sendMessage(messages.get("playershop.removed", Map.of()));
        } else {
            player.sendMessage(messages.get("playershop.remove-failed", Map.of()));
        }
        holder.render(manager, player.getUniqueId());
    }

    private void handleBuy(Player player, PlayerShopHolder holder, int listingId, boolean shiftClick) {
        int amount = shiftClick ? Integer.MAX_VALUE : 1;
        PlayerShopManager.BuyResult result = manager.buy(player, listingId, amount);
        switch (result) {
            case NOT_FOUND -> player.sendMessage(messages.get("playershop.not-found", Map.of()));
            case CANNOT_BUY_OWN -> player.sendMessage(messages.get("playershop.cannot-buy-own", Map.of()));
            case INSUFFICIENT_STOCK -> player.sendMessage(messages.get("playershop.insufficient-stock", Map.of()));
            case NO_ECONOMY -> player.sendMessage(messages.get("playershop.no-economy", Map.of()));
            case INSUFFICIENT_FUNDS -> player.sendMessage(messages.get("playershop.insufficient-funds", Map.of()));
            case INVENTORY_FULL -> player.sendMessage(messages.get("playershop.inventory-full", Map.of()));
            // The listing may already be gone from the map by now (fully sold out), so this is a
            // generic confirmation rather than one that reaches into the listing for its details.
            case SUCCESS -> player.sendMessage(messages.get("playershop.bought-generic", Map.of()));
        }
        holder.render(manager, player.getUniqueId());
    }

    private void handleStartListing(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            player.sendMessage(messages.get("playershop.hold-item-to-list", Map.of()));
            return;
        }
        ItemStack template = hand.clone();
        template.setAmount(1);
        int have = countHeld(player.getInventory(), template);
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(new NumberInputHolder(
                messages,
                messages.get("playershop.amount-title", Map.of("material", template.getType().name(), "have", String.valueOf(have))),
                Math.min(1, have), 1, (double) have, false,
                amount -> openPriceInput(player, template, have, amount.intValue()),
                null,
                () -> reopenMy(player)
        ).getInventory()));
    }

    private void openPriceInput(Player player, ItemStack template, int have, int amount) {
        Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(new NumberInputHolder(
                messages,
                messages.get("playershop.price-title", Map.of("amount", String.valueOf(amount), "material", template.getType().name())),
                1.0, 0.0, null, false,
                price -> finishListing(player, template, amount, price),
                null,
                () -> reopenMy(player)
        ).getInventory()));
    }

    private void finishListing(Player player, ItemStack template, int amount, double price) {
        int have = countHeld(player.getInventory(), template);
        if (amount <= 0 || amount > have) {
            player.sendMessage(messages.get("playershop.amount-invalid-range", Map.of("have", String.valueOf(have))));
            reopenMy(player);
            return;
        }
        PlayerShopManager.ListResult result = manager.createListing(player, template, amount, price);
        if (result == PlayerShopManager.ListResult.SUCCESS) {
            player.sendMessage(messages.get("playershop.listed", Map.of(
                    "amount", String.valueOf(amount),
                    "material", template.getType().name(),
                    "price", PlayerShopManager.formatMoney(price))));
        } else {
            player.sendMessage(messages.get("playershop.list-failed", Map.of("material", template.getType().name())));
        }
        reopenMy(player);
    }

    private void reopenMy(Player player) {
        // インベントリ切り替え直後の不具合を避けるため、次のtickにずらす。
        Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(
                new PlayerShopHolder(PlayerShopHolder.Mode.MY, manager, messages,
                        messages.get("playershop.my-title", Map.of()), player.getUniqueId()).getInventory()));
    }

    private static int countHeld(PlayerInventory inventory, ItemStack template) {
        int count = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack != null && stack.isSimilar(template)) {
                count += stack.getAmount();
            }
        }
        return count;
    }
}
