package com.yamakotaro.ecolobby.tasks;

import com.yamakotaro.ecolobby.EcoLobbyPlugin;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/** Replays each online player's equipped particle trail (see PlayerStateManager) at their feet -
 * only runs while this server is the lobby, matching every other lobby-only cosmetic. */
public class ParticleTrailTask extends BukkitRunnable {

    private final EcoLobbyPlugin plugin;

    public ParticleTrailTask(EcoLobbyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (!plugin.isLobbyServer() || !plugin.isFeatureEnabled("particle-trail")) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Particle particle = plugin.getPlayerStateManager().getTrail(player.getUniqueId());
            if (particle == null) {
                continue;
            }
            player.getWorld().spawnParticle(particle, player.getLocation().add(0, 0.1, 0), 4, 0.2, 0.05, 0.2, 0.01);
        }
    }
}
