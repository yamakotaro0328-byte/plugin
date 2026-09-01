package com.yamakotaro.ecorail.listeners;

import com.yamakotaro.ecorail.cart.CartManager;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;

/** A managed cart always passes through players, mobs, and everything else in its path. */
public class VehicleCollisionListener implements Listener {

    private final CartManager cartManager;

    public VehicleCollisionListener(CartManager cartManager) {
        this.cartManager = cartManager;
    }

    @EventHandler
    public void onCollide(VehicleEntityCollisionEvent event) {
        Vehicle vehicle = event.getVehicle();
        if (cartManager.isManaged(vehicle.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
