package com.yamakotaro.ecorail.listeners;

import com.yamakotaro.ecorail.cart.CartManager;
import com.yamakotaro.ecorail.cart.ManagedCart;
import com.yamakotaro.ecorail.protect.RailOwnerManager;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;

import java.util.UUID;

/**
 * Rails and minecarts can't be broken by anyone except whoever placed them (recorded at
 * placement time) and players with ecorail.admin - everyone else is blocked. A rail or cart
 * with no recorded owner (it existed before EcoRail was tracking it) defaults to admin-only.
 */
public class ProtectionListener implements Listener {

    private final RailOwnerManager railOwnerManager;
    private final CartManager cartManager;

    public ProtectionListener(RailOwnerManager railOwnerManager, CartManager cartManager) {
        this.railOwnerManager = railOwnerManager;
        this.cartManager = cartManager;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (CartAutoManageListener.isRail(event.getBlock().getType())) {
            railOwnerManager.recordOwner(event.getBlock(), event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!CartAutoManageListener.isRail(block.getType())) {
            return;
        }
        if (canBreak(event.getPlayer(), railOwnerManager.getOwner(block).orElse(null))) {
            railOwnerManager.removeOwner(block);
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onVehicleDamage(VehicleDamageEvent event) {
        if (isProtectedFrom(event.getVehicle(), event.getAttacker())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (isProtectedFrom(event.getVehicle(), event.getAttacker())) {
            event.setCancelled(true);
        }
    }

    private boolean isProtectedFrom(Entity vehicle, Entity attacker) {
        if (!(vehicle instanceof Minecart minecart) || !cartManager.isManaged(minecart.getUniqueId())) {
            return false;
        }
        if (!(attacker instanceof Player player)) {
            return true; // no player attacker (explosion, lava, etc.) - always protected
        }
        return !canBreak(player, cartManager.find(minecart.getUniqueId()).map(ManagedCart::getOwnerId).orElse(null));
    }

    private boolean canBreak(Player player, UUID owner) {
        return player.hasPermission("ecorail.admin") || (owner != null && owner.equals(player.getUniqueId()));
    }
}
