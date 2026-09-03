package com.yamakotaro.ecotp.listeners;

import com.yamakotaro.ecotp.EcoTpPlugin;
import com.yamakotaro.ecotp.gui.MainMenuHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * メニューアイテム (コンパス) の配布と使用を扱う。右クリックで /menu と同じGUIを開く
 * (feature/permission チェックは MenuCommand と同じ) - MenuItemManager 参照。
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
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!plugin.getMenuItemManager().isMenuItem(event.getItem())) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
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
}
