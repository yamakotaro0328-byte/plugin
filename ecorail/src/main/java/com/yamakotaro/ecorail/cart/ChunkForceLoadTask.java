package com.yamakotaro.ecorail.cart;

import com.yamakotaro.ecorail.stop.StopPoint;
import com.yamakotaro.ecorail.stop.StopPointManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;

/**
 * Runs every physics.tick-interval-ticks: force-loads a small square of chunks around each
 * managed cart's live position (so vanilla rail physics keeps simulating it with nobody nearby),
 * pauses it at marked stop points, and otherwise never lets it sit stopped or run backward -
 * anything that would (a shove, a collision, momentum at a corner) is corrected the very same
 * tick. Only gives up on a cart once its entity is confirmed gone (picked up, unloaded to a
 * dimension EcoRail can't see, etc.) - see MAX_CONSECUTIVE_MISSES.
 */
public class ChunkForceLoadTask extends BukkitRunnable {

    private static final double MOVING_SPEED_SQUARED_THRESHOLD = 0.0009; // ~0.03 blocks/tick
    private static final int MAX_CONSECUTIVE_MISSES = 20;
    private static final int SAVE_EVERY_N_RUNS = 25; // ~5s at the default 4-tick interval

    private final JavaPlugin plugin;
    private final CartManager cartManager;
    private final StopPointManager stopPointManager;
    private int runCount;

    public ChunkForceLoadTask(JavaPlugin plugin, CartManager cartManager, StopPointManager stopPointManager) {
        this.plugin = plugin;
        this.cartManager = cartManager;
        this.stopPointManager = stopPointManager;
    }

    @Override
    public void run() {
        int radiusBlocks = plugin.getConfig().getInt("physics.chunk-load-radius-blocks", 8);
        double stopRadius = plugin.getConfig().getDouble("physics.stop-radius", 1.5);
        double launchSpeed = plugin.getConfig().getDouble("physics.launch-speed", 0.4);
        long now = System.currentTimeMillis();

        for (Iterator<ManagedCart> it = cartManager.all().iterator(); it.hasNext(); ) {
            ManagedCart cart = it.next();
            World world = Bukkit.getWorld(cart.getWorld());
            if (world == null) {
                continue;
            }

            updateChunkTickets(world, cart, radiusBlocks);

            Entity entity = Bukkit.getEntity(cart.getEntityId());
            if (!(entity instanceof Minecart minecart) || entity.isDead()) {
                cart.incrementMissCount();
                if (cart.getMissCount() > MAX_CONSECUTIVE_MISSES) {
                    releaseAllChunks(world, cart);
                    it.remove();
                }
                continue;
            }
            cart.resetMissCount();

            Location location = minecart.getLocation();
            cart.setLastBlock(location.getBlockX(), location.getBlockZ());

            if (cart.isDwelling(now)) {
                continue;
            }
            if (cart.getDwellUntilMillis() != 0) {
                // Dwell just elapsed - relaunch the same direction it was already heading.
                minecart.setVelocity(new Vector(cart.getForwardDirX() * launchSpeed, 0, cart.getForwardDirZ() * launchSpeed));
                cart.setDwellUntilMillis(0);
                continue;
            }

            enforceContinuousMotion(minecart, cart, launchSpeed);
            handleStopPoint(minecart, cart, world, location, stopRadius);
        }

        if (++runCount >= SAVE_EVERY_N_RUNS) {
            runCount = 0;
            cartManager.save();
        }
    }

    private void updateChunkTickets(World world, ManagedCart cart, int radiusBlocks) {
        int minChunkX = (cart.getLastBlockX() - radiusBlocks) >> 4;
        int maxChunkX = (cart.getLastBlockX() + radiusBlocks) >> 4;
        int minChunkZ = (cart.getLastBlockZ() - radiusBlocks) >> 4;
        int maxChunkZ = (cart.getLastBlockZ() + radiusBlocks) >> 4;

        Set<Long> desired = new HashSet<>();
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                desired.add(packChunk(cx, cz));
            }
        }
        for (Long key : desired) {
            if (cart.getHeldChunks().add(key)) {
                world.addPluginChunkTicket(unpackX(key), unpackZ(key), plugin);
            }
        }
        cart.getHeldChunks().removeIf(key -> {
            if (desired.contains(key)) {
                return false;
            }
            world.removePluginChunkTicket(unpackX(key), unpackZ(key), plugin);
            return true;
        });
    }

    /**
     * Never lets a cart with an established direction sit stopped or run backward: a shove,
     * another entity's collision, or momentum at a corner all get corrected the same tick they
     * happen. A cart that has never moved (forwardDir still (0,0) - nobody has pushed it yet) is
     * left alone rather than forced into motion.
     */
    private void enforceContinuousMotion(Minecart minecart, ManagedCart cart, double launchSpeed) {
        Vector velocity = minecart.getVelocity();
        boolean hasDirection = cart.getForwardDirX() != 0 || cart.getForwardDirZ() != 0;

        if (hasDirection) {
            double forwardComponent = velocity.getX() * cart.getForwardDirX() + velocity.getZ() * cart.getForwardDirZ();
            double horizontalSpeedSquared = velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ();
            if (forwardComponent < 0 || horizontalSpeedSquared <= MOVING_SPEED_SQUARED_THRESHOLD) {
                // Running backward, or stalled/stopped outright - force it back up to speed forward.
                minecart.setVelocity(new Vector(cart.getForwardDirX() * launchSpeed, velocity.getY(), cart.getForwardDirZ() * launchSpeed));
                return;
            }
        }
        if (velocity.lengthSquared() > MOVING_SPEED_SQUARED_THRESHOLD) {
            updateForwardDirection(cart, velocity);
        }
    }

    /** Snaps the (possibly diagonal, on a corner rail) velocity to the dominant cardinal axis. */
    private void updateForwardDirection(ManagedCart cart, Vector velocity) {
        if (Math.abs(velocity.getX()) >= Math.abs(velocity.getZ())) {
            cart.setForwardDirection(velocity.getX() > 0 ? 1 : -1, 0);
        } else {
            cart.setForwardDirection(0, velocity.getZ() > 0 ? 1 : -1);
        }
    }

    private void handleStopPoint(Minecart minecart, ManagedCart cart, World world, Location location, double stopRadius) {
        Optional<StopPoint> nearby = stopPointManager.findNearest(location, stopRadius);
        if (nearby.isEmpty()) {
            cart.setLastHandledStopKey(null);
            return;
        }
        StopPoint stop = nearby.get();
        if (stop.key().equals(cart.getLastHandledStopKey())) {
            return; // already handled this same stop - wait until the cart moves away before it can trigger again
        }
        minecart.setVelocity(new Vector(0, 0, 0));
        cart.setDwellUntilMillis(System.currentTimeMillis() + stop.dwellSeconds() * 1000L);
        cart.setLastHandledStopKey(stop.key());
    }

    private void releaseAllChunks(World world, ManagedCart cart) {
        for (Long key : cart.getHeldChunks()) {
            world.removePluginChunkTicket(unpackX(key), unpackZ(key), plugin);
        }
        cart.getHeldChunks().clear();
    }

    private static long packChunk(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int unpackX(long key) {
        return (int) (key >> 32);
    }

    private static int unpackZ(long key) {
        return (int) key;
    }
}
