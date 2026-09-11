package com.yamakotaro.ecolobby.listeners;

import com.yamakotaro.ecolobby.EcoLobbyPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

/**
 * Keeps the lobby tidy and safe regardless of gamemode - only registered when this server is the
 * lobby (see EcoLobbyPlugin#onEnable) and features.protection is enabled. Each rule below has its
 * own protection.* config toggle so an admin can turn any single one off without disabling the rest.
 */
public class ProtectionListener implements Listener {

    private final EcoLobbyPlugin plugin;

    public ProtectionListener(EcoLobbyPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean enabled(String key) {
        return plugin.getConfig().getBoolean("protection." + key, true);
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (enabled("disable-pvp") && event.getEntity() instanceof Player && event.getDamager() instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (enabled("disable-fall-damage") && event.getCause() == EntityDamageEvent.DamageCause.FALL
                && event.getEntity() instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (enabled("disable-block-break")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (enabled("disable-block-place")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (enabled("disable-item-drop")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (enabled("disable-item-pickup") && event.getEntity() instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (enabled("disable-hunger")) {
            event.setCancelled(true);
        }
    }
}
