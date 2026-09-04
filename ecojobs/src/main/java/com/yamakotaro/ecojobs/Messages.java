package com.yamakotaro.ecojobs;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Map;

public class Messages {

    private final EcoJobsPlugin plugin;

    public Messages(EcoJobsPlugin plugin) {
        this.plugin = plugin;
    }

    private String language() {
        return plugin.config().getString("language", "en");
    }

    public String raw(String path, Map<String, String> replacements) {
        FileConfiguration config = plugin.config();
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

    /**
     * The human-readable display name for a job id (e.g. "miner" -> "Miner"/"採掘工"), from the
     * job-names.<language> section. Falls back to job-names.en, then the raw id.
     */
    public String jobName(String jobId) {
        FileConfiguration config = plugin.config();
        String value = config.getString("job-names." + language() + "." + jobId);
        if (value == null) {
            value = config.getString("job-names.en." + jobId, jobId);
        }
        return value;
    }
}
