package com.yamakotaro.ecorail.cart;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
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
            String ownerString = yaml.getString(path + "owner");
            ManagedCart cart = new ManagedCart(
                    entityId,
                    yaml.getString(path + "world"),
                    yaml.getInt(path + "block-x"),
                    yaml.getInt(path + "block-z"),
                    yaml.getInt(path + "forward-dir-x"),
                    yaml.getInt(path + "forward-dir-z"),
                    ownerString != null ? UUID.fromString(ownerString) : null);
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
            yaml.set(path + "block-x", cart.getLastBlockX());
            yaml.set(path + "block-z", cart.getLastBlockZ());
            yaml.set(path + "forward-dir-x", cart.getForwardDirX());
            yaml.set(path + "forward-dir-z", cart.getForwardDirZ());
            yaml.set(path + "owner", cart.getOwnerId() != null ? cart.getOwnerId().toString() : null);
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

    public boolean isManaged(UUID entityId) {
        return cartsById.containsKey(entityId);
    }

    public Optional<ManagedCart> find(UUID entityId) {
        return Optional.ofNullable(cartsById.get(entityId));
    }

    public Collection<ManagedCart> all() {
        return cartsById.values();
    }
}
