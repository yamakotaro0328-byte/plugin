package com.yamakotaro.ecojobs.menu;

import com.yamakotaro.ecojobs.BoosterManager;
import com.yamakotaro.ecojobs.JobManager;
import com.yamakotaro.ecojobs.JobOverrides;
import com.yamakotaro.ecojobs.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * /jobs admin: per-job live tuning without touching config.yml. Left-click toggles a job
 * enabled/disabled, right-click/shift-right-click bumps its pay multiplier up/down, and
 * shift-left-click opens its leaderboard (see LeaderboardMenuHolder). The bottom row also has
 * one-click quick actions for a global 2x booster (see BoosterManager) - use /jobs booster for
 * anything more specific than that.
 */
public class AdminMenuHolder implements InventoryHolder {

    public static final int BOOSTER_START_SLOT = 48;
    public static final int BOOSTER_STOP_SLOT = 50;
    public static final int CLOSE_SLOT = 53;
    public static final double MULTIPLIER_STEP = 0.1;
    public static final double QUICK_BOOSTER_MULTIPLIER = 2.0;
    public static final long QUICK_BOOSTER_MINUTES = 30;

    private final Messages messages;
    private final Inventory inventory;
    private final Map<Integer, String> slotToJobId = new HashMap<>();

    public AdminMenuHolder(Messages messages) {
        this.messages = messages;
        this.inventory = Bukkit.createInventory(this, 54, messages.get("admin.title", Map.of()));
    }

    public String jobIdAt(int slot) {
        return slotToJobId.get(slot);
    }

    public void render(JobManager jobManager, JobOverrides jobOverrides, BoosterManager boosterManager) {
        inventory.clear();
        slotToJobId.clear();
        int slot = 0;
        for (String jobId : jobManager.all().keySet()) {
            if (slot >= BOOSTER_START_SLOT) {
                break;
            }
            inventory.setItem(slot, buildJobItem(jobId, jobOverrides, boosterManager));
            slotToJobId.put(slot, jobId);
            slot++;
        }
        inventory.setItem(BOOSTER_START_SLOT, boosterStartItem());
        inventory.setItem(BOOSTER_STOP_SLOT, boosterStopItem(boosterManager));
        inventory.setItem(CLOSE_SLOT, MenuUtil.closeItem(messages));
    }

    private ItemStack buildJobItem(String jobId, JobOverrides jobOverrides, BoosterManager boosterManager) {
        Material material = JobsMenuHolder.ICONS.getOrDefault(jobId, Material.PAPER);
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            boolean enabled = jobOverrides.isEnabled(jobId);
            double multiplier = jobOverrides.payMultiplier(jobId);
            meta.displayName(messages.get("menu.job-title", Map.of("job", messages.jobName(jobId))));
            List<Component> lore = new ArrayList<>();
            lore.add(messages.get(enabled ? "admin.lore-enabled-yes" : "admin.lore-enabled-no", Map.of()));
            lore.add(messages.get("admin.lore-multiplier", Map.of("multiplier", String.format("%.2f", multiplier))));
            BoosterManager.ActiveBooster booster = boosterManager.getActiveBooster(jobId);
            if (booster != null) {
                lore.add(messages.get("admin.lore-job-booster", Map.of(
                        "money", String.format("%.2f", booster.moneyMultiplier()),
                        "xp", String.format("%.2f", booster.xpMultiplier()),
                        "minutes", String.valueOf(remainingMinutes(booster)))));
            }
            lore.add(messages.get("admin.lore-controls-toggle", Map.of()));
            lore.add(messages.get("admin.lore-controls-multiplier-up", Map.of()));
            lore.add(messages.get("admin.lore-controls-multiplier-down", Map.of()));
            lore.add(messages.get("admin.lore-controls-leaderboard", Map.of()));
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static long remainingMinutes(BoosterManager.ActiveBooster booster) {
        return Math.max(0, (booster.expiresAtMillis() - System.currentTimeMillis()) / 60_000);
    }

    private ItemStack boosterStartItem() {
        ItemStack stack = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(messages.get("admin.booster-start-title", Map.of()));
            meta.lore(List.of(messages.get("admin.booster-start-lore", Map.of(
                    "multiplier", String.format("%.0f", QUICK_BOOSTER_MULTIPLIER),
                    "minutes", String.valueOf(QUICK_BOOSTER_MINUTES)))));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack boosterStopItem(BoosterManager boosterManager) {
        ItemStack stack = new ItemStack(Material.BARRIER);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(messages.get("admin.booster-stop-title", Map.of()));
            meta.lore(List.of(messages.get("admin.booster-stop-lore",
                    Map.of("count", String.valueOf(boosterManager.active().size())))));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
