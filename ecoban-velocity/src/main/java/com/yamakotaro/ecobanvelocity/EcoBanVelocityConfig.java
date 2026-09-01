package com.yamakotaro.ecobanvelocity;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Velocity has no built-in equivalent of Bukkit's FileConfiguration, so this loads config.yml
 * with SnakeYAML directly, copying the bundled default out of the jar on first run.
 */
public class EcoBanVelocityConfig {

    private final Path configFile;
    private final Logger logger;
    private Map<String, Object> data;

    public EcoBanVelocityConfig(Path dataDirectory, Logger logger) {
        this.configFile = dataDirectory.resolve("config.yml");
        this.logger = logger;
        load();
    }

    public void load() {
        try {
            Files.createDirectories(configFile.getParent());
            if (Files.notExists(configFile)) {
                try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
                    Files.copy(in, configFile);
                }
            }
            try (InputStream in = Files.newInputStream(configFile)) {
                this.data = new Yaml().load(in);
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to load config.yml", e);
            this.data = Map.of();
        }
    }

    public String getString(String path, String defaultValue) {
        Object value = get(path);
        return value != null ? value.toString() : defaultValue;
    }

    public int getInt(String path, int defaultValue) {
        Object value = get(path);
        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    public boolean getBoolean(String path, boolean defaultValue) {
        Object value = get(path);
        return value instanceof Boolean bool ? bool : defaultValue;
    }

    @SuppressWarnings("unchecked")
    private Object get(String path) {
        Object current = data;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<String, Object>) current).get(segment);
        }
        return current;
    }
}
