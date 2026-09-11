package com.yamakotaro.ecolobby.listeners;

import com.yamakotaro.ecolobby.EcoLobbyPlugin;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.util.Vector;

/**
 * Every snowball thrown while this server is the lobby launches whoever it hits straight up,
 * instead of the barely-noticeable vanilla nudge - harmless chaos since real damage is already
 * disabled by ProtectionListener. Applies to any snowball (no marker needed - throwing one is
 * entirely vanilla, only the resulting knockback is custom), so no separate interact handling.
 */
public class SnowballLauncherListener implements Listener {

    private final EcoLobbyPlugin plugin;

    public SnowballLauncherListener(EcoLobbyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        if (!plugin.isFeatureEnabled("snowball-launcher") || !(event.getEntity() instanceof Snowball)) {
            return;
        }
        if (!(event.getHitEntity() instanceof LivingEntity target)) {
            return;
        }
        double velocity = plugin.getConfig().getDouble("snowball-launcher.velocity", 1.1);
        Vector boost = target.getVelocity();
        boost.setY(velocity);
        target.setVelocity(boost);
        target.getWorld().spawnParticle(Particle.CLOUD, target.getLocation(), 15, 0.3, 0.3, 0.3, 0.05);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_SLIME_JUMP, 1f, 1.6f);
    }
}
