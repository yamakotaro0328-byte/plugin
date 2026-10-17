package com.yamakotaro.ecotp.gui;

import com.yamakotaro.ecotp.ChatUtil;
import com.yamakotaro.ecotp.EcoTpPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * GUI の演出: 開いた時に左から順にアイコンが現れるアニメーションと、枠の虹色が流れ続ける
 * アニメーション、進み具合のバー表示。config.yml の gui.animations: false で全部止まる
 * (その場合は普通に一度に表示される)。
 */
final class MenuFx {

    private static final Material[] RAINBOW = {
            Material.RED_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE,
            Material.YELLOW_STAINED_GLASS_PANE, Material.LIME_STAINED_GLASS_PANE,
            Material.LIGHT_BLUE_STAINED_GLASS_PANE, Material.BLUE_STAINED_GLASS_PANE,
            Material.PURPLE_STAINED_GLASS_PANE, Material.MAGENTA_STAINED_GLASS_PANE,
    };

    private MenuFx() {
    }

    static boolean animationsEnabled(EcoTpPlugin plugin) {
        return plugin.getConfig().getBoolean("gui.animations", true);
    }

    /** 外周のスロット (時計回り)。 */
    static List<Integer> frameSlots(int size) {
        int rows = size / 9;
        List<Integer> slots = new ArrayList<>();
        for (int c = 0; c < 9; c++) {
            slots.add(c);
        }
        for (int r = 1; r < rows - 1; r++) {
            slots.add(r * 9 + 8);
        }
        for (int c = 8; c >= 0; c--) {
            slots.add((rows - 1) * 9 + c);
        }
        for (int r = rows - 2; r >= 1; r--) {
            slots.add(r * 9);
        }
        return slots;
    }

    /** 外周を虹色で塗る (tiles に入っているスロットは飛ばす)。 */
    static void paintFrame(Inventory inventory, Map<Integer, ItemStack> tiles, int offset) {
        List<Integer> frame = frameSlots(inventory.getSize());
        for (int i = 0; i < frame.size(); i++) {
            int slot = frame.get(i);
            if (tiles.containsKey(slot)) {
                continue;
            }
            Material material = RAINBOW[Math.floorMod(i * RAINBOW.length / frame.size() + offset, RAINBOW.length)];
            inventory.setItem(slot, MenuItems.item(material, " ", null));
        }
    }

    /**
     * アイコンを列ごとに順番に表示し、その後は viewer がこの GUI を見ている間だけ枠の虹色を流す。
     * holder のコンストラクタの中から呼んでよい (実際の表示は次の tick から始まる)。
     */
    static void open(EcoTpPlugin plugin, Player viewer, InventoryHolder holder, Inventory inventory,
                     Map<Integer, ItemStack> tiles, boolean rainbowFrame) {
        if (rainbowFrame) {
            paintFrame(inventory, tiles, 0);
        }
        if (!animationsEnabled(plugin)) {
            tiles.forEach(inventory::setItem);
            return;
        }
        // 列ごとにまとめる (左から右へ波のように出す)。
        TreeMap<Integer, Map<Integer, ItemStack>> byColumn = new TreeMap<>();
        tiles.forEach((slot, item) -> byColumn.computeIfAbsent(slot % 9, k -> new TreeMap<>()).put(slot, item));
        List<Map<Integer, ItemStack>> waves = new ArrayList<>(byColumn.values());

        Bukkit.getScheduler().runTaskTimer(plugin, new Consumer<BukkitTask>() {
            int tick;

            @Override
            public void accept(BukkitTask task) {
                boolean viewing = viewer.isOnline()
                        && viewer.getOpenInventory().getTopInventory().getHolder() == holder;
                if (!viewing) {
                    // 閉じられた/別のGUIに移った: 残りは即座に置いて終わり。
                    tiles.forEach(inventory::setItem);
                    task.cancel();
                    return;
                }
                if (tick < waves.size()) {
                    waves.get(tick).forEach(inventory::setItem);
                    viewer.playSound(viewer.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, 1.0f + tick * 0.1f);
                } else if (!rainbowFrame) {
                    task.cancel();
                    return;
                } else if (tick % 4 == 0) {
                    paintFrame(inventory, tiles, tick / 4);
                }
                tick++;
            }
        }, 1L, 1L);
    }

    /** "■■■■■□□□□□ 50%" のような進み具合のバー。 */
    static String bar(double ratio, int length) {
        double clamped = Math.max(0.0, Math.min(1.0, ratio));
        int filled = (int) Math.round(clamped * length);
        return ChatUtil.color("&a" + "■".repeat(filled) + "&8" + "■".repeat(length - filled)
                + " &f" + Math.round(clamped * 100) + "%");
    }

    /** 押した時の効果音。 */
    static void click(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
    }
}
