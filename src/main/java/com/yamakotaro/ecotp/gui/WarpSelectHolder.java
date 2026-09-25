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
 * メインメニューの「ワープ」から開くワープ地点の選択GUI。クリックで /warp <名前> を実行する。
 */
public class WarpSelectHolder implements InventoryHolder {

    public static final int CONTENT_SIZE = 45;
    private static final int SIZE = 54;
    public static final int SLOT_PREV = 48;
    public static final int SLOT_BACK = 49;
    public static final int SLOT_NEXT = 50;
    private static final int SLOT_PAGE_INDICATOR = 51;

    private final Inventory inventory;
    private final List<String> pageNames;
    private final int page;

    public WarpSelectHolder(EcoTpPlugin plugin, Player viewer, int page) {
        List<String> allNames = plugin.getWarpManager().getWarpNames();
        this.inventory = Bukkit.createInventory(this, SIZE, plugin.getMessages().get("menu.select-warp-title"));

        for (int slot = CONTENT_SIZE; slot < SIZE; slot++) {
            inventory.setItem(slot, MenuItems.borderPane(slot));
        }

        Paginator<String> paginator = new Paginator<>(allNames, CONTENT_SIZE);
        this.page = Math.max(0, Math.min(page, paginator.pageCount() - 1));
        this.pageNames = paginator.page(this.page);

        if (paginator.hasPrevPage(this.page)) {
            inventory.setItem(SLOT_PREV, MenuItems.prevPageItem(plugin));
        }
        if (paginator.hasNextPage(this.page)) {
            inventory.setItem(SLOT_NEXT, MenuItems.nextPageItem(plugin));
        }
        if (paginator.pageCount() > 1) {
            inventory.setItem(SLOT_PAGE_INDICATOR, MenuItems.pageIndicatorItem(plugin, this.page, paginator.pageCount()));
        }
        inventory.setItem(SLOT_BACK, MenuItems.item(Material.ARROW, plugin.getMessages().get("menu.back"), null));

        double minFee = plugin.getConfig().getDouble("costs.distance-min-fee", 100.0);
        double blocksPerYen = plugin.getConfig().getDouble("costs.distance-blocks-per-yen", 10.0);

        int slot = 0;
        for (String name : pageNames) {
            inventory.setItem(slot, warpItem(plugin, viewer, name, minFee, blocksPerYen));
            slot++;
        }
        MenuItems.playOpenSound(viewer);
    }

    private static ItemStack warpItem(EcoTpPlugin plugin, Player viewer, String name, double minFee, double blocksPerYen) {
        String displayName = ChatUtil.color("&b" + name);
        Location warp = plugin.getWarpManager().getWarp(name);
        if (warp == null) {
            return MenuItems.item(Material.BARRIER, displayName, plugin.getMessages().getList("menu.lore.warp-entry-missing"));
        }
        double fee = plugin.getTeleportSafetyManager().isSameDimension(viewer.getLocation(), warp)
                ? CostUtil.distanceCost(viewer.getLocation(), warp, minFee, blocksPerYen)
                : minFee;
        List<String> lore = plugin.getMessages().getList("menu.lore.home-entry-fee", "fee", ChatUtil.formatMoney(fee));
        return MenuItems.item(Material.LODESTONE, displayName, lore);
    }

    /** @return このスロットに対応するワープ名 (現在のページ内)。範囲外なら null。 */
    public String nameAt(int slot) {
        return slot >= 0 && slot < pageNames.size() ? pageNames.get(slot) : null;
    }

    public int getPage() {
        return page;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
