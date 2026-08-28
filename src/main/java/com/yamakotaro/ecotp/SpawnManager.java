package com.yamakotaro.ecotp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

public class SpawnManager {

    private final EcoTpPlugin plugin;
    private final File file;
    private final YamlConfiguration data;

    public SpawnManager(EcoTpPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "spawn.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public boolean hasCustomSpawn() {
        return data.contains("spawn.world");
    }

    public Location getSpawn() {
        if (hasCustomSpawn()) {
            String worldName = data.getString("spawn.world");
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                double x = data.getDouble("spawn.x");
                double y = data.getDouble("spawn.y");
                double z = data.getDouble("spawn.z");
                float yaw = (float) data.getDouble("spawn.yaw");
                float pitch = (float) data.getDouble("spawn.pitch");
                return new Location(world, x, y, z, yaw, pitch);
            }
        }
        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    public void setSpawn(Location location) {
        data.set("spawn.world", location.getWorld().getName());
        data.set("spawn.x", location.getX());
        data.set("spawn.y", location.getY());
        data.set("spawn.z", location.getZ());
        data.set("spawn.yaw", (double) location.getYaw());
        data.set("spawn.pitch", (double) location.getPitch());
        save();
    }

    public void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save spawn.yml", e);
        }
    }
}
