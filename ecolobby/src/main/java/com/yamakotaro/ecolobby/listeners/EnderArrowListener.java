package com.yamakotaro.ecolobby.listeners;

import com.yamakotaro.ecolobby.EcoLobbyPlugin;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * The "ender arrow" fun item: shooting the marked bow teleports the shooter to wherever the arrow
 * lands instead of dealing damage - a ranged, more precise alternative to an ender pearl. Tagging
 * follows the same launch-then-tag correlation Manhunt's SpecialProjectileListener uses for its
 * thrown items (check what's in hand at ProjectileLaunchEvent, tag the entity, read the tag back
 * on ProjectileHitEvent).
 */
public class EnderArrowListener implements Listener {

    private final EcoLobbyPlugin plugin;

    public EnderArrowListener(EcoLobbyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!plugin.isFeatureEnabled("ender-arrow") || !(event.getEntity() instanceof Arrow arrow)) {
            return;
        }
        if (!(arrow.getShooter() instanceof Player shooter)) {
            return;
        }
        if (!isEnderArrowBow(shooter.getInventory().getItemInMainHand())
                && !isEnderArrowBow(shooter.getInventory().getItemInOffHand())) {
            return;
        }
        arrow.getPersistentDataContainer().set(plugin.getEnderArrowProjectileKey(), PersistentDataType.BOOLEAN, true);
    }

    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) {
            return;
        }
        if (!Boolean.TRUE.equals(arrow.getPersistentDataContainer().get(plugin.getEnderArrowProjectileKey(), PersistentDataType.BOOLEAN))) {
            return;
        }
        if (!(arrow.getShooter() instanceof Player shooter)) {
            return;
        }
        Location landing = arrow.getLocation();
        shooter.teleport(landing.clone().add(0, 0.5, 0));
        shooter.getWorld().spawnParticle(Particle.PORTAL, landing, 40, 0.3, 0.5, 0.3, 0.1);
        shooter.playSound(landing, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        arrow.remove();
    }

    private boolean isEnderArrowBow(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return Boolean.TRUE.equals(meta.getPersistentDataContainer().get(plugin.getEnderArrowBowKey(), PersistentDataType.BOOLEAN));
    }
}
