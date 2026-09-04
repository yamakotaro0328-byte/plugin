package com.yamakotaro.ecojobs.menu;

import com.yamakotaro.ecojobs.BoosterManager;
import com.yamakotaro.ecojobs.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The landing screen /jobs menu now opens: a small hub pointing to the player's job list,
 * leaderboards, and personal settings - plus a shortcut into /jobs admin for anyone who can use
 * it, so ops don't need to remember a separate command. Also surfaces any currently-active booster
 * (previously visible only in the admin menu and the admin-only /jobs booster list, so a player
 * earning double money/xp from one had no way to actually see it was happening).
 */
public class HubMenuHolder implements InventoryHolder {

    public static final int MY_JOBS_SLOT = 11;
    public static final int LEADERBOARDS_SLOT = 13;
    public static final int SETTINGS_SLOT = 15;
    public static final int BOOSTER_SLOT = 17;
    public static final int ADMIN_SLOT = 22;
    public static final int CLOSE_SLOT = 26;

    private final Messages messages;
    private final Inventory inventory;

    public HubMenuHolder(Messages messages) {
        this.messages = messages;
        this.inventory = Bukkit.createInventory(this, 27, messages.get("menu.hub-title", Map.of()));
    }

    public void render(boolean showAdmin, BoosterManager boosterManager) {
        inventory.clear();
        inventory.setItem(MY_JOBS_SLOT, item(Material.WRITABLE_BOOK, "menu.hub-my-jobs", "menu.hub-my-jobs-lore"));
        inventory.setItem(LEADERBOARDS_SLOT, item(Material.GOLD_INGOT, "menu.hub-leaderboards", "menu.hub-leaderboards-lore"));
        inventory.setItem(SETTINGS_SLOT, item(Material.COMPARATOR, "menu.hub-settings", "menu.hub-settings-lore"));
        inventory.setItem(BOOSTER_SLOT, boosterItem(boosterManager));
        if (showAdmin) {
            inventory.setItem(ADMIN_SLOT, item(Material.COMMAND_BLOCK, "menu.hub-admin", "menu.hub-admin-lore"));
        }
        inventory.setItem(CLOSE_SLOT, MenuUtil.closeItem(messages));
    }

    private ItemStack item(Material material, String titleKey, String loreKey) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(messages.get(titleKey, Map.of()));
            meta.lore(List.of(messages.get(loreKey, Map.of())));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack boosterItem(BoosterManager boosterManager) {
        ItemStack stack = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(messages.get("menu.hub-boosters", Map.of()));
            List<Component> lore = new ArrayList<>();
            var active = boosterManager.active();
            if (active.isEmpty()) {
                lore.add(messages.get("jobs.booster-list-empty", Map.of()));
            } else {
                for (BoosterManager.ActiveBooster booster : active) {
                    long minutesLeft = Math.max(0, (booster.expiresAtMillis() - System.currentTimeMillis()) / 60_000);
                    String scopeLabel = BoosterManager.GLOBAL_SCOPE.equals(booster.scope())
                            ? messages.raw("jobs.booster-scope-all", Map.of())
                            : messages.jobName(booster.scope());
                    lore.add(messages.get("jobs.booster-list-entry", Map.of(
                            "scope", scopeLabel,
                            "money", String.format("%.2f", booster.moneyMultiplier()),
                            "xp", String.format("%.2f", booster.xpMultiplier()),
                            "minutes", String.valueOf(minutesLeft))));
                }
            }
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
