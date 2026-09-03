package com.yamakotaro.ecotp.gui;

import com.yamakotaro.ecotp.ChatUtil;
import com.yamakotaro.ecotp.CostUtil;
import com.yamakotaro.ecotp.EcoTpPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * メインメニューの「ホーム」から、複数ホームを持つプレイヤー向けに開く選択GUI。
 * ホームが1つ以下しか無い場合はこのGUIを経由せず直接 /home を実行する
 * (GuiListener 参照)。
 */
public class HomeSelectHolder implements InventoryHolder {

    public static final int CONTENT_SIZE = 45;
    private static final int SIZE = 54;
    public static final int SLOT_BACK = 49;

    private final Inventory inventory;
    private final List<String> homeNames;

    public HomeSelectHolder(EcoTpPlugin plugin, Player viewer) {
        this.homeNames = plugin.getHomeManager().getHomeNames(viewer.getUniqueId());
        this.inventory = Bukkit.createInventory(this, SIZE, plugin.getMessages().get("menu.select-home-title"));

        for (int slot = CONTENT_SIZE; slot < SIZE; slot++) {
            inventory.setItem(slot, MenuItems.borderPane(slot));
        }
        inventory.setItem(SLOT_BACK, MenuItems.item(Material.ARROW, plugin.getMessages().get("menu.back"), null));

        double minFee = plugin.getConfig().getDouble("costs.distance-min-fee", 100.0);
        double blocksPerYen = plugin.getConfig().getDouble("costs.distance-blocks-per-yen", 10.0);

        int slot = 0;
        for (String name : homeNames) {
            if (slot >= CONTENT_SIZE) {
                break; // 表示しきれない分は諦める (45件以上のホームは想定外)
            }
            inventory.setItem(slot, homeItem(plugin, viewer, name, minFee, blocksPerYen));
            slot++;
        }
        MenuItems.playOpenSound(viewer);
    }

    private static ItemStack homeItem(EcoTpPlugin plugin, Player viewer, String name, double minFee, double blocksPerYen) {
        String displayName = ChatUtil.color("&a" + name);
        Location home = plugin.getHomeManager().getHome(viewer.getUniqueId(), name);
        if (home == null) {
            return MenuItems.item(Material.BARRIER, displayName, plugin.getMessages().getList("menu.lore.home-entry-missing"));
        }
        double fee = plugin.getTeleportSafetyManager().isSameDimension(viewer.getLocation(), home)
                ? CostUtil.distanceCost(viewer.getLocation(), home, minFee, blocksPerYen)
                : minFee;
        List<String> lore = plugin.getMessages().getList("menu.lore.home-entry-fee", "fee", ChatUtil.formatMoney(fee));
        return MenuItems.item(Material.RED_BED, displayName, lore);
    }

    /** @return このスロットに対応するホーム名。範囲外なら null。 */
    public String nameAt(int slot) {
        return slot >= 0 && slot < homeNames.size() ? homeNames.get(slot) : null;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
