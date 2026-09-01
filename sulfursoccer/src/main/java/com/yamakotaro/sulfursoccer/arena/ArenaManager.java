package com.yamakotaro.sulfursoccer.arena;

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

public class ArenaManager {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, Arena> arenasById = new LinkedHashMap<>();

    public ArenaManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arenas.yml");
        load();
    }

    private void load() {
        arenasById.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String id : yaml.getKeys(false)) {
            String path = id + ".";
            String world = yaml.getString(path + "world");
            Arena arena = new Arena(id, world,
                    readBox(yaml, path + "goal-a"), readBox(yaml, path + "goal-b"),
                    readPoint(yaml, path + "kickoff"), readPoint(yaml, path + "spawn-a"), readPoint(yaml, path + "spawn-b"));
            arenasById.put(id, arena);
        }
    }

    private Point readPoint(YamlConfiguration yaml, String path) {
        if (!yaml.isConfigurationSection(path)) {
            return null;
        }
        return new Point(yaml.getInt(path + ".x"), yaml.getInt(path + ".y"), yaml.getInt(path + ".z"));
    }

    private Box readBox(YamlConfiguration yaml, String path) {
        Point corner1 = readPoint(yaml, path + ".corner1");
        Point corner2 = readPoint(yaml, path + ".corner2");
        return corner1 != null && corner2 != null ? new Box(corner1, corner2) : null;
    }

    private void writePoint(YamlConfiguration yaml, String path, Point point) {
        if (point == null) {
            return;
        }
        yaml.set(path + ".x", point.x());
        yaml.set(path + ".y", point.y());
        yaml.set(path + ".z", point.z());
    }

    private void writeBox(YamlConfiguration yaml, String path, Box box) {
        if (box == null) {
            return;
        }
        writePoint(yaml, path + ".corner1", box.corner1());
        writePoint(yaml, path + ".corner2", box.corner2());
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Arena arena : arenasById.values()) {
            String path = arena.id() + ".";
            yaml.set(path + "world", arena.world());
            writeBox(yaml, path + "goal-a", arena.goalA());
            writeBox(yaml, path + "goal-b", arena.goalB());
            writePoint(yaml, path + "kickoff", arena.kickoff());
            writePoint(yaml, path + "spawn-a", arena.spawnA());
            writePoint(yaml, path + "spawn-b", arena.spawnB());
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save arenas.yml", e);
        }
    }

    public boolean create(String name, String world) {
        String id = normalize(name);
        if (arenasById.containsKey(id)) {
            return false;
        }
        arenasById.put(id, new Arena(id, world, null, null, null, null, null));
        save();
        return true;
    }

    public boolean remove(String name) {
        boolean removed = arenasById.remove(normalize(name)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public void update(Arena arena) {
        arenasById.put(arena.id(), arena);
        save();
    }

    public Optional<Arena> find(String name) {
        return Optional.ofNullable(arenasById.get(normalize(name)));
    }

    public Collection<Arena> all() {
        return arenasById.values();
    }

    public static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
