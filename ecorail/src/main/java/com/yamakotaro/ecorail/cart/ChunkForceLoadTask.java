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
 * managed cart's last known position (so vanilla rail physics keeps simulating it with nobody
 * nearby), pauses it at marked stop points, corrects it back to its running direction if it
 * starts rolling backward, and gives up on carts that go missing or sit idle too long (a safety
 * net against a derailed cart pinning chunks loaded forever).
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
        int radius = plugin.getConfig().getInt("physics.chunk-load-radius", 2);
        double stopRadius = plugin.getConfig().getDouble("physics.stop-radius", 1.5);
        double launchSpeed = plugin.getConfig().getDouble("physics.launch-speed", 0.4);
        boolean antiReverse = plugin.getConfig().getBoolean("physics.anti-reverse", true);
        long idleReleaseMillis = plugin.getConfig().getLong("physics.idle-release-seconds", 300) * 1000L;
        long now = System.currentTimeMillis();

        for (Iterator<ManagedCart> it = cartManager.all().iterator(); it.hasNext(); ) {
            ManagedCart cart = it.next();
            World world = Bukkit.getWorld(cart.getWorld());
            if (world == null) {
                continue;
            }

            updateChunkTickets(world, cart, radius);

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
            cart.setLastChunk(location.getBlockX() >> 4, location.getBlockZ() >> 4);

            if (cart.isDwelling(now)) {
                continue;
            }
            if (cart.getDwellUntilMillis() != 0) {
                // Dwell just elapsed - relaunch the same direction it was already heading.
                minecart.setVelocity(new Vector(cart.getForwardDirX() * launchSpeed, 0, cart.getForwardDirZ() * launchSpeed));
                cart.setDwellUntilMillis(0);
                cart.markMoved();
                continue;
            }

            Vector velocity = minecart.getVelocity();
            if (antiReverse) {
                velocity = correctReverseRunning(minecart, cart, velocity);
            }
            if (velocity.lengthSquared() > MOVING_SPEED_SQUARED_THRESHOLD) {
                cart.markMoved();
                updateForwardDirection(cart, velocity);
            }

            if (handleStopPoint(minecart, cart, world, location, stopRadius)) {
                continue;
            }

            if (now - cart.getLastMovedAt() > idleReleaseMillis) {
                releaseAllChunks(world, cart);
                it.remove();
            }
        }

        if (++runCount >= SAVE_EVERY_N_RUNS) {
            runCount = 0;
            cartManager.save();
        }
    }

    private void updateChunkTickets(World world, ManagedCart cart, int radius) {
        Set<Long> desired = new HashSet<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                desired.add(packChunk(cart.getLastChunkX() + dx, cart.getLastChunkZ() + dz));
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

    /** @return the (possibly corrected) velocity. */
    private Vector correctReverseRunning(Minecart minecart, ManagedCart cart, Vector velocity) {
        if (cart.getForwardDirX() == 0 && cart.getForwardDirZ() == 0) {
            return velocity;
        }
        double forwardComponent = velocity.getX() * cart.getForwardDirX() + velocity.getZ() * cart.getForwardDirZ();
        if (forwardComponent >= 0) {
            return velocity;
        }
        double horizontalSpeed = Math.sqrt(velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ());
        Vector corrected = new Vector(cart.getForwardDirX() * horizontalSpeed, velocity.getY(), cart.getForwardDirZ() * horizontalSpeed);
        minecart.setVelocity(corrected);
        return corrected;
    }

    /** Snaps the (possibly diagonal, on a corner rail) velocity to the dominant cardinal axis. */
    private void updateForwardDirection(ManagedCart cart, Vector velocity) {
        if (Math.abs(velocity.getX()) >= Math.abs(velocity.getZ())) {
            cart.setForwardDirection(velocity.getX() > 0 ? 1 : -1, 0);
        } else {
            cart.setForwardDirection(0, velocity.getZ() > 0 ? 1 : -1);
        }
    }

    /** @return true if the cart just started (or is already) dwelling at a stop this tick. */
    private boolean handleStopPoint(Minecart minecart, ManagedCart cart, World world, Location location, double stopRadius) {
        Optional<StopPoint> nearby = stopPointManager.findNearest(location, stopRadius);
        if (nearby.isEmpty()) {
            cart.setLastHandledStopKey(null);
            return false;
        }
        StopPoint stop = nearby.get();
        if (stop.key().equals(cart.getLastHandledStopKey())) {
            return false; // already handled this same stop - wait until the cart moves away before it can trigger again
        }
        minecart.setVelocity(new Vector(0, 0, 0));
        cart.setDwellUntilMillis(System.currentTimeMillis() + stop.dwellSeconds() * 1000L);
        cart.setLastHandledStopKey(stop.key());
        return true;
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
