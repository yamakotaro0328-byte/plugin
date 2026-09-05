package com.yamakotaro.ecojobs.menu;

import com.yamakotaro.ecojobs.JobManager;
import com.yamakotaro.ecojobs.JobOverrides;
import com.yamakotaro.ecojobs.Messages;
import com.yamakotaro.ecojobs.PlayerJobManager;
import com.yamakotaro.ecojobs.PlayerJobProgress;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The player-facing /jobs menu: a bordered 54-slot GUI (not a bare item list) with a player
 * summary head, a visual XP progress bar per job, and shift-click-to-view-leaderboard - the same
 * visual language as the admin GUI (see AdminMenuHolder), just aimed at players instead of ops.
 */
public class JobsMenuHolder implements InventoryHolder {

    public static final int SUMMARY_SLOT = 4;
    public static final int BACK_SLOT = 45;
    public static final int CLOSE_SLOT = 49;
    private static final int PROGRESS_BAR_LENGTH = 10;

    // Package-private so AdminMenuHolder can reuse the same icons for its job list.
    static final Map<String, Material> ICONS = Map.ofEntries(
            Map.entry("miner", Material.IRON_PICKAXE),
            Map.entry("digger", Material.IRON_SHOVEL),
            Map.entry("woodcutter", Material.DIAMOND_AXE),
            Map.entry("farmer", Material.WHEAT),
            Map.entry("builder", Material.BRICKS),
            Map.entry("fisherman", Material.FISHING_ROD),
            Map.entry("treasurehunter", Material.CHEST),
            Map.entry("hunter", Material.IRON_SWORD),
            Map.entry("archer", Material.BOW),
            Map.entry("slayer", Material.NETHERITE_SWORD),
            Map.entry("warrior", Material.DIAMOND_SWORD),
            Map.entry("breeder", Material.WHEAT_SEEDS),
            Map.entry("tamer", Material.BONE),
            Map.entry("shearer", Material.SHEARS),
            Map.entry("beekeeper", Material.HONEYCOMB),
            Map.entry("enchanter", Material.ENCHANTING_TABLE),
            Map.entry("smelter", Material.FURNACE),
            Map.entry("crafter", Material.CRAFTING_TABLE),
            Map.entry("merchant", Material.EMERALD),
            Map.entry("explorer", Material.COMPASS));

    // 28 slots (see MenuUtil#interiorSlots), comfortably more than the 20 jobs that exist today,
    // with room to add more later.
    private static final List<Integer> JOB_SLOTS = MenuUtil.interiorSlots();

    private final Messages messages;
    private final Inventory inventory;
    private final Map<Integer, String> slotToJobId = new HashMap<>();

    public JobsMenuHolder(Messages messages) {
        this.messages = messages;
        this.inventory = Bukkit.createInventory(this, 54, messages.get("menu.title", Map.of()));
    }

    public String jobIdAt(int slot) {
        return slotToJobId.get(slot);
    }

    public void render(JobManager jobManager, PlayerJobManager playerJobManager, JobOverrides jobOverrides, Player viewer) {
        inventory.clear();
        slotToJobId.clear();
        MenuUtil.fillBorder(inventory);

        Map<String, PlayerJobProgress> allProgress = playerJobManager.allProgress(viewer.getUniqueId());
        boolean canViewLeaderboard = viewer.hasPermission("ecojobs.top");
        inventory.setItem(SUMMARY_SLOT, summaryItem(viewer, jobManager, playerJobManager, allProgress));

        int index = 0;
        for (String jobId : jobManager.all().keySet()) {
            if (index >= JOB_SLOTS.size()) {
                break;
            }
            boolean active = playerJobManager.isJoined(viewer.getUniqueId(), jobId);
            boolean enabled = jobOverrides.isEnabled(jobId);
            int slot = JOB_SLOTS.get(index++);
            inventory.setItem(slot, buildJobItem(jobId, allProgress.get(jobId), active, enabled, canViewLeaderboard, jobManager, playerJobManager));
            slotToJobId.put(slot, jobId);
        }
        inventory.setItem(BACK_SLOT, MenuUtil.backItem(messages));
        inventory.setItem(CLOSE_SLOT, MenuUtil.closeItem(messages));
    }

