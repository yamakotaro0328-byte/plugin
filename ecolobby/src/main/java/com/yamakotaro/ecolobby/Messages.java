package com.yamakotaro.ecolobby;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
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

    public Component color(String legacyText) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(legacyText);
    }

    /** Reads a messages.&lt;lang&gt;.&lt;path&gt; string list (e.g. rules.lines), falling back to "en". */
    public List<String> rawList(String path) {
        String language = language();
        List<String> value = plugin.getConfig().getStringList("messages." + language + "." + path);
        if (value.isEmpty()) {
            value = plugin.getConfig().getStringList("messages.en." + path);
        }
        return value;
    }
}
