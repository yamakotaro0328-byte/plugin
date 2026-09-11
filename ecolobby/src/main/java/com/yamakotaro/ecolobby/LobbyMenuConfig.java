package com.yamakotaro.ecolobby;

import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Reads the servers:/links: lists from config.yml into plain records - re-read fresh each time a
 * menu is opened so /ecolobby reload picks up edits without a restart.
 */
public class LobbyMenuConfig {

    public record ServerEntry(String name, String displayName, Material material, List<String> lore) {
    }

    public record LinkEntry(String label, String url) {
    }

    private final JavaPlugin plugin;

    public LobbyMenuConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public List<ServerEntry> servers() {
        List<ServerEntry> entries = new ArrayList<>();
        for (Map<?, ?> map : plugin.getConfig().getMapList("servers")) {
            String name = stringOf(map.get("name"));
            if (name == null) {
                continue;
            }
            String displayName = stringOf(map.get("display-name"));
            Material material = materialOf(map.get("material"));
            List<String> lore = new ArrayList<>();
            if (map.get("lore") instanceof List<?> loreList) {
                for (Object line : loreList) {
                    lore.add(String.valueOf(line));
                }
            }
            entries.add(new ServerEntry(name, displayName != null ? displayName : name, material, lore));
        }
        return entries;
    }

    public List<LinkEntry> links() {
        List<LinkEntry> entries = new ArrayList<>();
        for (Map<?, ?> map : plugin.getConfig().getMapList("links")) {
            String label = stringOf(map.get("label"));
            String url = stringOf(map.get("url"));
            if (label == null || url == null) {
                continue;
            }
            entries.add(new LinkEntry(label, url));
        }
        return entries;
    }

    private String stringOf(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private Material materialOf(Object value) {
        if (value == null) {
            return Material.STONE;
        }
        Material material = Material.matchMaterial(String.valueOf(value));
        if (material == null) {
            plugin.getLogger().log(Level.WARNING, "Unknown material '" + value + "' in config.yml servers list - using STONE.");
            return Material.STONE;
        }
        return material;
    }
}
