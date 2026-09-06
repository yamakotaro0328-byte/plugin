package com.yamakotaro.sulfursoccer.gimmick;

import com.yamakotaro.sulfursoccer.arena.ArenaManager;
import com.yamakotaro.sulfursoccer.arena.Box;
import com.yamakotaro.sulfursoccer.arena.Point;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/** Persists each arena's gimmicks (see Gimmick) to gimmicks.yml - same load-everything/rewrite-on-save
 * approach as ArenaManager, just keyed by arena id then gimmick id instead of a single record. */
public class GimmickManager {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, List<Gimmick>> gimmicksByArena = new LinkedHashMap<>();

    public GimmickManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "gimmicks.yml");
        load();
    }

    private void load() {
        gimmicksByArena.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String arenaId : yaml.getKeys(false)) {
            List<Gimmick> gimmicks = new ArrayList<>();
            for (String gimmickId : yaml.getConfigurationSection(arenaId).getKeys(false)) {
                String path = arenaId + "." + gimmickId + ".";
                GimmickType type = GimmickType.valueOf(yaml.getString(path + "type"));
                Box region = new Box(
                        new Point(yaml.getInt(path + "corner1.x"), yaml.getInt(path + "corner1.y"), yaml.getInt(path + "corner1.z")),
                        new Point(yaml.getInt(path + "corner2.x"), yaml.getInt(path + "corner2.y"), yaml.getInt(path + "corner2.z")));
                gimmicks.add(new Gimmick(gimmickId, type, region));
            }
            gimmicksByArena.put(arenaId, gimmicks);
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, List<Gimmick>> entry : gimmicksByArena.entrySet()) {
            for (Gimmick gimmick : entry.getValue()) {
                String path = entry.getKey() + "." + gimmick.id() + ".";
                yaml.set(path + "type", gimmick.type().name());
                yaml.set(path + "corner1.x", gimmick.region().corner1().x());
                yaml.set(path + "corner1.y", gimmick.region().corner1().y());
                yaml.set(path + "corner1.z", gimmick.region().corner1().z());
                yaml.set(path + "corner2.x", gimmick.region().corner2().x());
                yaml.set(path + "corner2.y", gimmick.region().corner2().y());
                yaml.set(path + "corner2.z", gimmick.region().corner2().z());
            }
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save gimmicks.yml", e);
        }
    }

    public List<Gimmick> forArena(String arenaId) {
        return gimmicksByArena.getOrDefault(normalize(arenaId), List.of());
    }

    /** @return false if a gimmick with this id already exists in this arena. */
    public boolean add(String arenaId, Gimmick gimmick) {
        List<Gimmick> gimmicks = gimmicksByArena.computeIfAbsent(normalize(arenaId), k -> new ArrayList<>());
        if (gimmicks.stream().anyMatch(g -> g.id().equalsIgnoreCase(gimmick.id()))) {
            return false;
        }
        gimmicks.add(gimmick);
        save();
        return true;
    }

    public boolean remove(String arenaId, String gimmickId) {
        List<Gimmick> gimmicks = gimmicksByArena.get(normalize(arenaId));
        boolean removed = gimmicks != null && gimmicks.removeIf(g -> g.id().equalsIgnoreCase(gimmickId));
        if (removed) {
            save();
        }
        return removed;
    }

    private static String normalize(String arenaId) {
        return ArenaManager.normalize(arenaId);
    }
}
