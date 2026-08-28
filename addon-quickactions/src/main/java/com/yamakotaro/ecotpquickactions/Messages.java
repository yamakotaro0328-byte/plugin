package com.yamakotaro.ecotpquickactions;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Map;

/**
 * config.yml の messages.<language> と weather-names.<language> から読む小さなヘルパー。
 * EcoTP本体のMessages.javaのような別ファイル(messages.yml)への切り出しはせず、
 * このアドオンの規模に合わせてconfig.yml内に収めている。
 */
public class Messages {

    private final EcoTpQuickActionsPlugin plugin;

    public Messages(EcoTpQuickActionsPlugin plugin) {
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

    public String weatherName(String weatherKey) {
        FileConfiguration config = plugin.getConfig();
        String language = language();
        return config.getString("weather-names." + language + "." + weatherKey,
                config.getString("weather-names.en." + weatherKey, weatherKey));
    }
}
