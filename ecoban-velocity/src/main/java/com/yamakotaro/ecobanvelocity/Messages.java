package com.yamakotaro.ecobanvelocity;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Map;

public class Messages {

    private final EcoBanVelocityConfig config;

    public Messages(EcoBanVelocityConfig config) {
        this.config = config;
    }

    private String language() {
        return config.getString("language", "en");
    }

    public String raw(String path, Map<String, String> replacements) {
        String language = language();
        String value = config.getString("messages." + language + "." + path, null);
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
