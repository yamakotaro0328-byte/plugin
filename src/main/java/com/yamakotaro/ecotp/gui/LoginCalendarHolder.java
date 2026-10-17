package com.yamakotaro.ecotp.gui;

import com.yamakotaro.ecotp.ChatUtil;
import com.yamakotaro.ecotp.DailyRewardManager;
import com.yamakotaro.ecotp.EcoTpPlugin;
import com.yamakotaro.ecotp.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ログインカレンダー: 今の周期の1日目〜最終日を並べ、受け取り済みの日・今日・特別ボーナスの
 * 日がひと目で分かるようにする。今日の分は日付アイコンか下段のボタンで受け取れる。
 */
public class LoginCalendarHolder implements EcoTpMenu {

    private static final int SIZE = 54;
    private static final int MAX_DAYS = 45;
    public static final int SLOT_BACK = 45;
    public static final int SLOT_CLAIM = 49;
    private static final int SLOT_INFO = 53;

    private final Inventory inventory;
    private final int claimDaySlot;

    public LoginCalendarHolder(EcoTpPlugin plugin, Player viewer) {
        DailyRewardManager daily = plugin.getDailyRewardManager();
        Messages messages = plugin.getMessages();
        UUID uuid = viewer.getUniqueId();
        int streak = daily.getEffectiveStreak(uuid);
        boolean claimable = daily.isClaimable(uuid);

        int cycle = daily.calendarCycle();
        int display = Math.min(MAX_DAYS, cycle > 0 ? cycle : 28);
        int doneInCycle = streak == 0 ? 0 : Math.min(daily.calendarDay(streak), display);
        if (claimable && doneInCycle >= display) {
            doneInCycle = 0; // 1周終わった: 次の受け取りから新しい周期
        }
        int cycleStart = streak - doneInCycle; // この画面の「1日目」の直前までの連続日数
        int nextDay = doneInCycle + 1;

        this.inventory = Bukkit.createInventory(this, SIZE,
                messages.get("menu.calendar-title", "streak", String.valueOf(streak)));
        for (int slot = MAX_DAYS; slot < SIZE; slot++) {
            inventory.setItem(slot, MenuItems.borderPane(slot));
        }

        Map<Integer, ItemStack> tiles = new HashMap<>();
        for (int day = 1; day <= display; day++) {
            int actualStreak = cycleStart + day;
            double amount = daily.amountForStreak(actualStreak);
            double bonus = daily.calendarBonus(actualStreak);
            tiles.put(day - 1, dayItem(messages, day, amount, bonus, day <= doneInCycle, day == nextDay, claimable,
                    daily.formatRemainingCooldown(uuid)));
        }
        this.claimDaySlot = claimable && nextDay <= display ? nextDay - 1 : -1;

        tiles.put(SLOT_BACK, MenuItems.item(Material.ARROW, messages.get("menu.back"), null));
        tiles.put(SLOT_CLAIM, claimable
                ? MenuItems.item(Material.GOLD_BLOCK, messages.get("menu.calendar-claim"),
                        messages.getList("menu.lore.calendar-claim"), true)
                : MenuItems.item(Material.CLOCK, messages.get("menu.calendar-wait"),
                        messages.getList("menu.lore.calendar-wait", "time", daily.formatRemainingCooldown(uuid))));
        int next = daily.nextCalendarDay(streak);
        List<String> info = new ArrayList<>(messages.getList("menu.lore.calendar-info", "streak", String.valueOf(streak)));
        if (next > 0) {
            info.add(messages.get("menu.lore.calendar-next", "day", String.valueOf(next),
                    "days", String.valueOf(next - streak), "bonus", ChatUtil.formatMoney(daily.calendarBonus(next))));
        }
        tiles.put(SLOT_INFO, MenuItems.item(Material.BOOK, messages.get("menu.calendar-info"), info));

        MenuItems.playOpenSound(viewer);
        MenuFx.open(plugin, viewer, this, inventory, tiles, false);
    }

    private static ItemStack dayItem(Messages messages, int day, double amount, double bonus,
                                     boolean done, boolean next, boolean claimable, String remaining) {
        boolean special = bonus > 0;
        String amountText = ChatUtil.formatMoney(amount);
        List<String> lore = new ArrayList<>(messages.getList("menu.lore.calendar-day", "amount", amountText));
        if (special) {
            lore.add(messages.get("menu.lore.calendar-special", "bonus", ChatUtil.formatMoney(bonus)));
        }
        ItemStack item;
        if (done) {
            lore.add(messages.get("menu.lore.calendar-done"));
            item = MenuItems.item(special ? Material.LIME_SHULKER_BOX : Material.LIME_STAINED_GLASS,
                    messages.get("menu.calendar-day-done", "day", String.valueOf(day)), lore);
        } else if (next) {
            lore.add(claimable ? messages.get("menu.lore.calendar-today")
                    : messages.get("menu.lore.calendar-tomorrow", "time", remaining));
            item = MenuItems.item(special ? Material.ENDER_CHEST : Material.CHEST,
                    messages.get(claimable ? "menu.calendar-day-today" : "menu.calendar-day-next", "day", String.valueOf(day)),
                    lore, true);
        } else {
            item = MenuItems.item(special ? Material.YELLOW_SHULKER_BOX : Material.WHITE_STAINED_GLASS,
                    messages.get(special ? "menu.calendar-day-special" : "menu.calendar-day", "day", String.valueOf(day)), lore);
        }
        item.setAmount(Math.max(1, Math.min(64, day)));
        return item;
    }

    /** @return 今日の分を受け取れる日付アイコンのスロット。受け取れなければ -1。 */
    public int getClaimDaySlot() {
        return claimDaySlot;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
