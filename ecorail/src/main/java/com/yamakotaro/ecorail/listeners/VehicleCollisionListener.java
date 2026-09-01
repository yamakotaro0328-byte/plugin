package com.yamakotaro.ecorail.listeners;

import com.yamakotaro.ecorail.cart.CartManager;
import com.yamakotaro.ecorail.cart.ManagedCart;
import com.yamakotaro.ecorail.settings.PlayerSettingsManager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;

import java.util.Optional;

/** Lets a player opt their own carts out of pushing other players around (settings.player-collision). */
public class VehicleCollisionListener implements Listener {

    private final CartManager cartManager;
    private final PlayerSettingsManager settingsManager;

    public VehicleCollisionListener(CartManager cartManager, PlayerSettingsManager settingsManager) {
        this.cartManager = cartManager;
        this.settingsManager = settingsManager;
    }

    @EventHandler
    public void onCollide(VehicleEntityCollisionEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Vehicle vehicle = event.getVehicle();
        Optional<ManagedCart> cart = cartManager.find(vehicle.getUniqueId());
        if (cart.isEmpty() || cart.get().getOwnerId() == null) {
            return;
        }
        if (!settingsManager.get(cart.get().getOwnerId()).playerCollision()) {
            event.setCancelled(true);
        }
    }
}
