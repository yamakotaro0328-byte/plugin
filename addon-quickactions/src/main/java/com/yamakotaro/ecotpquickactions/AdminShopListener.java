package com.yamakotaro.ecotpquickactions;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.text.Normalizer;
import java.util.Map;

/**
 * アドミンショップのGUI操作。出品(アイテムの追加・削除)はGUI内のクリックだけで完結し、
 * 価格の入力だけはチャットで行う(EcoTP本体の/pay金額入力と同じ方式)。
 */
public class AdminShopListener implements Listener {

    private final Plugin plugin;
    private final AdminShopManager manager;
    private final Messages messages;
    private final ChatInputManager chatInputManager;

    public AdminShopListener(Plugin plugin, AdminShopManager manager, Messages messages, ChatInputManager chatInputManager) {
        this.plugin = plugin;
        this.manager = manager;
        this.messages = messages;
        this.chatInputManager = chatInputManager;
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
        player.closeInventory();
        player.sendMessage(messages.get(buy ? "adminshop.enter-buy-price" : "adminshop.enter-sell-price", Map.of()));
        chatInputManager.request(player, input -> applyPrice(player, slot, buy, input));
    }

    private void applyPrice(Player player, int slot, boolean buy, String rawInput) {
        // Japanese IME often defaults to full-width digits (１２３) unless switched to direct
        // input, which Double.parseDouble rejects outright; NFKC normalization folds those
        // (and full-width "－"/"．") down to their standard ASCII equivalents first.
        String raw = Normalizer.normalize(rawInput.trim(), Normalizer.Form.NFKC);
        Double parsed;
        if (raw.equals("-")) {
            parsed = null;
        } else {
            try {
                parsed = Double.parseDouble(raw);
            } catch (NumberFormatException e) {
                player.sendMessage(messages.get("adminshop.price-invalid-number", Map.of()));
                chatInputManager.request(player, input -> applyPrice(player, slot, buy, input));
                return;
            }
        }

        boolean updated = buy ? manager.setBuyPrice(slot, parsed) : manager.setSellPrice(slot, parsed);
        if (updated) {
            String messageKey;
            Map<String, String> placeholders;
            if (parsed == null) {
                messageKey = buy ? "adminshop.buy-price-disabled" : "adminshop.sell-price-disabled";
                placeholders = Map.of();
            } else {
                messageKey = buy ? "adminshop.buy-price-set" : "adminshop.sell-price-set";
                placeholders = Map.of("price", String.valueOf(parsed));
            }
            player.sendMessage(messages.get(messageKey, placeholders));
        }
        reopenAdmin(player);
    }

    private void reopenAdmin(Player player) {
        // チャット送信直後にインベントリを開くと不具合が起きることがあるため、次のtickにずらす。
        Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(
                new AdminShopHolder(AdminShopHolder.Mode.ADMIN, manager,
                        messages.get("adminshop.admin-title", Map.of())).getInventory()));
    }
}
