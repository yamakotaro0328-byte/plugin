package com.yamakotaro.ecoboss.location;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Persists where each boss can spawn: a list of spawn points for WORLD/EVENT bosses, or a single
 * trigger region for DUNGEON bosses. Purely location data - boss combat stats live in config.yml.
 */
public class BossLocationManager {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, List<Point>> spawnPointsByBossId = new HashMap<>();
    private final Map<String, Box> regionByBossId = new HashMap<>();

    public BossLocationManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "boss-locations.yml");
        load();
    }

    private void load() {
        spawnPointsByBossId.clear();
        regionByBossId.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        if (yaml.isConfigurationSection("spawn-points")) {
            for (String bossId : yaml.getConfigurationSection("spawn-points").getKeys(false)) {
                List<Point> points = new ArrayList<>();
                List<Map<?, ?>> entries = yaml.getMapList("spawn-points." + bossId);
                for (Map<?, ?> entry : entries) {
                    points.add(new Point(String.valueOf(entry.get("world")),
                            toInt(entry.get("x")), toInt(entry.get("y")), toInt(entry.get("z"))));
                }
                spawnPointsByBossId.put(bossId, points);
            }
        }

        if (yaml.isConfigurationSection("regions")) {
            for (String bossId : yaml.getConfigurationSection("regions").getKeys(false)) {
                String path = "regions." + bossId + ".";
                String world = yaml.getString(path + "world");
                Point corner1 = new Point(world, yaml.getInt(path + "corner1.x"), yaml.getInt(path + "corner1.y"), yaml.getInt(path + "corner1.z"));
                Point corner2 = new Point(world, yaml.getInt(path + "corner2.x"), yaml.getInt(path + "corner2.y"), yaml.getInt(path + "corner2.z"));
                regionByBossId.put(bossId, new Box(world, corner1, corner2));
            }
        }
    }

    private static int toInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, List<Point>> entry : spawnPointsByBossId.entrySet()) {
            List<Map<String, Object>> serialized = new ArrayList<>();
            for (Point point : entry.getValue()) {
                serialized.add(Map.of("world", point.world(), "x", point.x(), "y", point.y(), "z", point.z()));
            }
            yaml.set("spawn-points." + entry.getKey(), serialized);
        }
        for (Map.Entry<String, Box> entry : regionByBossId.entrySet()) {
            String path = "regions." + entry.getKey() + ".";
            Box box = entry.getValue();
            yaml.set(path + "world", box.world());
            yaml.set(path + "corner1.x", box.corner1().x());
            yaml.set(path + "corner1.y", box.corner1().y());
            yaml.set(path + "corner1.z", box.corner1().z());
            yaml.set(path + "corner2.x", box.corner2().x());
            yaml.set(path + "corner2.y", box.corner2().y());
            yaml.set(path + "corner2.z", box.corner2().z());
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save boss-locations.yml", e);
        }
    }

    public List<Point> getSpawnPoints(String bossId) {
        return spawnPointsByBossId.getOrDefault(bossId, List.of());
    }

    public int addSpawnPoint(String bossId, Point point) {
        List<Point> points = spawnPointsByBossId.computeIfAbsent(bossId, id -> new ArrayList<>());
        points.add(point);
        save();
        return points.size();
    }

    public void clearSpawnPoints(String bossId) {
        spawnPointsByBossId.remove(bossId);
        save();
    }

    public Box getRegion(String bossId) {
        return regionByBossId.get(bossId);
    }

    public void setRegion(String bossId, Box box) {
        regionByBossId.put(bossId, box);
        save();
    }
}
