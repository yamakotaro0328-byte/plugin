package com.yamakotaro.ecotp.gui;

import com.yamakotaro.ecotp.ChatUtil;
import com.yamakotaro.ecotp.DailyQuestManager;
import com.yamakotaro.ecotp.EcoTpPlugin;
import com.yamakotaro.ecotp.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** /quest : 今日のデイリークエストと進み具合、全部達成ボーナスを表示する。 */
public class QuestHolder implements EcoTpMenu {

    private static final int SIZE = 27;
    private static final int[] QUEST_SLOTS = {11, 13, 15, 10, 12, 14, 16};
    private static final int SLOT_BONUS = 4;
    public static final int SLOT_BACK = 22;

    private final Inventory inventory;

    public QuestHolder(EcoTpPlugin plugin, Player viewer) {
        Messages messages = plugin.getMessages();
        DailyQuestManager quests = plugin.getDailyQuestManager();
        this.inventory = Bukkit.createInventory(this, SIZE, messages.get("quest.title"));
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, MenuItems.borderPane(slot));
        }
        Map<Integer, ItemStack> tiles = new HashMap<>();
        List<DailyQuestManager.QuestProgress> list = quests.getQuests(viewer.getUniqueId());
        int done = 0;
        for (int i = 0; i < list.size() && i < QUEST_SLOTS.length; i++) {
            DailyQuestManager.QuestProgress quest = list.get(i);
            if (quest.done()) {
                done++;
            }
            List<String> lore = messages.getList(quest.done() ? "quest.lore-done" : "quest.lore",
                    "bar", MenuFx.bar((double) quest.progress() / quest.def().amount(), 10),
                    "progress", String.valueOf(quest.progress()), "amount", String.valueOf(quest.def().amount()),
                    "reward", ChatUtil.formatMoney(plugin.getFeverManager().apply(quest.def().reward())));
            tiles.put(QUEST_SLOTS[i], MenuItems.item(quest.done() ? Material.LIME_DYE : quest.def().icon(),
                    (quest.done() ? ChatUtil.color("&a✔ ") : "") + quest.def().name(), lore, quest.done()));
        }
        boolean bonus = quests.isBonusClaimed(viewer.getUniqueId());
        double bonusAmount = plugin.getFeverManager().apply(plugin.getConfig().getDouble("daily-quests.all-complete-bonus", 1000.0));
        tiles.put(SLOT_BONUS, MenuItems.item(bonus ? Material.CHEST_MINECART : Material.CHEST,
                messages.get("quest.bonus"), messages.getList(bonus ? "quest.bonus-lore-done" : "quest.bonus-lore",
                        "done", String.valueOf(done), "total", String.valueOf(list.size()),
                        "bonus", ChatUtil.formatMoney(bonusAmount)), bonus));
        tiles.put(SLOT_BACK, MenuItems.item(Material.ARROW, messages.get("menu.back"), null));
        MenuItems.playOpenSound(viewer);
        MenuFx.open(plugin, viewer, this, inventory, tiles, false);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
