package com.yamakotaro.ecotp.listeners;

import com.yamakotaro.ecotp.EcoTpPlugin;
import com.yamakotaro.ecotp.gui.MainMenuHolder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;

/**
 * メニューアイテム (コンパス) の配布・使用を扱う。右クリックで /menu と同じGUIを開く
 * (feature/permission チェックは MenuCommand と同じ) - MenuItemManager 参照。
 *
 * 通常の右クリック使用 (ロードストーンに追従させる等、羅針盤本来の機能) は右クリックを
 * 常に無効化していることで結果的に一切発生しない。それ以外は普通のアイテムとして
 * ドロップ・保管・取引などを行えるが、クラフト (自分の2x2欄・作業台の3x3欄への投入) だけは
 * 別途禁止する: 何かのレシピの材料になって消費されてしまうのを防ぐため。
 */
public class MenuItemListener implements Listener {

    private final EcoTpPlugin plugin;

    public MenuItemListener(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getMenuItemManager().giveOnJoinIfMissing(event.getPlayer());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        // オフハンドでも同じイベントが飛んでくるため、二重処理を避けるためメインハンドのみ扱う。
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!plugin.getMenuItemManager().isMenuItem(event.getItem())) {
            return;
        }
        // メニューを開く以外の右クリック用途 (ロードストーン追従の設定など) を一切許可しない。
        event.setCancelled(true);
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        var player = event.getPlayer();
        plugin.getMenuItemManager().refreshExisting(player);
        if (!plugin.isFeatureEnabled("menu")) {
            player.sendMessage(plugin.msg("general.feature-disabled"));
            return;
        }
        if (!player.hasPermission("ecotp.menu")) {
            player.sendMessage(plugin.msg("general.no-permission"));
            return;
        }
        player.openInventory(new MainMenuHolder(plugin, player).getInventory());
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (containsMenuItem(event.getInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        boolean involvesMenuItem = plugin.getMenuItemManager().isMenuItem(event.getCurrentItem())
                || plugin.getMenuItemManager().isMenuItem(event.getCursor());
        if (!involvesMenuItem) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!(top instanceof CraftingInventory)) {
            return;
        }
        // slot 0 は完成品スロットなので、材料欄 (1以降) への出し入れだけを禁止する。
        int rawSlot = event.getRawSlot();
        boolean targetsMatrix = rawSlot >= 1 && rawSlot < top.getSize();
        boolean shiftClickIntoMatrix = event.isShiftClick()
                && event.getClickedInventory() != null
                && !event.getClickedInventory().equals(top);
        if (targetsMatrix || shiftClickIntoMatrix) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!plugin.getMenuItemManager().isMenuItem(event.getOldCursor())) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!(top instanceof CraftingInventory)) {
            return;
        }
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 1 && rawSlot < top.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private boolean containsMenuItem(CraftingInventory inventory) {
        for (var stack : inventory.getMatrix()) {
            if (plugin.getMenuItemManager().isMenuItem(stack)) {
                return true;
            }
        }
        return false;
    }
}
