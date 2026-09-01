package com.yamakotaro.ecorail.settings;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Per-player toggles, applied automatically to every cart that player boards from then on -
 * there's nothing to re-select each time, only /ecorail settings to change the defaults.
 */
public class PlayerSettingsManager {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, PlayerSettings> settingsByPlayer = new HashMap<>();

    public PlayerSettingsManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "player-settings.yml");
        load();
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            String path = key + ".";
            settingsByPlayer.put(uuid, new PlayerSettings(
                    yaml.getBoolean(path + "anti-reverse", defaultAntiReverse()),
                    yaml.getBoolean(path + "player-collision", defaultPlayerCollision())));
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerSettings> entry : settingsByPlayer.entrySet()) {
            String path = entry.getKey() + ".";
            yaml.set(path + "anti-reverse", entry.getValue().antiReverse());
            yaml.set(path + "player-collision", entry.getValue().playerCollision());
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save player-settings.yml", e);
        }
    }

    private boolean defaultAntiReverse() {
        return plugin.getConfig().getBoolean("settings.default-anti-reverse", true);
    }

    private boolean defaultPlayerCollision() {
        return plugin.getConfig().getBoolean("settings.default-player-collision", true);
    }

    public PlayerSettings get(UUID uuid) {
        return settingsByPlayer.getOrDefault(uuid, new PlayerSettings(defaultAntiReverse(), defaultPlayerCollision()));
    }

    public void setAntiReverse(UUID uuid, boolean value) {
        PlayerSettings current = get(uuid);
        settingsByPlayer.put(uuid, new PlayerSettings(value, current.playerCollision()));
        save();
    }

    public void setPlayerCollision(UUID uuid, boolean value) {
        PlayerSettings current = get(uuid);
        settingsByPlayer.put(uuid, new PlayerSettings(current.antiReverse(), value));
        save();
    }
}
