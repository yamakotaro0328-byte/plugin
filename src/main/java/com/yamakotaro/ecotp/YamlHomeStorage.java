package com.yamakotaro.ecotp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * homes.yml にホームを保存するデフォルトのストレージ。追加設定は不要。
 */
public class YamlHomeStorage implements HomeStorage {

    private final EcoTpPlugin plugin;
    private final File file;
    private final YamlConfiguration data;

    public YamlHomeStorage(EcoTpPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "homes.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    private String homesPath(UUID uuid) {
        return uuid + ".homes";
    }

    @Override
    public boolean hasHome(UUID uuid, String name) {
        return data.contains(homesPath(uuid) + "." + name + ".world");
    }

    @Override
    public Location getHome(UUID uuid, String name) {
        String path = homesPath(uuid) + "." + name;
        String worldName = data.getString(path + ".world");
        if (worldName == null) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        double x = data.getDouble(path + ".x");
        double y = data.getDouble(path + ".y");
        double z = data.getDouble(path + ".z");
        float yaw = (float) data.getDouble(path + ".yaw");
        float pitch = (float) data.getDouble(path + ".pitch");
        return new Location(world, x, y, z, yaw, pitch);
    }

    @Override
    public void setHome(UUID uuid, String name, Location location) {
        String path = homesPath(uuid) + "." + name;
        data.set(path + ".world", location.getWorld().getName());
        data.set(path + ".x", location.getX());
        data.set(path + ".y", location.getY());
        data.set(path + ".z", location.getZ());
        data.set(path + ".yaw", (double) location.getYaw());
        data.set(path + ".pitch", (double) location.getPitch());
        save();
    }

    @Override
    public boolean deleteHome(UUID uuid, String name) {
        if (!hasHome(uuid, name)) {
            return false;
        }
        data.set(homesPath(uuid) + "." + name, null);
        save();
        return true;
    }

    @Override
    public List<String> getHomeNames(UUID uuid) {
        ConfigurationSection section = data.getConfigurationSection(homesPath(uuid));
        if (section == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(section.getKeys(false));
    }

    @Override
    public int getSetHomeCount(UUID uuid) {
        return data.getInt(uuid + ".sethome-count", 0);
    }

    @Override
    public void incrementSetHomeCount(UUID uuid) {
        data.set(uuid + ".sethome-count", getSetHomeCount(uuid) + 1);
        save();
    }

    @Override
    public void saveIfDirty() {
        save();
    }

    @Override
    public void close() {
        save();
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save homes.yml", e);
        }
    }
}
