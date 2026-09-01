package com.yamakotaro.ecorail.cart;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Only the fields ChunkForceLoadTask needs to resume tracking after a restart are persisted -
 * held chunk tickets are never saved, since Minecraft itself doesn't keep plugin chunk tickets
 * across a restart either; they're simply re-requested on the first tick after reload.
 */
public class CartManager {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, ManagedCart> cartsById = new LinkedHashMap<>();

    public CartManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "carts.yml");
        load();
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            UUID entityId;
            try {
                entityId = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            String path = key + ".";
            ManagedCart cart = new ManagedCart(
                    entityId,
                    yaml.getString(path + "world"),
                    yaml.getInt(path + "chunk-x"),
                    yaml.getInt(path + "chunk-z"),
                    yaml.getInt(path + "forward-dir-x"),
                    yaml.getInt(path + "forward-dir-z"));
            cart.setDwellUntilMillis(yaml.getLong(path + "dwell-until", 0));
            cart.setLastHandledStopKey(yaml.getString(path + "last-stop"));
            cartsById.put(entityId, cart);
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (ManagedCart cart : cartsById.values()) {
            String path = cart.getEntityId() + ".";
            yaml.set(path + "world", cart.getWorld());
            yaml.set(path + "chunk-x", cart.getLastChunkX());
            yaml.set(path + "chunk-z", cart.getLastChunkZ());
            yaml.set(path + "forward-dir-x", cart.getForwardDirX());
            yaml.set(path + "forward-dir-z", cart.getForwardDirZ());
            yaml.set(path + "dwell-until", cart.getDwellUntilMillis());
            yaml.set(path + "last-stop", cart.getLastHandledStopKey());
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save carts.yml", e);
        }
    }

    public void register(ManagedCart cart) {
        cartsById.put(cart.getEntityId(), cart);
        save();
    }

    /**
     * Removes a cart from tracking and releases its held chunk tickets immediately. Only
     * ChunkForceLoadTask itself is allowed to remove a cart without also releasing its tickets
     * (it does so directly via its own iterator, right after releasing them) - everywhere else,
     * such as /ecorail cart unmark, must go through this or the chunks it was holding stay
     * force-loaded forever.
     */
    public void unregisterAndRelease(UUID entityId, Plugin plugin) {
        ManagedCart cart = cartsById.remove(entityId);
        if (cart == null) {
            return;
        }
        World world = Bukkit.getWorld(cart.getWorld());
        if (world != null) {
            for (long key : cart.getHeldChunks()) {
                world.removePluginChunkTicket((int) (key >> 32), (int) key, plugin);
            }
        }
        save();
    }

    public boolean isManaged(UUID entityId) {
        return cartsById.containsKey(entityId);
    }

    public Collection<ManagedCart> all() {
        return cartsById.values();
    }
}
