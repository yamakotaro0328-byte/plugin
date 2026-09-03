package com.yamakotaro.ecotp.gui;

import com.yamakotaro.ecotp.ChatUtil;
import com.yamakotaro.ecotp.EcoTpPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * /pay, /donate で相手を選んだ後に開く、金額のクイック選択GUI。
 * よくある金額はここでワンクリックで送れる。それ以外の金額は「カスタム金額」から
 * 従来通りチャットに入力する。
 */
public class AmountSelectHolder implements InventoryHolder {

    private static final int SIZE = 27;
    public static final int SLOT_CUSTOM = 22;
    public static final int SLOT_BACK = 18;
    private static final double[] PRESET_AMOUNTS = {100, 500, 1000, 5000, 10000, 50000};
    private static final int[] PRESET_SLOTS = {10, 11, 12, 14, 15, 16};

    private final Inventory inventory;
    private final String targetName;
    private final boolean donate;

    public AmountSelectHolder(EcoTpPlugin plugin, Player viewer, String targetName, boolean donate) {
        this.targetName = targetName;
        this.donate = donate;
        String titleKey = donate ? "menu.select-donate-amount-title" : "menu.select-pay-amount-title";
        this.inventory = Bukkit.createInventory(this, SIZE, plugin.getMessages().get(titleKey, "player", targetName));

        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, MenuItems.borderPane(slot));
        }
        inventory.setItem(SLOT_BACK, MenuItems.item(Material.ARROW, plugin.getMessages().get("menu.back"), null));

        for (int i = 0; i < PRESET_AMOUNTS.length; i++) {
            String name = ChatUtil.color("&e" + ChatUtil.formatMoney(PRESET_AMOUNTS[i]));
            inventory.setItem(PRESET_SLOTS[i], MenuItems.item(Material.GOLD_NUGGET, name, plugin.getMessages().getList("menu.lore.amount-preset")));
        }
        inventory.setItem(SLOT_CUSTOM, MenuItems.item(Material.WRITABLE_BOOK,
                plugin.getMessages().get("menu.amount-custom"), plugin.getMessages().getList("menu.lore.amount-custom")));

        MenuItems.playOpenSound(viewer);
    }

    /** @return クリックされたスロットに対応するプリセット金額。プリセットのスロットでなければ -1。 */
    public static double presetAmountForSlot(int slot) {
        for (int i = 0; i < PRESET_SLOTS.length; i++) {
            if (PRESET_SLOTS[i] == slot) {
                return PRESET_AMOUNTS[i];
            }
        }
        return -1;
    }

    public String getTargetName() {
        return targetName;
    }

    public boolean isDonate() {
        return donate;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
