package com.yamakotaro.ecotp.gui;

import com.yamakotaro.ecotp.BalanceEntry;
import com.yamakotaro.ecotp.EcoTpEconomy;
import com.yamakotaro.ecotp.EcoTpPlugin;
import com.yamakotaro.ecotp.TpaManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class GuiListener implements Listener {

    private final EcoTpPlugin plugin;

    public GuiListener(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof MainMenuHolder || holder instanceof PlayerSelectHolder
                || holder instanceof HomeSelectHolder || holder instanceof BaltopHolder
                || holder instanceof AmountSelectHolder || holder instanceof IncomingRequestHolder) {
            // これらのGUIは表示専用: ドラッグでアイテムを置かせない
            // (空いたスロットにアイテムをドロップされて紛失するのを防ぐ)。
            event.setCancelled(true);
        }
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
                backToMainMenu(player);
                return;
            }
            if (slot >= PlayerSelectHolder.CONTENT_SIZE) {
                return; // 下段の枠をクリックしただけ
            }
            handlePlayerSelectClick(player, selectHolder, event.getCurrentItem());
            return;
        }

        if (holder instanceof HomeSelectHolder homeHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getInventory()) {
                return;
            }
            int slot = event.getSlot();
            if (slot == HomeSelectHolder.SLOT_BACK) {
                backToMainMenu(player);
                return;
            }
            if (slot >= HomeSelectHolder.CONTENT_SIZE) {
                return;
            }
            String homeName = homeHolder.nameAt(slot);
            if (homeName == null) {
                return;
            }
            player.closeInventory();
            player.performCommand("home " + homeName);
            return;
        }

        if (holder instanceof BaltopHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getInventory()) {
                return;
            }
            if (event.getSlot() == BaltopHolder.SLOT_BACK) {
                backToMainMenu(player);
            }
            // それ以外 (ランキングの頭アイテム) は表示専用でクリックしても何も起きない。
            return;
        }

        if (holder instanceof AmountSelectHolder amountHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getInventory()) {
                return;
            }
            handleAmountSelectClick(player, amountHolder, event.getSlot());
            return;
        }

        if (holder instanceof IncomingRequestHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getInventory()) {
                return;
            }
            int slot = event.getSlot();
            if (slot == IncomingRequestHolder.SLOT_ACCEPT) {
                player.closeInventory();
                player.performCommand("tpaccept");
            } else if (slot == IncomingRequestHolder.SLOT_DENY) {
                player.closeInventory();
                player.performCommand("tpdeny");
            } else if (slot == IncomingRequestHolder.SLOT_BACK) {
                backToMainMenu(player);
            }
        }
    }

    private void handleAmountSelectClick(Player player, AmountSelectHolder holder, int slot) {
        String commandName = holder.isDonate() ? "donate" : "pay";
        String targetName = holder.getTargetName();

        if (slot == AmountSelectHolder.SLOT_BACK) {
            player.openInventory(new PlayerSelectHolder(plugin, player,
                    holder.isDonate() ? PlayerSelectHolder.Purpose.DONATE : PlayerSelectHolder.Purpose.PAY).getInventory());
            return;
        }
        if (slot == AmountSelectHolder.SLOT_CUSTOM) {
            player.closeInventory();
            player.sendMessage(plugin.msg("menu.chat-input-amount"));
            plugin.getChatInputManager().request(player, input -> {
                String amount = input.trim();
                if (!amount.matches("\\d+(\\.\\d+)?")) {
                    player.sendMessage(plugin.getMessages().get("pay.invalid-amount"));
                    return;
                }
                player.performCommand(commandName + " " + targetName + " " + amount);
            });
            return;
        }
        double preset = AmountSelectHolder.presetAmountForSlot(slot);
        if (preset > 0) {
            player.closeInventory();
            player.performCommand(commandName + " " + targetName + " " + Math.round(preset));
        }
    }

    private void backToMainMenu(Player player) {
        player.openInventory(new MainMenuHolder(plugin, player).getInventory());
    }

    private void handleMainMenuClick(Player player, int slot) {
        switch (slot) {
            case MainMenuHolder.SLOT_INCOMING_REQUEST -> openIncomingRequest(player);
            case MainMenuHolder.SLOT_HOME -> handleHomeClick(player);
            case MainMenuHolder.SLOT_SETHOME -> runAndClose(player, "sethome");
            case MainMenuHolder.SLOT_SPAWN -> runAndClose(player, "spawn");
            case MainMenuHolder.SLOT_TPA -> openPlayerSelectAndClose(player, PlayerSelectHolder.Purpose.TPA);
            case MainMenuHolder.SLOT_TPHERE -> openPlayerSelectAndClose(player, PlayerSelectHolder.Purpose.TPHERE);
            case MainMenuHolder.SLOT_BALANCE -> runAndClose(player, "balance");
            case MainMenuHolder.SLOT_PAY -> openPlayerSelectAndClose(player, PlayerSelectHolder.Purpose.PAY);
            case MainMenuHolder.SLOT_BALTOP -> openBaltop(player);
            case MainMenuHolder.SLOT_DAILY -> runAndClose(player, "daily");
            case MainMenuHolder.SLOT_DONATE -> openPlayerSelectAndClose(player, PlayerSelectHolder.Purpose.DONATE);
            case MainMenuHolder.SLOT_CLOSE -> player.closeInventory();
            default -> {
                // 枠 (ガラス板) や無効化された機能のスロットをクリックしただけ: 何もしない
            }
        }
    }

    private void openIncomingRequest(Player player) {
        var info = plugin.getTpaManager().getIncomingRequest(player.getUniqueId());
        if (info.isEmpty()) {
            // 通知表示後にタイムアウト/取消などで状況が変わった: 何もせず閉じるだけ
            player.closeInventory();
            return;
        }
        player.closeInventory();
        TpaManager.IncomingRequestInfo request = info.get();
        player.openInventory(new IncomingRequestHolder(plugin, player, request.type(), request.requesterName()).getInventory());
    }

    private void handleHomeClick(Player player) {
        List<String> homes = plugin.getHomeManager().getHomeNames(player.getUniqueId());
        if (homes.size() <= 1) {
            // ホームが0か1個しか無いなら、選択GUIを挟まず直接 /home を実行する。
            runAndClose(player, "home");
            return;
        }
        player.closeInventory();
        player.openInventory(new HomeSelectHolder(plugin, player).getInventory());
    }

    private void openBaltop(Player player) {
        EcoTpEconomy ecoTpEconomy = plugin.getEcoTpEconomy();
        if (ecoTpEconomy == null) {
            // 外部の経済プラグインを使っている場合は GUI 化できない (/baltop 参照)。
            runAndClose(player, "baltop");
            return;
        }
        if (!player.hasPermission("ecotp.baltop")) {
            player.closeInventory();
            player.sendMessage(plugin.msg("general.no-permission"));
            return;
        }
        int limit = plugin.getConfig().getInt("baltop-limit", 10);
        List<BalanceEntry> top = ecoTpEconomy.getTopBalances(limit);
        if (top.isEmpty()) {
            player.closeInventory();
            player.sendMessage(plugin.msg("baltop.empty"));
            return;
        }
        player.closeInventory();
        player.openInventory(new BaltopHolder(plugin, player, top).getInventory());
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

        if (holder.getPurpose() == PlayerSelectHolder.Purpose.TPA) {
            player.closeInventory();
            player.performCommand("tpa " + targetName);
            return;
        }
        if (holder.getPurpose() == PlayerSelectHolder.Purpose.TPHERE) {
            player.closeInventory();
            player.performCommand("tphere " + targetName);
            return;
        }

        // pay/donate: 送金先を選んだので、次は金額のクイック選択GUIを開く。
        boolean donate = holder.getPurpose() == PlayerSelectHolder.Purpose.DONATE;
        player.openInventory(new AmountSelectHolder(plugin, player, targetName, donate).getInventory());
    }
}
