package com.yamakotaro.ecotpquickactions;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Map;

/**
 * アドミンショップのGUI操作。出品(アイテムの追加・削除・価格設定)はすべてGUI内で完結し、
 * コマンドを打つ必要はない。
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
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!(event.getInventory().getHolder() instanceof PriceInputHolder)) {
            return;
        }
        // バニラの修理費用計算に関係なく、常にリネーム後の内容をそのまま結果スロットに反映する。
        ItemStack input = event.getInventory().getItem(0);
        event.setResult(input == null ? null : input.clone());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof PriceInputHolder priceHolder) {
            handlePriceInputClick(event, priceHolder);
            return;
        }
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
        String currentValue = current == null ? "-" : String.valueOf(current);
        Component title = messages.get(buy ? "adminshop.set-buy-price" : "adminshop.set-sell-price", Map.of());
        var priceInput = new PriceInputHolder(slot, buy, title, existing.getMaterial(), currentValue);
        // クリック処理中に別のインベントリを開くと不具合が起きることがあるため、次のtickにずらす。
        Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(priceInput.getInventory()));
    }

    private void handlePriceInputClick(InventoryClickEvent event, PriceInputHolder holder) {
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getInventory() || event.getSlot() != 2) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack result = event.getCurrentItem();
        if (result == null || !result.hasItemMeta() || !result.getItemMeta().hasDisplayName()) {
            return;
        }
        String raw = result.getItemMeta().getDisplayName();
        player.closeInventory();

        Double parsed;
        if (raw.equals("-")) {
            parsed = null;
        } else {
            try {
                parsed = Double.parseDouble(raw);
            } catch (NumberFormatException e) {
                player.sendMessage(messages.get("adminshop.price-invalid-number", Map.of()));
                return;
            }
        }

        boolean updated = holder.isBuy()
                ? manager.setBuyPrice(holder.getShopSlot(), parsed)
                : manager.setSellPrice(holder.getShopSlot(), parsed);
        if (!updated) {
            return; // その間にスロットが削除された等
        }
        String messageKey;
        Map<String, String> placeholders;
        if (parsed == null) {
            messageKey = holder.isBuy() ? "adminshop.buy-price-disabled" : "adminshop.sell-price-disabled";
            placeholders = Map.of();
        } else {
            messageKey = holder.isBuy() ? "adminshop.buy-price-set" : "adminshop.sell-price-set";
            placeholders = Map.of("price", String.valueOf(parsed));
        }
        player.sendMessage(messages.get(messageKey, placeholders));
    }
}
