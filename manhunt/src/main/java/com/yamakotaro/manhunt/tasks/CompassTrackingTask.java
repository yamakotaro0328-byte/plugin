package com.yamakotaro.manhunt.tasks;

import com.yamakotaro.manhunt.game.ManhuntGame;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

/** Keeps every hunter's compass pointed at the nearest (same-world) alive runner. */
public class CompassTrackingTask extends BukkitRunnable {

    private final ManhuntGame game;

    public CompassTrackingTask(ManhuntGame game) {
        this.game = game;
    }

    @Override
    public void run() {
        if (!game.isRunning()) {
            return;
        }
        List<Player> runners = game.onlineAliveRunners();
        if (runners.isEmpty()) {
            return;
        }
        for (Player hunter : game.onlineHunters()) {
            Player nearest = null;
            double nearestDistanceSquared = Double.MAX_VALUE;
            for (Player runner : runners) {
                if (!runner.getWorld().equals(hunter.getWorld())) {
                    continue;
                }
                double distanceSquared = runner.getLocation().distanceSquared(hunter.getLocation());
                if (distanceSquared < nearestDistanceSquared) {
                    nearestDistanceSquared = distanceSquared;
                    nearest = runner;
                }
            }
            if (nearest != null) {
                hunter.setCompassTarget(nearest.getLocation());
            }
        }
    }
}
