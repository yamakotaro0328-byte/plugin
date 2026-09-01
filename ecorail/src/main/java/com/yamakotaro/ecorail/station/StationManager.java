package com.yamakotaro.ecorail.station;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

/** Stations are small in number and admin-managed, so a plain YAML file is enough. */
public class StationManager {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, Station> stationsById = new LinkedHashMap<>();

    public StationManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stations.yml");
        load();
    }

    public void load() {
        stationsById.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String id : yaml.getKeys(false)) {
            String path = id + ".";
            Station station = new Station(
                    id,
                    yaml.getString(path + "name", id),
                    yaml.getString(path + "world"),
                    yaml.getInt(path + "x"),
                    yaml.getInt(path + "y"),
                    yaml.getInt(path + "z"),
                    yaml.getInt(path + "dir-x"),
                    yaml.getInt(path + "dir-z"));
            stationsById.put(id, station);
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Station station : stationsById.values()) {
            String path = station.id() + ".";
            yaml.set(path + "name", station.name());
            yaml.set(path + "world", station.world());
            yaml.set(path + "x", station.x());
            yaml.set(path + "y", station.y());
            yaml.set(path + "z", station.z());
            yaml.set(path + "dir-x", station.dirX());
            yaml.set(path + "dir-z", station.dirZ());
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save stations.yml", e);
        }
    }

    public boolean create(Station station) {
        String id = station.id();
        if (stationsById.containsKey(id)) {
            return false;
        }
        stationsById.put(id, station);
        save();
        return true;
    }

    public boolean remove(String name) {
        boolean removed = stationsById.remove(normalize(name)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public Optional<Station> find(String name) {
        return Optional.ofNullable(stationsById.get(normalize(name)));
    }

    public Collection<Station> all() {
        return stationsById.values();
    }

    public static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
