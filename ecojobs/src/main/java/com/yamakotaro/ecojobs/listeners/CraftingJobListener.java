package com.yamakotaro.ecojobs.listeners;

import com.yamakotaro.ecojobs.PlayerJobManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles smelter/crafter/enchanter.
 */
public class CraftingJobListener implements Listener {

    private final PlayerJobManager jobs;

    public CraftingJobListener(PlayerJobManager jobs) {
        this.jobs = jobs;
    }

    @EventHandler(ignoreCancelled = true)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        // Scaled by how many items were actually pulled out, not a flat 1 - otherwise taking a
        // full stack out in one click would pay the same as taking a single item, rewarding the
        // tedious "grab one at a time" approach over normal play.
        jobs.reward(event.getPlayer(), "smelter", "smelt-item", event.getItemType().name(), event.getItemAmount());
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack result = event.getRecipe().getResult();
        if (result.getType().isAir()) {
            return;
        }
        // Some recipes yield more than 1 per craft (e.g. 4 torches) - scale by that yield so a
        // higher-yield recipe pays proportionally more, not the same as a single-item one.
        jobs.reward(player, "crafter", "craft-item", result.getType().name(), result.getAmount());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        // Scaled by the enchant's own XP-level cost - a natural difficulty proxy - rather than a
        // flat rate; see ActionReward's moneyPerLevel/xpPerLevel and config.yml's enchanter job.
        jobs.reward(event.getEnchanter(), "enchanter", "enchant-item", "default", event.getExpLevelCost());
    }
}
