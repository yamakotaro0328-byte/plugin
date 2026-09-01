package com.yamakotaro.ecorail.stop;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

/** Stop points are few and admin-managed, so a plain YAML file is enough. */
public class StopPointManager {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, StopPoint> stopsByKey = new LinkedHashMap<>();

    public StopPointManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stops.yml");
        load();
    }

    private void load() {
        stopsByKey.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            String path = key + ".";
            StopPoint stop = new StopPoint(
                    yaml.getString(path + "world"),
                    yaml.getInt(path + "x"),
                    yaml.getInt(path + "y"),
                    yaml.getInt(path + "z"),
                    yaml.getInt(path + "dwell-seconds"));
            stopsByKey.put(stop.key(), stop);
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (StopPoint stop : stopsByKey.values()) {
            String path = stop.key() + ".";
            yaml.set(path + "world", stop.world());
            yaml.set(path + "x", stop.x());
            yaml.set(path + "y", stop.y());
            yaml.set(path + "z", stop.z());
            yaml.set(path + "dwell-seconds", stop.dwellSeconds());
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save stops.yml", e);
        }
    }

    public void create(StopPoint stop) {
        stopsByKey.put(stop.key(), stop);
        save();
    }

    /** Removes whichever stop point is nearest to location, within radius blocks - there's no name to target one by. */
    public Optional<StopPoint> removeNearest(Location location, double radius) {
        Optional<StopPoint> nearest = findNearest(location, radius);
        nearest.ifPresent(stop -> {
            stopsByKey.remove(stop.key());
            save();
        });
        return nearest;
    }

    public Optional<StopPoint> findNearest(Location location, double radius) {
        StopPoint best = null;
        double bestDistanceSquared = radius * radius;
        for (StopPoint stop : stopsByKey.values()) {
            if (!stop.world().equals(location.getWorld().getName())) {
                continue;
            }
            double dx = location.getX() - stop.centerX();
            double dz = location.getZ() - stop.centerZ();
            double dy = location.getY() - stop.y();
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared <= bestDistanceSquared) {
                best = stop;
                bestDistanceSquared = distanceSquared;
            }
        }
        return Optional.ofNullable(best);
    }

    public Collection<StopPoint> all() {
        return stopsByKey.values();
    }
}
