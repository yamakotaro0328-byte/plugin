package com.yamakotaro.ecojobs.menu;

import com.yamakotaro.ecojobs.JobManager;
import com.yamakotaro.ecojobs.Messages;
import com.yamakotaro.ecojobs.PlayerJobManager;
import com.yamakotaro.ecojobs.PlayerJobProgress;
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
import java.util.UUID;

public class JobsMenuHolder implements InventoryHolder {

    public static final int CLOSE_SLOT = 31;

    private static final Map<String, Material> ICONS = Map.ofEntries(
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

    private final Messages messages;
    private final Inventory inventory;
    private final Map<Integer, String> slotToJobId = new HashMap<>();

    public JobsMenuHolder(Messages messages) {
        this.messages = messages;
        this.inventory = Bukkit.createInventory(this, 36, messages.get("menu.title", Map.of()));
    }

    public String jobIdAt(int slot) {
        return slotToJobId.get(slot);
    }

    public void render(JobManager jobManager, PlayerJobManager playerJobManager, UUID viewer) {
        inventory.clear();
        slotToJobId.clear();
        Map<String, PlayerJobProgress> joined = playerJobManager.joinedJobs(viewer);
        int slot = 0;
        for (String jobId : jobManager.all().keySet()) {
            if (slot >= CLOSE_SLOT) {
                break;
            }
            PlayerJobProgress progress = joined.get(jobId);
            inventory.setItem(slot, buildJobItem(jobId, progress, playerJobManager));
            slotToJobId.put(slot, jobId);
            slot++;
        }
        inventory.setItem(CLOSE_SLOT, closeItem());
    }

    private ItemStack buildJobItem(String jobId, PlayerJobProgress progress, PlayerJobManager playerJobManager) {
        Material material = ICONS.getOrDefault(jobId, Material.PAPER);
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(messages.get("menu.job-title", Map.of("job", messages.jobName(jobId))));
            List<Component> lore = new ArrayList<>();
            if (progress != null) {
                lore.add(messages.get("menu.lore-level", Map.of(
                        "level", String.valueOf(progress.getLevel()),
                        "xp", String.format("%.0f", progress.getXp()),
                        "next_xp", String.format("%.0f", playerJobManager.xpToNextLevel(progress.getLevel())))));
                lore.add(messages.get("menu.lore-click-leave", Map.of()));
            } else {
                lore.add(messages.get("menu.lore-not-joined", Map.of()));
                lore.add(messages.get("menu.lore-click-join", Map.of()));
            }
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack closeItem() {
        ItemStack stack = new ItemStack(Material.BARRIER);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Close"));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
