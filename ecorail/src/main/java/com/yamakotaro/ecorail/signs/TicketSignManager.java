package com.yamakotaro.ecorail.signs;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

public class TicketSignManager {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, TicketSign> signsByKey = new LinkedHashMap<>();

    public TicketSignManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "signs.yml");
        load();
    }

    public void load() {
        signsByKey.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            String path = key + ".";
            TicketSign sign = new TicketSign(
                    yaml.getString(path + "world"),
                    yaml.getInt(path + "x"),
                    yaml.getInt(path + "y"),
                    yaml.getInt(path + "z"),
                    yaml.getString(path + "from"),
                    yaml.getString(path + "to"),
                    yaml.getDouble(path + "price"));
            signsByKey.put(sign.key(), sign);
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (TicketSign sign : signsByKey.values()) {
            String path = sign.key() + ".";
            yaml.set(path + "world", sign.world());
            yaml.set(path + "x", sign.x());
            yaml.set(path + "y", sign.y());
            yaml.set(path + "z", sign.z());
            yaml.set(path + "from", sign.fromStationId());
            yaml.set(path + "to", sign.toStationId());
            yaml.set(path + "price", sign.price());
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save signs.yml", e);
        }
    }

    public void register(TicketSign sign) {
        signsByKey.put(sign.key(), sign);
        save();
    }

    public void unregister(String world, int x, int y, int z) {
        if (signsByKey.remove(TicketSign.key(world, x, y, z)) != null) {
            save();
        }
    }

    public Optional<TicketSign> find(String world, int x, int y, int z) {
        return Optional.ofNullable(signsByKey.get(TicketSign.key(world, x, y, z)));
    }
}
