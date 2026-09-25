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
import java.util.logging.Level;

/**
 * 管理者が /setwarp で登録する、全員共通の名前付きワープ地点 (warps.yml)。
 * 名前の大文字小文字は区別せずに引ける (登録時の表記は一覧表示にそのまま使う)。
 */
public class WarpManager {

    private final EcoTpPlugin plugin;
    private final File file;
    private final YamlConfiguration data;

    public WarpManager(EcoTpPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "warps.yml");
        this.data = YamlIo.load(file);
    }

    public List<String> getWarpNames() {
        ConfigurationSection section = data.getConfigurationSection("warps");
        List<String> names = section == null ? new ArrayList<>() : new ArrayList<>(section.getKeys(false));
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /** @return 登録済みの表記での名前。存在しなければ null。 */
    public String resolveName(String name) {
        for (String existing : getWarpNames()) {
            if (existing.equalsIgnoreCase(name)) {
                return existing;
            }
        }
        return null;
    }

    /** @return ワープ先。名前が無い、またはワールドが消えている場合は null。 */
    public Location getWarp(String name) {
        String key = resolveName(name);
        if (key == null) {
            return null;
        }
        String path = "warps." + key;
        World world = Bukkit.getWorld(data.getString(path + ".world", ""));
        if (world == null) {
            return null;
        }
        return new Location(world,
                data.getDouble(path + ".x"), data.getDouble(path + ".y"), data.getDouble(path + ".z"),
                (float) data.getDouble(path + ".yaw"), (float) data.getDouble(path + ".pitch"));
    }

    public void setWarp(String name, Location location) {
        String existing = resolveName(name);
        if (existing != null) {
            data.set("warps." + existing, null);
        }
        String path = "warps." + name;
        data.set(path + ".world", location.getWorld().getName());
        data.set(path + ".x", location.getX());
        data.set(path + ".y", location.getY());
        data.set(path + ".z", location.getZ());
        data.set(path + ".yaw", (double) location.getYaw());
        data.set(path + ".pitch", (double) location.getPitch());
        save();
    }

    public boolean deleteWarp(String name) {
        String key = resolveName(name);
        if (key == null) {
            return false;
        }
        data.set("warps." + key, null);
        save();
        return true;
    }

    private void save() {
        try {
            YamlIo.save(data, file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save warps.yml", e);
        }
    }
}
