package com.yamakotaro.ecorail.listeners;

import com.yamakotaro.ecorail.cart.CartManager;
import com.yamakotaro.ecorail.cart.ManagedCart;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Every minecart on the server becomes managed the moment EcoRail knows about it - there's no
 * opt-in step. A freshly-placed cart is attributed to whoever placed it (for /ecorail-style
 * protection - see ProtectionListener) by correlating the PlayerInteractEvent that placed it
 * with the VehicleCreateEvent that follows, since core Bukkit has no direct "who placed this
 * entity" event; a cart discovered any other way (already existing, or its chunk just loaded)
 * has no owner and is only breakable by ecorail.admin.
 */
public class CartAutoManageListener implements Listener {

    private final JavaPlugin plugin;
    private final CartManager cartManager;
    private UUID pendingPlacer;

    public CartAutoManageListener(JavaPlugin plugin, CartManager cartManager) {
        this.plugin = plugin;
        this.cartManager = cartManager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block clicked = event.getClickedBlock();
        ItemStack item = event.getItem();
        if (clicked == null || !isRail(clicked.getType()) || item == null || item.getType() != Material.MINECART) {
            return;
        }
        UUID placer = event.getPlayer().getUniqueId();
        pendingPlacer = placer;
        // In case the placement silently fails (no VehicleCreateEvent follows), don't let a
        // stale placer linger and get wrongly credited for some unrelated cart later.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (placer.equals(pendingPlacer)) {
                pendingPlacer = null;
            }
        });
    }

    @EventHandler
    public void onVehicleCreate(VehicleCreateEvent event) {
        UUID owner = pendingPlacer;
        pendingPlacer = null;
        manage(event.getVehicle(), owner);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            manage(entity, null);
        }
    }

    /** Scans every currently-loaded chunk once at startup, for carts that existed before this restart. */
    public void scanLoadedChunks() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                for (Entity entity : chunk.getEntities()) {
                    manage(entity, null);
                }
            }
        }
    }

    private void manage(Entity entity, UUID owner) {
        if (!(entity instanceof Minecart minecart) || cartManager.isManaged(minecart.getUniqueId())) {
            return;
        }
        Location location = minecart.getLocation();
        cartManager.register(new ManagedCart(minecart.getUniqueId(), location.getWorld().getName(),
                location.getBlockX(), location.getBlockZ(), 0, 0, owner));
    }

    public static boolean isRail(Material material) {
        return material == Material.RAIL || material == Material.POWERED_RAIL
                || material == Material.DETECTOR_RAIL || material == Material.ACTIVATOR_RAIL;
    }
}
