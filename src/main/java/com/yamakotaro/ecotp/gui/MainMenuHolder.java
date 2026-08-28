package com.yamakotaro.ecotp.gui;

import com.yamakotaro.ecotp.EcoTpPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * /menu で開くメインメニュー。プレイヤーはコマンドを覚えなくても、
 * ここからホーム・スポーン・tpa・所持金確認/送金・ランキングをクリックだけで操作できる。
 */
public class MainMenuHolder implements InventoryHolder {

    public static final int SLOT_HOME = 10;
    public static final int SLOT_SETHOME = 11;
    public static final int SLOT_SPAWN = 12;
    public static final int SLOT_TPA = 13;
    public static final int SLOT_TPHERE = 14;
    public static final int SLOT_BALANCE = 15;
    public static final int SLOT_PAY = 16;
    public static final int SLOT_BALTOP = 20;
    public static final int SLOT_CLOSE = 22;

    private final Inventory inventory;

    public MainMenuHolder(EcoTpPlugin plugin) {
        this.inventory = Bukkit.createInventory(this, 27, plugin.getMessages().get("menu.title"));
        inventory.setItem(SLOT_HOME, item(Material.RED_BED, plugin.getMessages().get("menu.home")));
        inventory.setItem(SLOT_SETHOME, item(Material.COMPASS, plugin.getMessages().get("menu.sethome")));
        inventory.setItem(SLOT_SPAWN, item(Material.GRASS_BLOCK, plugin.getMessages().get("menu.spawn")));
        inventory.setItem(SLOT_TPA, item(Material.ENDER_PEARL, plugin.getMessages().get("menu.tpa")));
        inventory.setItem(SLOT_TPHERE, item(Material.ENDER_EYE, plugin.getMessages().get("menu.tphere")));
        inventory.setItem(SLOT_BALANCE, item(Material.GOLD_INGOT, plugin.getMessages().get("menu.balance")));
        inventory.setItem(SLOT_PAY, item(Material.EMERALD, plugin.getMessages().get("menu.pay")));
        inventory.setItem(SLOT_BALTOP, item(Material.DIAMOND, plugin.getMessages().get("menu.baltop")));
        inventory.setItem(SLOT_CLOSE, item(Material.BARRIER, plugin.getMessages().get("menu.close")));
    }

    private static ItemStack item(Material material, String displayName) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
