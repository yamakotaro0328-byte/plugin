package com.yamakotaro.ecojobs.listeners;

import com.yamakotaro.ecojobs.PlayerJobManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Handles explorer: pays out every time a joined player reaches a new farthest (horizontal-only)
 * distance from their current world's spawn point. Only does any work at all for players who
 * have actually joined explorer, and only re-checks on a block-level horizontal move (not on
 * every look-around/jump, which would otherwise fire this on nearly every tick).
 */
public class ExplorerListener implements Listener {

    private final PlayerJobManager jobs;

    public ExplorerListener(PlayerJobManager jobs) {
        this.jobs = jobs;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ())) {
            return;
        }
        Player player = event.getPlayer();
        if (!jobs.isJoined(player.getUniqueId(), "explorer")) {
            return;
        }
        Location spawn = player.getWorld().getSpawnLocation();
        double dx = to.getX() - spawn.getX();
        double dz = to.getZ() - spawn.getZ();
        jobs.checkExplorerMilestones(player, player.getWorld().getName(), Math.sqrt(dx * dx + dz * dz));
    }
}
