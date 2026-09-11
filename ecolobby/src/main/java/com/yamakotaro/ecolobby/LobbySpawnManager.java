package com.yamakotaro.ecolobby;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Persists the single lobby spawn point (spawn.yml) - set once via /ecolobby setspawn, used by
 * join, /hub, and the void-teleport safety net. Purely in-memory data numbers/world name only, so
 * no special UTF-8 handling is needed the way human-authored text elsewhere in this plugin family
 * sometimes does.
 */
public class LobbySpawnManager {

    private final JavaPlugin plugin;
    private final File file;
    private Location spawn;

    public LobbySpawnManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "spawn.yml");
        load();
    }

    public Optional<Location> getSpawn() {
        return Optional.ofNullable(spawn);
    }

    public void setSpawn(Location location) {
        this.spawn = location.clone();
        save();
    }

    private void load() {
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        String worldName = data.getString("world");
        if (worldName == null) {
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Lobby spawn world '" + worldName + "' isn't loaded yet - "
                    + "the lobby spawn will be unavailable until it is.");
            return;
        }
        spawn = new Location(world, data.getDouble("x"), data.getDouble("y"), data.getDouble("z"),
                (float) data.getDouble("yaw"), (float) data.getDouble("pitch"));
    }

    private void save() {
        YamlConfiguration data = new YamlConfiguration();
        data.set("world", spawn.getWorld().getName());
        data.set("x", spawn.getX());
        data.set("y", spawn.getY());
        data.set("z", spawn.getZ());
        data.set("yaw", (double) spawn.getYaw());
        data.set("pitch", (double) spawn.getPitch());
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save spawn.yml", e);
        }
    }
}
