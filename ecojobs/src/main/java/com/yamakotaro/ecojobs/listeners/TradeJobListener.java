package com.yamakotaro.ecojobs.listeners;

import com.yamakotaro.ecojobs.PlayerJobManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;

/**
 * Handles merchant. There's no dedicated "trade completed" event in the base Bukkit API, so this
 * uses the same technique most plugins use: a villager trade GUI always has exactly 3 slots
 * (0/1 = ingredients, 2 = result), so clicking a non-empty result slot in a MerchantInventory
 * means a trade just completed.
 */
public class TradeJobListener implements Listener {

    private static final int RESULT_SLOT = 2;

    private final PlayerJobManager jobs;

    public TradeJobListener(PlayerJobManager jobs) {
        this.jobs = jobs;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getClickedInventory() instanceof MerchantInventory)) {
            return;
        }
        if (event.getSlot() != RESULT_SLOT) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType().isAir()) {
            return;
        }
        jobs.reward(player, "merchant", "trade-villager", "default", 1);
    }
}
