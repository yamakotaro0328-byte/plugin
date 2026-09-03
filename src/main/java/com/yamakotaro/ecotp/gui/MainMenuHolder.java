package com.yamakotaro.ecotp.gui;

import com.yamakotaro.ecotp.ChatUtil;
import com.yamakotaro.ecotp.EcoTpPlugin;
import com.yamakotaro.ecotp.Messages;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * /menu で開くメインメニュー。プレイヤーはコマンドを覚えなくても、
 * ここからホーム・スポーン・tpa・所持金確認/送金・ランキング・デイリー・寄付を
 * クリックだけで操作できる。config.yml の features.* で無効化されている項目は
 * 表示されず (枠のガラス板のまま)、料金や連続日数など現在の状況もアイテムの
 * 説明文に表示する。
 */
public class MainMenuHolder implements InventoryHolder {

    private static final int SIZE = 45;

    public static final int SLOT_HOME = 10;
    public static final int SLOT_SETHOME = 11;
    public static final int SLOT_SPAWN = 12;
    public static final int SLOT_TPA = 13;
    public static final int SLOT_TPHERE = 14;
    public static final int SLOT_BALANCE = 15;
    public static final int SLOT_PAY = 16;
    public static final int SLOT_BALTOP = 20;
    public static final int SLOT_DAILY = 22;
    public static final int SLOT_DONATE = 24;
    public static final int SLOT_CLOSE = 40;

    private final Inventory inventory;

    public MainMenuHolder(EcoTpPlugin plugin, Player viewer) {
        this.inventory = Bukkit.createInventory(this, SIZE, plugin.getMessages().get("menu.title"));
        ItemStack filler = MenuItems.filler();
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, filler);
        }

        Messages messages = plugin.getMessages();
        double minFee = plugin.getConfig().getDouble("costs.distance-min-fee", 100.0);
        String minFeeFormatted = ChatUtil.formatMoney(minFee);

        putIfEnabled(plugin, SLOT_HOME, "home", Material.RED_BED,
                messages.getList("menu.lore.home", "fee", minFeeFormatted));
        putIfEnabled(plugin, SLOT_SETHOME, "sethome", Material.COMPASS,
                sethomeLore(plugin, viewer));
        putIfEnabled(plugin, SLOT_SPAWN, "spawn", Material.GRASS_BLOCK,
                messages.getList("menu.lore.spawn", "fee", minFeeFormatted));
        putIfEnabled(plugin, SLOT_TPA, "tpa", Material.ENDER_PEARL,
                messages.getList("menu.lore.tpa", "fee", minFeeFormatted));
        putIfEnabled(plugin, SLOT_TPHERE, "tphere", Material.ENDER_EYE,
                messages.getList("menu.lore.tphere", "fee", minFeeFormatted));
        inventory.setItem(SLOT_BALANCE, MenuItems.item(Material.GOLD_INGOT, messages.get("menu.balance"),
                balanceLore(plugin, viewer)));
        putIfEnabled(plugin, SLOT_PAY, "pay", Material.EMERALD, messages.getList("menu.lore.pay"));
        putIfEnabled(plugin, SLOT_BALTOP, "baltop", Material.DIAMOND, messages.getList("menu.lore.baltop"));
        putIfEnabled(plugin, SLOT_DAILY, "daily", Material.CLOCK, dailyLore(plugin, viewer));
        putIfEnabled(plugin, SLOT_DONATE, "donate", Material.NETHER_STAR, messages.getList("menu.lore.donate"));

        inventory.setItem(SLOT_CLOSE, MenuItems.item(Material.BARRIER, messages.get("menu.close"), null));
    }

    private void putIfEnabled(EcoTpPlugin plugin, int slot, String featureKey, Material material, List<String> lore) {
        if (!plugin.isFeatureEnabled(featureKey)) {
            return; // 枠のガラス板のまま (無効な機能はメニューに出さない)
        }
        String displayName = plugin.getMessages().get("menu." + featureKey);
        inventory.setItem(slot, MenuItems.item(material, displayName, lore));
    }

    private static List<String> sethomeLore(EcoTpPlugin plugin, Player viewer) {
        UUID uuid = viewer.getUniqueId();
        double nextCost = plugin.getHomeManager().getNextSetHomeCost(uuid);
        int used = plugin.getHomeManager().getHomeNames(uuid).size();
        int max = plugin.getConfig().getInt("homes.max-per-player", 3);
        return plugin.getMessages().getList("menu.lore.sethome",
                "cost", ChatUtil.formatMoney(nextCost), "used", String.valueOf(used), "max", String.valueOf(max));
    }

    private static List<String> balanceLore(EcoTpPlugin plugin, Player viewer) {
        Economy economy = plugin.getEconomyHolder().get();
        if (economy == null) {
            return plugin.getMessages().getList("menu.lore.balance", "balance", "?");
        }
        return plugin.getMessages().getList("menu.lore.balance", "balance", ChatUtil.formatMoney(economy.getBalance(viewer)));
    }

    private static List<String> dailyLore(EcoTpPlugin plugin, Player viewer) {
        UUID uuid = viewer.getUniqueId();
        int streak = plugin.getDailyRewardManager().getStreak(uuid);
        if (plugin.getDailyRewardManager().isClaimable(uuid)) {
            String status = plugin.getMessages().get("menu.lore.daily-status-ready");
            return plugin.getMessages().getList("menu.lore.daily", "streak", String.valueOf(streak), "status", status);
        }
        String status = plugin.getMessages().get("menu.lore.daily-status-waiting",
                "time", plugin.getDailyRewardManager().formatRemainingCooldown(uuid));
        return plugin.getMessages().getList("menu.lore.daily", "streak", String.valueOf(streak), "status", status);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
