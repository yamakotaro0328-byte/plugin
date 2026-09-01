package com.yamakotaro.ecorail.cart;

import com.yamakotaro.ecorail.Messages;
import com.yamakotaro.ecorail.settings.PlayerSettings;
import com.yamakotaro.ecorail.settings.PlayerSettingsManager;
import com.yamakotaro.ecorail.station.Station;
import com.yamakotaro.ecorail.station.StationManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Runs every physics.tick-interval-ticks: force-loads a small square of chunks around each
 * managed cart's last known position (so vanilla rail physics keeps simulating it with nobody
 * nearby), checks whether it has reached its destination station, and gives up on carts that
 * go missing or sit idle too long (see ManagedCart's class doc for why).
 */
public class ChunkForceLoadTask extends BukkitRunnable {

    private static final double MOVING_SPEED_SQUARED_THRESHOLD = 0.0009; // ~0.03 blocks/tick
    private static final int MAX_CONSECUTIVE_MISSES = 20;
    private static final int SAVE_EVERY_N_RUNS = 25; // ~5s at the default 4-tick interval

    private final JavaPlugin plugin;
    private final CartManager cartManager;
    private final StationManager stationManager;
    private final PlayerSettingsManager settingsManager;
    private final Messages messages;
    private int runCount;

    public ChunkForceLoadTask(JavaPlugin plugin, CartManager cartManager, StationManager stationManager,
                               PlayerSettingsManager settingsManager, Messages messages) {
        this.plugin = plugin;
        this.cartManager = cartManager;
        this.stationManager = stationManager;
        this.settingsManager = settingsManager;
        this.messages = messages;
    }

    @Override
    public void run() {
        int radius = plugin.getConfig().getInt("physics.chunk-load-radius", 2);
        double stopRadius = plugin.getConfig().getDouble("physics.stop-radius", 1.5);
        long idleReleaseMillis = plugin.getConfig().getLong("physics.idle-release-seconds", 300) * 1000L;

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
            Vector velocity = minecart.getVelocity();
            if (velocity.lengthSquared() > MOVING_SPEED_SQUARED_THRESHOLD) {
                cart.markMoved();
            }
            correctReverseRunning(minecart, cart, velocity);

            if (hasArrived(cart, world, location, stopRadius)) {
                handleArrival(minecart, cart);
                releaseAllChunks(world, cart);
                it.remove();
                continue;
            }

            if (System.currentTimeMillis() - cart.getLastMovedAt() > idleReleaseMillis) {
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

    /**
     * A shove from another cart, a player, or momentum at a dead end can send a cart rolling
     * backward relative to how it departed. If the owner wants anti-reverse protection, flip it
     * back to its original launch direction, keeping the same speed.
     */
    private void correctReverseRunning(Minecart minecart, ManagedCart cart, Vector velocity) {
        if (cart.getOwnerId() == null || (cart.getForwardDirX() == 0 && cart.getForwardDirZ() == 0)) {
            return;
        }
        PlayerSettings settings = settingsManager.get(cart.getOwnerId());
        if (!settings.antiReverse()) {
            return;
        }
        double forwardComponent = velocity.getX() * cart.getForwardDirX() + velocity.getZ() * cart.getForwardDirZ();
        if (forwardComponent >= 0) {
            return;
        }
        double horizontalSpeed = Math.sqrt(velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ());
        minecart.setVelocity(new Vector(cart.getForwardDirX() * horizontalSpeed, velocity.getY(), cart.getForwardDirZ() * horizontalSpeed));
    }

    private boolean hasArrived(ManagedCart cart, World world, Location location, double stopRadius) {
        if (cart.getDestinationStationId() == null) {
            return false;
        }
        Optional<Station> destination = stationManager.find(cart.getDestinationStationId());
        if (destination.isEmpty() || !destination.get().world().equals(world.getName())) {
            return false;
        }
        Station station = destination.get();
        double dx = location.getX() - station.centerX();
        double dz = location.getZ() - station.centerZ();
        return dx * dx + dz * dz <= stopRadius * stopRadius;
    }

    private void handleArrival(Minecart minecart, ManagedCart cart) {
        minecart.setVelocity(new Vector(0, 0, 0));
        Station station = stationManager.find(cart.getDestinationStationId()).orElseThrow();
        for (Entity passenger : List.copyOf(minecart.getPassengers())) {
            minecart.removePassenger(passenger);
            if (passenger instanceof Player player) {
                player.sendMessage(messages.get("arrival.arrived", Map.of("name", station.name())));
            }
        }
    }

    /**
     * Only releases this cart's chunk tickets - removing it from tracking is the caller's job
     * (via the iterator's own remove()), since cartManager.unregister() would mutate the same
     * backing map the caller is iterating and trip a ConcurrentModificationException.
     */
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
