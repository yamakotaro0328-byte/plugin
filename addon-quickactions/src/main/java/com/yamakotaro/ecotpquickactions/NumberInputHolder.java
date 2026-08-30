package com.yamakotaro.ecotpquickactions;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * チャット入力の代わりに+/-ボタンだけで数値(価格・数量)を決めるGUI。他のチャット系
 * プラグイン(LunaChat等)の影響を受けて誤動作していたチャット入力方式をやめ、
 * クリックだけで完結させることで根本的に解決する。
 */
public class NumberInputHolder implements InventoryHolder {

    public static final int CANCEL_SLOT = 20;
    public static final int CONFIRM_SLOT = 22;
    public static final int DISABLE_SLOT = 24;

    private final Messages messages;
    private final Inventory inventory;
    private final double min;
    private final Double max;
    private final boolean allowDisable;
    private final Consumer<Double> onConfirm;
    private final Runnable onDisable;
    private final Runnable onCancel;
    private final Map<Integer, Double> deltaBySlot = new HashMap<>();
    private double value;

    public NumberInputHolder(Messages messages, Component title, double initialValue, double min, Double max,
                              boolean allowDisable, Consumer<Double> onConfirm, Runnable onDisable, Runnable onCancel) {
        this.messages = messages;
        this.min = min;
        this.max = max;
        this.allowDisable = allowDisable;
        this.onConfirm = onConfirm;
        this.onDisable = onDisable;
        this.onCancel = onCancel;
        this.inventory = Bukkit.createInventory(this, 27, title);
        this.value = clamp(initialValue);

        deltaBySlot.put(0, -10000.0);
        deltaBySlot.put(1, -1000.0);
        deltaBySlot.put(2, -100.0);
        deltaBySlot.put(3, -10.0);
        deltaBySlot.put(4, -1.0);
        deltaBySlot.put(6, 1.0);
        deltaBySlot.put(7, 10.0);
        deltaBySlot.put(8, 100.0);
        deltaBySlot.put(9, 1000.0);
        deltaBySlot.put(10, 10000.0);

        render();
    }

    public double getValue() {
        return value;
    }

    public boolean canConfirm() {
        return value > 0;
    }

    public boolean isAllowDisable() {
        return allowDisable;
    }

    public Double getDelta(int slot) {
        return deltaBySlot.get(slot);
    }

    public Consumer<Double> getOnConfirm() {
        return onConfirm;
    }

    public Runnable getOnDisable() {
        return onDisable;
    }

    public Runnable getOnCancel() {
        return onCancel;
    }

    public void adjust(double delta) {
        value = clamp(value + delta);
        render();
    }

    private double clamp(double v) {
        double result = Math.max(min, v);
        if (max != null) {
            result = Math.min(max, result);
        }
        return result;
    }

    private void render() {
        for (Map.Entry<Integer, Double> entry : deltaBySlot.entrySet()) {
            double delta = entry.getValue();
            Material material = delta < 0 ? Material.RED_CONCRETE : Material.LIME_CONCRETE;
            String label = (delta > 0 ? "+" : "") + (long) delta;
            inventory.setItem(entry.getKey(), item(material, Component.text(label)));
        }
        inventory.setItem(13, item(Material.PAPER, messages.get("numberinput.current-value",
                Map.of("value", formatValue()))));
        inventory.setItem(CANCEL_SLOT, item(Material.BARRIER, messages.get("numberinput.cancel", Map.of())));
        inventory.setItem(CONFIRM_SLOT, item(Material.EMERALD_BLOCK, messages.get("numberinput.confirm", Map.of())));
        if (allowDisable) {
            inventory.setItem(DISABLE_SLOT, item(Material.GRAY_CONCRETE, messages.get("numberinput.disable", Map.of())));
        }
    }

    private String formatValue() {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    private static ItemStack item(Material material, Component name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            meta.lore(List.of());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
