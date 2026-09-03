package com.yamakotaro.ecotp.gui;

import com.yamakotaro.ecotp.BalanceEntry;
import com.yamakotaro.ecotp.ChatUtil;
import com.yamakotaro.ecotp.EcoTpPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

/**
 * メインメニューの「ランキング」から開く、所持金上位者の一覧GUI (/baltop の見た目版)。
 * 表示専用: クリックしても何も起きない (戻るボタン以外)。45件を超える場合はページ送りできる
 * (Paginator 参照。順位は常にページをまたいだ通し番号)。
 */
public class BaltopHolder implements InventoryHolder {

    public static final int CONTENT_SIZE = 45;
    private static final int SIZE = 54;
    public static final int SLOT_PREV = 48;
    public static final int SLOT_BACK = 49;
    public static final int SLOT_NEXT = 50;
    private static final int SLOT_PAGE_INDICATOR = 51;

    private final Inventory inventory;
    private final int page;

    public BaltopHolder(EcoTpPlugin plugin, Player viewer, List<BalanceEntry> entries, int page) {
        this.inventory = Bukkit.createInventory(this, SIZE, plugin.getMessages().get("menu.baltop-title"));

        for (int slot = CONTENT_SIZE; slot < SIZE; slot++) {
            inventory.setItem(slot, MenuItems.borderPane(slot));
        }

        Paginator<BalanceEntry> paginator = new Paginator<>(entries, CONTENT_SIZE);
        this.page = Math.max(0, Math.min(page, paginator.pageCount() - 1));

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

        int rank = this.page * CONTENT_SIZE + 1;
        int slot = 0;
        for (BalanceEntry entry : paginator.page(this.page)) {
            inventory.setItem(slot, entryHead(plugin, rank, entry));
            rank++;
            slot++;
        }
        MenuItems.playOpenSound(viewer);
    }

    private static ItemStack entryHead(EcoTpPlugin plugin, int rank, BalanceEntry entry) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(entry.name());
            skullMeta.setOwningPlayer(owner);
            String rankColor = switch (rank) {
                case 1 -> "&6&l";
                case 2 -> "&7&l";
                case 3 -> "&c&l";
                default -> "&f";
            };
            skullMeta.setDisplayName(ChatUtil.color(rankColor + "#" + rank + " " + entry.name()));
            skullMeta.setLore(plugin.getMessages().getList("menu.lore.baltop-entry", "balance", ChatUtil.formatMoney(entry.balance())));
            stack.setItemMeta(skullMeta);
        }
        return stack;
    }

    public int getPage() {
        return page;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
