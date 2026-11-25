package com.yamakotaro.ecotp.gui;

import com.yamakotaro.ecotp.ChatUtil;
import com.yamakotaro.ecotp.EcoTpPlugin;
import com.yamakotaro.ecotp.Messages;
import com.yamakotaro.ecotp.SeasonalEventManager;
import com.yamakotaro.ecotp.SeasonalEventManager.SeasonalEvent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 季節イベントの景品交換所。上の段に景品 (アイテム・称号・お金)、下の段に手持ちのコイン・
 * 帽子の切り替え・称号メニュー・戻るを置く。景品はクリックでコインと交換する。
 */
public class EventShopHolder implements EcoTpMenu {

    private static final int SIZE = 54;
    private static final int SHOP_SLOTS = 36;
    public static final int SLOT_BACK = 45;
    private static final int SLOT_COINS = 49;
    public static final int SLOT_HAT = 47;
    public static final int SLOT_TITLES = 51;

    private final EcoTpPlugin plugin;
    private final Player viewer;
    private final SeasonalEvent event;
    private final Inventory inventory;

    public EventShopHolder(EcoTpPlugin plugin, Player viewer, SeasonalEvent event) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.event = event;
        this.inventory = Bukkit.createInventory(this, SIZE,
                plugin.getMessages().get("event.shop-title", "event", ChatUtil.color(event.name())));
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, slot < SHOP_SLOTS ? null : MenuItems.borderPane(slot));
        }
        Map<Integer, ItemStack> tiles = new HashMap<>(render());
        tiles.put(SLOT_BACK, MenuItems.item(Material.ARROW, plugin.getMessages().get("menu.back"), null));
        tiles.put(SLOT_TITLES, MenuItems.item(Material.NAME_TAG, plugin.getMessages().get("event.titles-button"),
                plugin.getMessages().getList("event.titles-button-lore")));
        MenuItems.playOpenSound(viewer);
        MenuFx.open(plugin, viewer, this, inventory, tiles, true);
    }

    private Map<Integer, ItemStack> render() {
        Messages messages = plugin.getMessages();
        SeasonalEventManager manager = plugin.getSeasonalEventManager();
        Map<Integer, ItemStack> items = new HashMap<>();
        int coins = manager.countCoins(viewer, event);
        List<Map<?, ?>> shop = event.shop();
        for (int i = 0; i < shop.size() && i < SHOP_SLOTS; i++) {
            items.put(i, shopItem(shop.get(i), coins));
        }
        ItemStack coinIcon = manager.createCoins(event, 1);
        var meta = coinIcon.getItemMeta();
        meta.setDisplayName(messages.get("event.your-coins", "coins", String.valueOf(coins)));
        meta.setLore(messages.getList("event.your-coins-lore"));
        coinIcon.setItemMeta(meta);
        items.put(SLOT_COINS, coinIcon);
        if (event.hat() != null) {
            boolean wearing = manager.isWearingHat(viewer.getUniqueId());
            ItemStack hat = event.hat().clone();
            var hatMeta = hat.getItemMeta();
            hatMeta.setDisplayName(messages.get("event.hat-button"));
            hatMeta.setLore(messages.getList("event.hat-button-lore",
                    "state", messages.get(wearing ? "menu.toggle-on" : "menu.toggle-off")));
            hatMeta.setEnchantmentGlintOverride(wearing);
            hat.setItemMeta(hatMeta);
            items.put(SLOT_HAT, hat);
        }
        return items;
    }

    private ItemStack shopItem(Map<?, ?> entry, int coins) {
        Messages messages = plugin.getMessages();
        int price = number(entry.get("price"), 1);
        List<String> lore = new ArrayList<>();
        ItemStack icon;
        if (entry.get("title") != null) {
            String title = entry.get("title").toString();
            boolean owned = plugin.getTitleManager().owns(viewer.getUniqueId(), title);
            icon = MenuItems.item(Material.NAME_TAG, messages.get("event.shop-title-item", "title", ChatUtil.color(title)), null, owned);
            lore.add(messages.get(owned ? "event.shop-owned" : "event.shop-price", "price", String.valueOf(price)));
        } else if (entry.get("money") != null) {
            double money = number(entry.get("money"), 0);
            icon = MenuItems.item(Material.GOLD_INGOT, messages.get("event.shop-money", "amount", ChatUtil.formatMoney(money)), null);
            lore.add(messages.get("event.shop-price", "price", String.valueOf(price)));
        } else {
            Material material = Material.matchMaterial(String.valueOf(entry.get("item")));
            icon = new ItemStack(material == null ? Material.BARRIER : material, Math.max(1, Math.min(64, number(entry.get("amount"), 1))));
            lore.add(messages.get("event.shop-price", "price", String.valueOf(price)));
        }
        lore.add(messages.get(coins >= price ? "event.shop-click" : "event.shop-not-enough"));
        var meta = icon.getItemMeta();
        if (entry.get("name") != null) {
            meta.setDisplayName(ChatUtil.color(entry.get("name").toString()));
        }
        meta.setLore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private static int number(Object value, int fallback) {
        return value instanceof Number n ? n.intValue() : fallback;
    }

    /** 景品のスロットがクリックされた: コインが足りれば交換する。 */
    public void buy(int slot) {
        if (slot < 0 || slot >= event.shop().size() || slot >= SHOP_SLOTS) {
            return;
        }
        Map<?, ?> entry = event.shop().get(slot);
        int price = number(entry.get("price"), 1);
        SeasonalEventManager manager = plugin.getSeasonalEventManager();
        String title = entry.get("title") == null ? null : entry.get("title").toString();
        if (title != null && plugin.getTitleManager().owns(viewer.getUniqueId(), title)) {
            return; // もう持っている称号
        }
        Economy economy = plugin.getEconomyHolder().get();
        if (entry.get("money") != null && economy == null) {
            viewer.sendMessage(plugin.msg("general.no-economy"));
            return;
        }
        if (!manager.takeCoins(viewer, event, price)) {
            viewer.sendMessage(plugin.msg("event.not-enough-coins", "price", String.valueOf(price)));
            viewer.playSound(viewer.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            return;
        }
        String got;
        if (title != null) {
            plugin.getTitleManager().give(viewer.getUniqueId(), title);
            got = ChatUtil.color(title);
        } else if (entry.get("money") != null) {
            double money = number(entry.get("money"), 0);
            economy.depositPlayer(viewer, money);
            got = ChatUtil.formatMoney(money);
        } else {
            ItemStack item = shopItem(entry, 0).clone();
            var meta = item.getItemMeta();
            if (entry.get("name") == null) {
                meta.setDisplayName(null);
            }
            meta.setLore(null);
            item.setItemMeta(meta);
            manager.giveItem(viewer, item);
            got = (entry.get("name") != null ? ChatUtil.color(entry.get("name").toString()) : item.getType().name())
                    + " x" + item.getAmount();
        }
        viewer.sendMessage(plugin.msg("event.bought", "item", got, "price", String.valueOf(price)));
        viewer.playSound(viewer.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
        render().forEach(inventory::setItem);
    }

    public void toggleHat() {
        boolean wearing = plugin.getSeasonalEventManager().toggleHat(viewer);
        viewer.sendMessage(plugin.msg(wearing ? "event.hat-on" : "event.hat-off"));
        render().forEach(inventory::setItem);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
