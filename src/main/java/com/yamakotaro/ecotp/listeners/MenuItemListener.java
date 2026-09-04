package com.yamakotaro.ecotp.listeners;

import com.yamakotaro.ecotp.EcoTpPlugin;
import com.yamakotaro.ecotp.MenuItemManager;
import com.yamakotaro.ecotp.gui.MainMenuHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * メニューアイテム (コンパス) の配布・使用を扱う。右クリックで /menu と同じGUIを開く
 * (feature/permission チェックは MenuCommand と同じ) - MenuItemManager 参照。
 *
 * このアイテムは「メニューを開く」以外の用途に一切使えないようにする: 通常の
 * 右クリック使用 (羅針盤としてのロードストーン追従設定等) はもちろん、ドロップ、
 * エンティティへの使用 (額縁への設置等)、そして自分のインベントリの外
 * (作業台/かまど/金床/エンチャント台/チェスト/村人取引など、あらゆる別インベントリ)
 * への移動・投入も禁止する。死亡時にドロップさせない(戦利品として奪われない)。
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
    public void onRespawn(PlayerRespawnEvent event) {
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

        Player player = event.getPlayer();
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
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (plugin.getMenuItemManager().isMenuItem(item) || plugin.getMenuItemManager().isMenuItem(event.getPlayer().getInventory().getItemInOffHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.getMenuItemManager().isMenuItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        MenuItemManager manager = plugin.getMenuItemManager();
        event.getDrops().removeIf(manager::isMenuItem);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        MenuItemManager manager = plugin.getMenuItemManager();
        boolean involvesMenuItem = manager.isMenuItem(event.getCurrentItem()) || manager.isMenuItem(event.getCursor());
        if (!involvesMenuItem) {
            return;
        }
        InventoryType topType = event.getView().getTopInventory().getType();
        if (topType == InventoryType.PLAYER || topType == InventoryType.CRAFTING) {
            // 自分のインベントリ内 (Eキー) での並べ替えだけなので許可する。
            return;
        }
        // それ以外 (作業台/金床/エンチャント台/チェスト/村人取引/EcoTP自身のGUI等) には
        // 一切出し入れさせない。EcoTP自身のメニューGUIは元々全クリックを無効化しているので
        // ここで二重にキャンセルしても問題ない。
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!plugin.getMenuItemManager().isMenuItem(event.getOldCursor())) {
            return;
        }
        InventoryType topType = event.getView().getTopInventory().getType();
        if (topType == InventoryType.PLAYER || topType == InventoryType.CRAFTING) {
            return;
        }
        event.setCancelled(true);
    }
}
