package com.yamakotaro.ecojobs.listeners;

import com.yamakotaro.ecojobs.PlayerJobManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;

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
        jobs.reward(event.getPlayer(), "smelter", "smelt-item", event.getItemType().name(), 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getRecipe().getResult().getType().isAir()) {
            return;
        }
        jobs.reward(player, "crafter", "craft-item", event.getRecipe().getResult().getType().name(), 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        // Scaled by the enchant's own XP-level cost - a natural difficulty proxy - rather than a
        // flat rate; see ActionReward's moneyPerLevel/xpPerLevel and config.yml's enchanter job.
        jobs.reward(event.getEnchanter(), "enchanter", "enchant-item", "default", event.getExpLevelCost());
    }
}
