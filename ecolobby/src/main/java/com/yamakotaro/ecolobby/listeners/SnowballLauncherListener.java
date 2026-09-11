package com.yamakotaro.ecolobby.listeners;

import com.yamakotaro.ecolobby.EcoLobbyPlugin;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.util.Vector;

/**
 * A "snowball rocket jump": throwing a snowball near your own feet launches YOU upward - not
 * whoever it hits, so this never turns into knocking other players around (that would read as
 * PvP even with real damage disabled). Only the shooter, and only when the impact point is close
 * to where they're currently standing.
 */
public class SnowballLauncherListener implements Listener {

    private final EcoLobbyPlugin plugin;

    public SnowballLauncherListener(EcoLobbyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        if (!plugin.isFeatureEnabled("snowball-launcher") || !(event.getEntity() instanceof Snowball snowball)) {
            return;
        }
        if (!(snowball.getShooter() instanceof Player shooter)) {
            return;
        }
        Location impact = snowball.getLocation();
        double radius = plugin.getConfig().getDouble("snowball-launcher.radius", 3.0);
        if (shooter.getLocation().distanceSquared(impact) > radius * radius) {
            return;
        }

        double velocity = plugin.getConfig().getDouble("snowball-launcher.velocity", 1.1);
        Vector boost = shooter.getVelocity();
        boost.setY(velocity);
        shooter.setVelocity(boost);
        shooter.getWorld().spawnParticle(Particle.CLOUD, impact, 15, 0.3, 0.3, 0.3, 0.05);
        shooter.getWorld().playSound(impact, Sound.ENTITY_SLIME_JUMP, 1f, 1.6f);
    }
}