    private ItemStack summaryItem(Player viewer, JobManager jobManager, PlayerJobManager playerJobManager, Map<String, PlayerJobProgress> allProgress) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(viewer);
            skullMeta.displayName(messages.get("menu.summary-title", Map.of("player", viewer.getName())));
            int totalLevel = 0;
            int joinedCount = 0;
            for (String jobId : jobManager.all().keySet()) {
                PlayerJobProgress progress = allProgress.get(jobId);
                if (progress != null) {
                    totalLevel += progress.getLevel();
                }
                if (playerJobManager.isJoined(viewer.getUniqueId(), jobId)) {
                    joinedCount++;
                }
            }
            skullMeta.lore(List.of(messages.get("menu.summary-lore", Map.of(
                    "total_level", String.valueOf(totalLevel),
                    "joined", String.valueOf(joinedCount),
                    "max", String.valueOf(jobManager.maxConcurrentJobs())))));
            stack.setItemMeta(skullMeta);
        }
        return stack;
    }

    private ItemStack buildJobItem(String jobId, PlayerJobProgress progress, boolean active, boolean enabled, boolean canViewLeaderboard,
                                    JobManager jobManager, PlayerJobManager playerJobManager) {
        Material material = ICONS.getOrDefault(jobId, Material.PAPER);
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(messages.get("menu.job-title", Map.of("job", messages.jobName(jobId))));
            List<Component> lore = new ArrayList<>();
            boolean maxed = false;
            if (progress != null) {
                lore.add(messages.get("menu.lore-level", Map.of(
                        "level", String.valueOf(progress.getLevel()),
                        "prestige", String.valueOf(progress.getPrestige()),
                        "xp", String.format("%.0f", progress.getXp()),
                        "next_xp", String.format("%.0f", playerJobManager.xpToNextLevel(progress.getLevel())))));
                maxed = progress.getLevel() >= jobManager.maxLevel();
                if (!maxed) {
                    double nextXp = playerJobManager.xpToNextLevel(progress.getLevel());
                    lore.add(messages.get("menu.lore-progress", Map.of(
                            "bar", progressBar(progress.getXp(), nextXp),
                            "percent", String.valueOf(progressPercent(progress.getXp(), nextXp)))));
                }
            } else {
                lore.add(messages.get("menu.lore-not-joined", Map.of()));
            }
            if (!enabled) {
                // Disabled jobs (see /jobs admin) never show a click prompt - join() would just
                // reject it anyway, so there's nothing productive to invite the player to do.
                lore.add(messages.get("menu.lore-disabled", Map.of()));
            } else if (progress != null) {
                // A job with progress but not currently active was left, not never-joined - the
                // level/xp/prestige above is retained, so clicking resumes it rather than
                // restarting at level 1 (see PlayerJobManager#join).
                lore.add(messages.get(active ? "menu.lore-click-leave" : "menu.lore-click-rejoin", Map.of()));
            } else {
                lore.add(messages.get("menu.lore-click-join", Map.of()));
            }
            lore.add(messages.get("menu.lore-click-info", Map.of()));
            if (canViewLeaderboard) {
                lore.add(messages.get("menu.lore-click-leaderboard", Map.of()));
            }
            // Prestige isn't offered directly from this list even when maxed - right-click always
            // opens the info screen now (see JobsMenuListener), and that screen's own header shows
            // the prestige prompt when eligible. Advertising it here too would be a 4th, redundant
            // meaning for the same right-click gesture.
            meta.lore(lore);
            // Glint means exactly one thing: this job is currently active (holding one of the
            // player's job slots). A maxed-but-left job used to glint too as a "trophy", but that
            // made a shiny icon ambiguous between "working this now" and "mastered this once" -
            // maxed status is already visible in the level line, so it doesn't need the glint too.
            meta.setEnchantmentGlintOverride(active);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static String progressBar(double xp, double nextXp) {
        double ratio = nextXp > 0 ? Math.min(1.0, xp / nextXp) : 0;
        int filled = (int) Math.round(PROGRESS_BAR_LENGTH * ratio);
        return "&a" + "■".repeat(filled) + "&7" + "□".repeat(PROGRESS_BAR_LENGTH - filled);
    }

    private static int progressPercent(double xp, double nextXp) {
        double ratio = nextXp > 0 ? Math.min(1.0, xp / nextXp) : 0;
        return (int) Math.round(ratio * 100);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
