package com.yamakotaro.ecojobs.listeners;

import com.yamakotaro.ecojobs.JobDefinition;
import com.yamakotaro.ecojobs.JobManager;
import com.yamakotaro.ecojobs.PerkManager;
import com.yamakotaro.ecojobs.PlacedBlockTracker;
import com.yamakotaro.ecojobs.PlayerJobManager;
import com.yamakotaro.ecojobs.PlayerJobProgress;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Handles the miner/digger/woodcutter/farmer/builder/beekeeper jobs, all of which are triggered
 * by ordinary block placing/breaking/harvesting. Also applies the double-drop perk (miner/
 * digger/woodcutter/farmer) and the auto-smelt perk (miner only) - see PerkManager.
 */
public class BlockJobListener implements Listener {

    private static final List<String> MINING_JOB_IDS = List.of("miner", "digger", "woodcutter");

    private final PlayerJobManager jobs;
    private final PlacedBlockTracker placedBlocks;
    private final JobManager jobManager;
    private final PerkManager perkManager;

    public BlockJobListener(PlayerJobManager jobs, PlacedBlockTracker placedBlocks, JobManager jobManager, PerkManager perkManager) {
        this.jobs = jobs;
        this.placedBlocks = placedBlocks;
        this.jobManager = jobManager;
        this.perkManager = perkManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        placedBlocks.markPlaced(event.getBlock());
        // 同じ座標に置き直しただけでは支払わない。採掘側は再破壊がトラッカーで無効になるのに
        // 建築士は設置のたびに必ず支払われていたため、「置く→壊す→また置く」を繰り返すだけで
        // (オートクリッカーでも)無限に稼げてしまっていた。
        if (!placedBlocks.markBuildPaid(event.getBlock())) {
            return;
        }
        jobs.reward(event.getPlayer(), "builder", "place-block", event.getBlock().getType().name(), 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material type = block.getType();

        // Crops always need real time to reach a harvestable age, so unlike everything else in
        // this method, farming is never checked against the placed-block tracker: replanting is
        // the normal way to earn this money, not an exploit (see config.yml's farmer comment).
        if (isFullyGrownCrop(block)) {
            applyDoubleDrop(event, player, block, "farmer");
            jobs.reward(player, "farmer", "harvest-crop", type.name(), 1);
            return;
        }

        // Unlike age-based crops, a melon/pumpkin block can be placed by hand (already at its
        // final state) and instantly re-broken, so it's checked like miner/digger/woodcutter.
        if (type == Material.MELON || type == Material.PUMPKIN) {
            if (!placedBlocks.wasPlaced(block)) {
                applyDoubleDrop(event, player, block, "farmer");
                jobs.reward(player, "farmer", "harvest-tall-plant", type.name(), 1);
            }
            return;
        }

        if (placedBlocks.wasPlaced(block)) {
            return;
        }
        applyMiningPerks(event, player, block, type);
        jobs.reward(player, "miner", "break-block", type.name(), 1);
        jobs.reward(player, "digger", "break-block", type.name(), 1);
        jobs.reward(player, "woodcutter", "break-block", type.name(), 1);
    }

    /**
     * Fires for "harvest without destroying the block" interactions - most relevantly, taking
     * honey/honeycomb from a beehive/bee nest with a bottle or shears. A full hive can hand over
     * several honeycombs in one harvest, so this scales by the actual item count rather than
     * paying a flat 1 regardless of how much came out.
     */
    @EventHandler(ignoreCancelled = true)
    public void onHarvestBlock(PlayerHarvestBlockEvent event) {
        int amount = event.getItemsHarvested().stream().mapToInt(ItemStack::getAmount).sum();
        if (amount <= 0) {
            return;
        }
        jobs.reward(event.getPlayer(), "beekeeper", "harvest-block", event.getHarvestedBlock().getType().name(), amount);
    }

    private boolean isFullyGrownCrop(Block block) {
        return block.getBlockData() instanceof Ageable ageable && ageable.getAge() >= ageable.getMaximumAge();
    }

    /**
     * Checks miner/digger/woodcutter's double-drop perk (whichever of the three the player has
     * joined and unlocked it for) and miner's auto-smelt perk, and - if either applies - replaces
     * the block's natural drops accordingly. A no-op (drops untouched) unless at least one perk
     * actually fires, so this never affects a player with no perks unlocked.
     */
    private void applyMiningPerks(BlockBreakEvent event, Player player, Block block, Material type) {
        Map<String, PlayerJobProgress> joined = jobs.joinedJobs(player.getUniqueId());
        boolean doubleDrop = false;
        Material smelted = null;
        for (String jobId : MINING_JOB_IDS) {
            PlayerJobProgress progress = joined.get(jobId);
            JobDefinition job = progress != null ? jobManager.get(jobId) : null;
            if (job == null) {
                continue;
            }
            int effectiveLevel = perkManager.effectiveLevel(progress);
            if (!doubleDrop && perkManager.rollDoubleDrop(job, effectiveLevel)) {
                doubleDrop = true;
            }
            if (smelted == null && "miner".equals(jobId) && perkManager.hasAutoSmelt(job, effectiveLevel)) {
                smelted = perkManager.smeltedResult(type);
            }
        }
        if (!doubleDrop && smelted == null) {
            return;
        }
        Collection<ItemStack> drops = block.getDrops(player.getInventory().getItemInMainHand(), player);
        if (drops.isEmpty()) {
            return;
        }
        event.setDropItems(false);
        if (smelted != null) {
            // Replaces the whole drop with the smelted result rather than trying to match each
            // individual drop back to a "raw" item (several ores drop something other than their
            // own block type already, e.g. iron/gold/copper ore drop raw materials, coal/diamond/
            // emerald/redstone/lapis ore drop their item form) - the total count (which fortune
            // may already have multiplied) carries over as-is.
            int totalAmount = drops.stream().mapToInt(ItemStack::getAmount).sum();
            dropStack(block, new ItemStack(smelted, totalAmount), doubleDrop);
        } else {
            for (ItemStack drop : drops) {
                dropStack(block, drop, doubleDrop);
            }
        }
    }

    /** Farmer's double-drop only (no auto-smelt concept for crops/melons/pumpkins). */
    private void applyDoubleDrop(BlockBreakEvent event, Player player, Block block, String jobId) {
        PlayerJobProgress progress = jobs.joinedJobs(player.getUniqueId()).get(jobId);
        JobDefinition job = progress != null ? jobManager.get(jobId) : null;
        if (job == null || !perkManager.rollDoubleDrop(job, perkManager.effectiveLevel(progress))) {
            return;
        }
        Collection<ItemStack> drops = block.getDrops(player.getInventory().getItemInMainHand(), player);
        if (drops.isEmpty()) {
            return;
        }
        event.setDropItems(false);
        for (ItemStack drop : drops) {
            dropStack(block, drop, true);
        }
    }

    private void dropStack(Block block, ItemStack stack, boolean doubled) {
        block.getWorld().dropItemNaturally(block.getLocation(), stack);
        if (doubled) {
            block.getWorld().dropItemNaturally(block.getLocation(), stack.clone());
        }
    }
}
