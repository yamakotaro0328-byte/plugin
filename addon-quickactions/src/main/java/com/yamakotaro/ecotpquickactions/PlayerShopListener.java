package com.yamakotaro.ecotpquickactions;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

import java.text.Normalizer;
import java.util.Map;

/**
 * プレイヤー間ショップのGUI操作。出品(数量・価格)はチャット入力(EcoTP本体の/pay金額入力や
 * アドミンショップの価格入力と同じ方式)、購入はBROWSE画面のクリックだけで完結する。
 */
public class PlayerShopListener implements Listener {

    private final Plugin plugin;
    private final PlayerShopManager manager;
    private final Messages messages;
    private final ChatInputManager chatInputManager;

    public PlayerShopListener(Plugin plugin, PlayerShopManager manager, Messages messages, ChatInputManager chatInputManager) {
        this.plugin = plugin;
        this.manager = manager;
        this.messages = messages;
        this.chatInputManager = chatInputManager;
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
        Material material = hand.getType();
        int have = countHeld(player.getInventory(), material);
        player.closeInventory();
        player.sendMessage(messages.get("playershop.enter-amount", Map.of(
                "material", material.name(), "have", String.valueOf(have))));
        chatInputManager.request(player, input -> handleAmountInput(player, material, have, input));
    }

    private void handleAmountInput(Player player, Material material, int have, String rawInput) {
        String raw = Normalizer.normalize(rawInput.trim(), Normalizer.Form.NFKC);
        int amount;
        try {
            amount = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            player.sendMessage(messages.get("playershop.amount-invalid-number", Map.of()));
            chatInputManager.request(player, input -> handleAmountInput(player, material, have, input));
            return;
        }
        if (amount <= 0 || amount > have) {
            player.sendMessage(messages.get("playershop.amount-invalid-range", Map.of("have", String.valueOf(have))));
            chatInputManager.request(player, input -> handleAmountInput(player, material, have, input));
            return;
        }
        player.sendMessage(messages.get("playershop.enter-price", Map.of()));
        chatInputManager.request(player, input -> handlePriceInput(player, material, amount, input));
    }

    private void handlePriceInput(Player player, Material material, int amount, String rawInput) {
        String raw = Normalizer.normalize(rawInput.trim(), Normalizer.Form.NFKC);
        double price;
        try {
            price = Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            player.sendMessage(messages.get("playershop.price-invalid-number", Map.of()));
            chatInputManager.request(player, input -> handlePriceInput(player, material, amount, input));
            return;
        }
        if (price <= 0) {
            player.sendMessage(messages.get("playershop.price-invalid-range", Map.of()));
            chatInputManager.request(player, input -> handlePriceInput(player, material, amount, input));
            return;
        }
        PlayerShopManager.ListResult result = manager.createListing(player, material, amount, price);
        if (result == PlayerShopManager.ListResult.SUCCESS) {
            player.sendMessage(messages.get("playershop.listed", Map.of(
                    "amount", String.valueOf(amount),
                    "material", material.name(),
                    "price", PlayerShopManager.formatMoney(price))));
        } else {
            player.sendMessage(messages.get("playershop.list-failed", Map.of("material", material.name())));
        }
        reopenMy(player);
    }

    private void reopenMy(Player player) {
        // チャット送信直後にインベントリを開くと不具合が起きることがあるため、次のtickにずらす。
        Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(
                new PlayerShopHolder(PlayerShopHolder.Mode.MY, manager, messages,
                        messages.get("playershop.my-title", Map.of()), player.getUniqueId()).getInventory()));
    }

    private static int countHeld(PlayerInventory inventory, Material material) {
        int count = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack != null && stack.getType() == material) {
                count += stack.getAmount();
            }
        }
        return count;
    }
}
