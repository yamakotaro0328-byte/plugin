package com.yamakotaro.ecojobs.menu;

import com.yamakotaro.ecojobs.JobManager;
import com.yamakotaro.ecojobs.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;

/**
 * Job picker shown before a leaderboard (see LeaderboardMenuHolder) - reached from the hub's
 * Leaderboards button, so players don't need to dig through their own job list just to check
 * someone else's job's rankings.
 */
public class LeaderboardPickerMenuHolder implements InventoryHolder {

    public static final int BACK_SLOT = 45;
    public static final int CLOSE_SLOT = 49;

    private final Messages messages;
    private final Inventory inventory;
    private final Map<Integer, String> slotToJobId = new HashMap<>();

    public LeaderboardPickerMenuHolder(Messages messages) {
        this.messages = messages;
        this.inventory = Bukkit.createInventory(this, 54, messages.get("menu.leaderboard-picker-title", Map.of()));
    }

    public String jobIdAt(int slot) {
        return slotToJobId.get(slot);
    }

    public void render(JobManager jobManager) {
        inventory.clear();
        slotToJobId.clear();
        int slot = 0;
        for (String jobId : jobManager.all().keySet()) {
            if (slot >= BACK_SLOT) {
                break;
            }
            Material material = JobsMenuHolder.ICONS.getOrDefault(jobId, Material.PAPER);
            ItemStack stack = new ItemStack(material);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.displayName(messages.get("menu.job-title", Map.of("job", messages.jobName(jobId))));
                stack.setItemMeta(meta);
            }
            inventory.setItem(slot, stack);
            slotToJobId.put(slot, jobId);
            slot++;
        }
        inventory.setItem(BACK_SLOT, MenuUtil.backItem(messages));
        inventory.setItem(CLOSE_SLOT, MenuUtil.closeItem(messages));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
