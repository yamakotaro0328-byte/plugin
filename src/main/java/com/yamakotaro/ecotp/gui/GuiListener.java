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
            int slot = event.getSlot();
            if (slot == PlayerSelectHolder.SLOT_BACK) {
                player.openInventory(new MainMenuHolder(plugin, player).getInventory());
                return;
            }
            if (slot >= PlayerSelectHolder.CONTENT_SIZE) {
                return; // 下段の枠をクリックしただけ
            }
            handlePlayerSelectClick(player, selectHolder, event.getCurrentItem());
        }
    }

    private void handleMainMenuClick(Player player, int slot) {
        switch (slot) {
            case MainMenuHolder.SLOT_HOME -> runAndClose(player, "home");
            case MainMenuHolder.SLOT_SETHOME -> runAndClose(player, "sethome");
            case MainMenuHolder.SLOT_SPAWN -> runAndClose(player, "spawn");
            case MainMenuHolder.SLOT_TPA -> openPlayerSelectAndClose(player, PlayerSelectHolder.Purpose.TPA);
            case MainMenuHolder.SLOT_TPHERE -> openPlayerSelectAndClose(player, PlayerSelectHolder.Purpose.TPHERE);
            case MainMenuHolder.SLOT_BALANCE -> runAndClose(player, "balance");
            case MainMenuHolder.SLOT_PAY -> openPlayerSelectAndClose(player, PlayerSelectHolder.Purpose.PAY);
            case MainMenuHolder.SLOT_BALTOP -> runAndClose(player, "baltop");
            case MainMenuHolder.SLOT_DAILY -> runAndClose(player, "daily");
            case MainMenuHolder.SLOT_DONATE -> openPlayerSelectAndClose(player, PlayerSelectHolder.Purpose.DONATE);
            case MainMenuHolder.SLOT_CLOSE -> player.closeInventory();
            default -> {
                // 枠 (ガラス板) や無効化された機能のスロットをクリックしただけ: 何もしない
            }
        }
    }

    private void runAndClose(Player player, String command) {
        player.closeInventory();
        player.performCommand(command);
    }

    private void openPlayerSelectAndClose(Player player, PlayerSelectHolder.Purpose purpose) {
        player.closeInventory();
        openPlayerSelect(player, purpose);
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

        // pay/donate: 送金先を選んだので、金額はチャットで入力してもらう。
        String commandName = holder.getPurpose() == PlayerSelectHolder.Purpose.DONATE ? "donate" : "pay";
        player.sendMessage(plugin.msg("menu.chat-input-amount"));
        plugin.getChatInputManager().request(player, input -> {
            String amount = input.trim();
            if (!amount.matches("\\d+(\\.\\d+)?")) {
                player.sendMessage(plugin.getMessages().get("pay.invalid-amount"));
                return;
            }
            player.performCommand(commandName + " " + targetName + " " + amount);
        });
    }
}
