package com.yamakotaro.ecoban;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Map;

public class Messages {

    private final EcoBanPlugin plugin;

    public Messages(EcoBanPlugin plugin) {
        this.plugin = plugin;
    }

    private String language() {
        return plugin.getConfig().getString("language", "en");
    }

    public String raw(String path, Map<String, String> replacements) {
        FileConfiguration config = plugin.getConfig();
        String language = language();
        String value = config.getString("messages." + language + "." + path);
        if (value == null) {
            value = config.getString("messages.en." + path, path);
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
