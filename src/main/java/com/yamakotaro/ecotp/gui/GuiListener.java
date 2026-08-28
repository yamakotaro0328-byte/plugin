package com.yamakotaro.ecotp.gui;

import com.yamakotaro.ecotp.EcoTpPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class GuiListener implements Listener {

    private final EcoTpPlugin plugin;

    public GuiListener(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (holder instanceof MainMenuHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getInventory()) {
                return; // 自分の持ち物側のクリックは無視 (アイテムを取られないように)
            }
            handleMainMenuClick(player, event.getSlot());
            return;
        }

        if (holder instanceof PlayerSelectHolder selectHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getInventory()) {
                return;
            }
            handlePlayerSelectClick(player, selectHolder, event.getCurrentItem());
        }
    }

    private void handleMainMenuClick(Player player, int slot) {
        player.closeInventory();
        switch (slot) {
            case MainMenuHolder.SLOT_HOME -> player.performCommand("home");
            case MainMenuHolder.SLOT_SETHOME -> player.performCommand("sethome");
            case MainMenuHolder.SLOT_SPAWN -> player.performCommand("spawn");
            case MainMenuHolder.SLOT_TPA -> openPlayerSelect(player, PlayerSelectHolder.Purpose.TPA);
            case MainMenuHolder.SLOT_TPHERE -> openPlayerSelect(player, PlayerSelectHolder.Purpose.TPHERE);
            case MainMenuHolder.SLOT_BALANCE -> player.performCommand("balance");
            case MainMenuHolder.SLOT_PAY -> openPlayerSelect(player, PlayerSelectHolder.Purpose.PAY);
            case MainMenuHolder.SLOT_BALTOP -> player.performCommand("baltop");
            default -> {
                // 閉じるボタン等: closeInventory 済みなので何もしなくてよい
            }
        }
    }

    private void openPlayerSelect(Player player, PlayerSelectHolder.Purpose purpose) {
        if (Bukkit.getOnlinePlayers().size() <= 1) {
            player.sendMessage(plugin.msg("menu.no-players-online"));
            return;
        }
        player.openInventory(new PlayerSelectHolder(plugin, player, purpose).getInventory());
    }

    private void handlePlayerSelectClick(Player player, PlayerSelectHolder holder, ItemStack clicked) {
        if (clicked == null) {
            return;
        }
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return;
        }
        String targetName = meta.getDisplayName();
        player.closeInventory();

        if (holder.getPurpose() == PlayerSelectHolder.Purpose.TPA) {
            player.performCommand("tpa " + targetName);
            return;
        }
        if (holder.getPurpose() == PlayerSelectHolder.Purpose.TPHERE) {
            player.performCommand("tphere " + targetName);
            return;
        }

        // 送金先を選んだので、金額はチャットで入力してもらう。
        player.sendMessage(plugin.msg("menu.chat-input-amount"));
        plugin.getChatInputManager().request(player, input -> {
            String amount = input.trim();
            if (!amount.matches("\\d+(\\.\\d+)?")) {
                player.sendMessage(plugin.getMessages().get("pay.invalid-amount"));
                return;
            }
            player.performCommand("pay " + targetName + " " + amount);
        });
    }
}
