package com.yamakotaro.ecoboss;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public class Messages {

    private final JavaPlugin plugin;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private String language() {
        return plugin.getConfig().getString("language", "en");
    }

    public String raw(String path, Map<String, String> replacements) {
        String language = language();
        String value = plugin.getConfig().getString("messages." + language + "." + path);
        if (value == null) {
            value = plugin.getConfig().getString("messages.en." + path, path);
        }
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            value = value.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return value;
    }

    public Component get(String path, Map<String, String> replacements) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(raw(path, replacements));
    }
}
