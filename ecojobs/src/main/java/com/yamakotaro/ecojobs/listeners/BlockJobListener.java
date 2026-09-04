package com.yamakotaro.ecojobs.listeners;

import com.yamakotaro.ecojobs.PlacedBlockTracker;
import com.yamakotaro.ecojobs.PlayerJobManager;
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

/**
 * Handles the miner/digger/woodcutter/farmer/builder/beekeeper jobs, all of which are triggered
 * by ordinary block placing/breaking/harvesting.
 */
public class BlockJobListener implements Listener {

    private final PlayerJobManager jobs;
    private final PlacedBlockTracker placedBlocks;

    public BlockJobListener(PlayerJobManager jobs, PlacedBlockTracker placedBlocks) {
        this.jobs = jobs;
        this.placedBlocks = placedBlocks;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        placedBlocks.markPlaced(event.getBlock());
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
            jobs.reward(player, "farmer", "harvest-crop", type.name(), 1);
            return;
        }

        // Unlike age-based crops, a melon/pumpkin block can be placed by hand (already at its
        // final state) and instantly re-broken, so it's checked like miner/digger/woodcutter.
        if (type == Material.MELON || type == Material.PUMPKIN) {
            if (!placedBlocks.wasPlaced(block)) {
                jobs.reward(player, "farmer", "harvest-tall-plant", type.name(), 1);
            }
            return;
        }

        if (placedBlocks.wasPlaced(block)) {
            return;
        }
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
}
