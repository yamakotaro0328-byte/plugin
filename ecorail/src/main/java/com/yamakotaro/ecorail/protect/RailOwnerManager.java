package com.yamakotaro.ecorail.protect;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Rails aren't tile entities, so they can't carry their own PersistentDataContainer - this
 * remembers who placed each protected rail block instead, keyed by location, so that player
 * (and anyone with ecorail.admin) can still break it while everyone else can't.
 */
public class RailOwnerManager {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, UUID> ownerByKey = new HashMap<>();

    public RailOwnerManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "rail-owners.yml");
        load();
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            String ownerString = yaml.getString(key);
            if (ownerString == null) {
                continue;
            }
            try {
                ownerByKey.put(key, UUID.fromString(ownerString));
            } catch (IllegalArgumentException ignored) {
                // corrupt entry - drop it
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, UUID> entry : ownerByKey.entrySet()) {
            yaml.set(entry.getKey(), entry.getValue().toString());
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save rail-owners.yml", e);
        }
    }

    public void recordOwner(Block block, UUID owner) {
        ownerByKey.put(key(block.getLocation()), owner);
        save();
    }

    public Optional<UUID> getOwner(Block block) {
        return Optional.ofNullable(ownerByKey.get(key(block.getLocation())));
    }

    public void removeOwner(Block block) {
        if (ownerByKey.remove(key(block.getLocation())) != null) {
            save();
        }
    }

    private static String key(Location location) {
        return location.getWorld().getName() + ";" + location.getBlockX() + ";" + location.getBlockY() + ";" + location.getBlockZ();
    }
}
