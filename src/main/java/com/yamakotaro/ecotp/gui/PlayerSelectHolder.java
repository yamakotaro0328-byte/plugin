package com.yamakotaro.ecotp.gui;

import com.yamakotaro.ecotp.EcoTpPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * /tpa や /pay, /donate の相手を選ぶための、オンラインプレイヤーの頭アイテム一覧GUI。
 * アイテムの表示名 = プレイヤー名 (色無し) にしておき、クリック時にそのまま
 * Bukkit.getPlayerExact に渡せるようにしている。
 * 45人を超えてオンラインの場合はページ送りで全員に到達できる (Paginator 参照)。
 */
public class PlayerSelectHolder implements InventoryHolder {

    public enum Purpose {
        TPA, TPHERE, PAY, DONATE
    }

    /** 頭アイテムを並べられる枠 (最下段は戻る/ページ送り専用)。 */
    public static final int CONTENT_SIZE = 45;
    private static final int SIZE = 54;
    public static final int SLOT_PREV = 48;
    public static final int SLOT_BACK = 49;
    public static final int SLOT_NEXT = 50;
    private static final int SLOT_PAGE_INDICATOR = 51;

    private final Inventory inventory;
    private final Purpose purpose;
    private final int page;

    public PlayerSelectHolder(EcoTpPlugin plugin, Player viewer, Purpose purpose, int page) {
        this.purpose = purpose;
        this.inventory = Bukkit.createInventory(this, SIZE, plugin.getMessages().get("menu.select-player-title"));

        for (int slot = CONTENT_SIZE; slot < SIZE; slot++) {
            inventory.setItem(slot, MenuItems.borderPane(slot));
        }

        List<Player> candidates = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(viewer.getUniqueId())) {
                candidates.add(online);
            }
        }
        Paginator<Player> paginator = new Paginator<>(candidates, CONTENT_SIZE);
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

        List<String> headLore = plugin.getMessages().getList("menu.lore.select-player-head");
        int slot = 0;
        for (Player online : paginator.page(this.page)) {
            inventory.setItem(slot, headOf(online, headLore));
            slot++;
        }
        MenuItems.playOpenSound(viewer);
    }

    private static ItemStack headOf(Player player, List<String> lore) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(player);
            skullMeta.setDisplayName(player.getName());
            if (!lore.isEmpty()) {
                skullMeta.setLore(lore);
            }
            stack.setItemMeta(skullMeta);
        }
        return stack;
    }

    public Purpose getPurpose() {
        return purpose;
    }

    public int getPage() {
        return page;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
