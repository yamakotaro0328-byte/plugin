package com.yamakotaro.ecorail.listeners;

import com.yamakotaro.ecorail.cart.CartManager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** When physics.player-collision is off, a managed cart passes through players instead of pushing them. */
public class VehicleCollisionListener implements Listener {

    private final JavaPlugin plugin;
    private final CartManager cartManager;

    public VehicleCollisionListener(JavaPlugin plugin, CartManager cartManager) {
        this.plugin = plugin;
        this.cartManager = cartManager;
    }

    @EventHandler
    public void onCollide(VehicleEntityCollisionEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Vehicle vehicle = event.getVehicle();
        if (!cartManager.isManaged(vehicle.getUniqueId())) {
            return;
        }
        if (!plugin.getConfig().getBoolean("physics.player-collision", true)) {
            event.setCancelled(true);
        }
    }
}
