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
import java.util.regex.Pattern;

/**
 * プレイヤーごとの複数ホーム (名前付き) と、/sethome を使った回数
 * (料金の上昇に使う。ホーム名に関わらずプレイヤー全体で共通) を管理する。
 */
public class HomeManager {

    /** ホーム名は数字・日本語 (ひらがな/カタカナ/漢字/長音符)・英字のみ、最大16文字。 */
    public static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z0-9\\u3040-\\u30FF\\u4E00-\\u9FFF]{1,16}$");
    public static final String DEFAULT_NAME = "home";

    private final EcoTpPlugin plugin;
    private final File file;
    private final YamlConfiguration data;

    public HomeManager(EcoTpPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "homes.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    private String homesPath(UUID uuid) {
        return uuid + ".homes";
    }

    public boolean hasHome(UUID uuid, String name) {
        return data.contains(homesPath(uuid) + "." + name + ".world");
    }

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

    /**
     * @return 上限に達していて新規のホームを作成できない場合 false。
     * 既存の名前を上書きする場合は上限に関わらず常に true。
     */
    public boolean canSetHome(UUID uuid, String name) {
        if (hasHome(uuid, name)) {
            return true;
        }
        int max = plugin.getConfig().getInt("homes.max-per-player", 3);
        return getHomeNames(uuid).size() < max;
    }

    public void setHome(UUID uuid, String name, Location location) {
        String path = homesPath(uuid) + "." + name;
        data.set(path + ".world", location.getWorld().getName());
        data.set(path + ".x", location.getX());
        data.set(path + ".y", location.getY());
        data.set(path + ".z", location.getZ());
        data.set(path + ".yaw", (double) location.getYaw());
        data.set(path + ".pitch", (double) location.getPitch());
        data.set(uuid + ".sethome-count", getSetHomeCount(uuid) + 1);
        save();
    }

    public List<String> getHomeNames(UUID uuid) {
        ConfigurationSection section = data.getConfigurationSection(homesPath(uuid));
        if (section == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(section.getKeys(false));
    }

    public int getSetHomeCount(UUID uuid) {
        return data.getInt(uuid + ".sethome-count", 0);
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
