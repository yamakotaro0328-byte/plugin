package com.yamakotaro.ecotp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;

/**
 * プレイヤーごとの単一ホームと、/sethome を使った回数 (料金の上昇に使う) を管理する。
 */
public class HomeManager {

    private final EcoTpPlugin plugin;
    private final File file;
    private final YamlConfiguration data;

    public HomeManager(EcoTpPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "homes.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public boolean hasHome(UUID uuid) {
        return data.contains("homes." + uuid + ".world");
    }

    public Location getHome(UUID uuid) {
        String path = "homes." + uuid;
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

    public void setHome(UUID uuid, Location location) {
        String path = "homes." + uuid;
        data.set(path + ".world", location.getWorld().getName());
        data.set(path + ".x", location.getX());
        data.set(path + ".y", location.getY());
        data.set(path + ".z", location.getZ());
        data.set(path + ".yaw", (double) location.getYaw());
        data.set(path + ".pitch", (double) location.getPitch());
        data.set(path + ".sethome-count", getSetHomeCount(uuid) + 1);
        save();
    }

    public int getSetHomeCount(UUID uuid) {
        return data.getInt("homes." + uuid + ".sethome-count", 0);
    }

    /**
     * 次に /sethome を実行したときの料金。
     * 1回目: base, 2回目: base + increment, 3回目: base + increment*2 ...
     */
    public double getNextSetHomeCost(UUID uuid) {
        double base = plugin.getConfig().getDouble("costs.sethome-base", 1000.0);
        double increment = plugin.getConfig().getDouble("costs.sethome-increment", 1000.0);
        int count = getSetHomeCount(uuid);
        return base + increment * count;
    }

    public void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "homes.yml の保存に失敗しました", e);
        }
    }
}
