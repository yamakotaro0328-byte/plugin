package com.yamakotaro.ecotpquickactions;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * menu.yml の中身。ブートストラップ(Gキー用データパック生成)と実行時(/quickmenu の動的ダイアログ)の
 * 両方から使うため、Bukkit のサーバーインスタンスには依存しない(YamlConfiguration は純粋なパーサーとして使う)。
 */
public record MenuDefinition(
        Map<String, String> title,
        int gKeyColumns,
        List<String> gKeyButtons,
        int hubColumns,
        List<String> hubCategories,
        Map<String, Category> categories,
        Map<String, Button> buttons,
        List<String> playerActions,
        List<Double> payPresets) {

    public enum ActionType { COMMAND, MENU, BUILTIN }

    public record Button(String id, Map<String, String> label, Map<String, String> tooltip,
                         ActionType type, String value, String permission, String requires) {
    }

    public record Category(String id, Map<String, String> label, int columns, List<String> buttons) {
    }

    public static String localized(Map<String, String> texts, String language, String fallback) {
        if (texts == null || texts.isEmpty()) {
            return fallback;
        }
        String value = texts.get(language);
        if (value == null) {
            value = texts.getOrDefault("en", texts.values().iterator().next());
        }
        return value;
    }

    /**
     * Data folder copy if present (falling back to the bundled default if it can't be parsed),
     * otherwise the default bundled in the jar. Errors go to a plain JUL logger rather than
     * YamlConfiguration.loadConfiguration's Bukkit.getLogger(), which doesn't exist yet during
     * bootstrap.
     */
    public static YamlConfiguration loadYaml(Path dataDirectory, String fileName) {
        Path file = dataDirectory.resolve(fileName);
        if (Files.exists(file)) {
            YamlConfiguration yaml = new YamlConfiguration();
            try {
                yaml.load(file.toFile());
                return yaml;
            } catch (IOException | InvalidConfigurationException e) {
                Logger.getLogger("EcoTP-QuickActions").warning(
                        "Couldn't read " + file + ", using the bundled default instead: " + e.getMessage());
            }
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStream in = MenuDefinition.class.getResourceAsStream("/" + fileName)) {
            if (in != null) {
                yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException | InvalidConfigurationException ignored) {
            // The bundled default is valid; an empty config is the only other possible outcome.
        }
        return yaml;
    }

    public static MenuDefinition parse(YamlConfiguration yaml) {
        Map<String, Button> buttons = new LinkedHashMap<>();
        ConfigurationSection buttonSection = yaml.getConfigurationSection("buttons");
        if (buttonSection != null) {
            for (String id : buttonSection.getKeys(false)) {
                ConfigurationSection s = buttonSection.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                ActionType type;
                String value;
                if (s.isString("command")) {
                    type = ActionType.COMMAND;
                    value = s.getString("command");
                } else if (s.isString("menu")) {
                    type = ActionType.MENU;
                    value = s.getString("menu");
                } else if (s.isString("builtin")) {
                    type = ActionType.BUILTIN;
                    value = s.getString("builtin");
                } else {
                    continue;
                }
                buttons.put(id, new Button(id, texts(s, "label", id), texts(s, "tooltip", null),
                        type, value, blankToNull(s.getString("permission")), blankToNull(s.getString("requires"))));
            }
        }

        Map<String, Category> categories = new LinkedHashMap<>();
        ConfigurationSection categorySection = yaml.getConfigurationSection("categories");
        if (categorySection != null) {
            for (String id : categorySection.getKeys(false)) {
                ConfigurationSection s = categorySection.getConfigurationSection(id);
                if (s != null) {
                    categories.put(id, new Category(id, texts(s, "label", id),
                            clampColumns(s.getInt("columns", 2)), s.getStringList("buttons")));
                }
            }
        }

        List<Double> presets = new ArrayList<>();
        for (Object o : yaml.getList("pay-presets", List.of())) {
            if (o instanceof Number n && n.doubleValue() > 0) {
                presets.add(n.doubleValue());
            }
        }

        return new MenuDefinition(
                texts(yaml, "title", "EcoTP"),
                clampColumns(yaml.getInt("g-key.columns", 2)),
                yaml.getStringList("g-key.buttons"),
                clampColumns(yaml.getInt("hub.columns", 2)),
                yaml.getStringList("hub.categories"),
                categories,
                buttons,
                yaml.getStringList("player-actions"),
                presets);
    }

    private static Map<String, String> texts(ConfigurationSection s, String path, String fallback) {
        Map<String, String> out = new LinkedHashMap<>();
        if (s.isConfigurationSection(path)) {
            ConfigurationSection t = s.getConfigurationSection(path);
            for (String lang : t.getKeys(false)) {
                out.put(lang, t.getString(lang, ""));
            }
        } else if (s.isString(path)) {
            out.put("en", s.getString(path));
        } else if (fallback != null) {
            out.put("en", fallback);
        }
        return out;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static int clampColumns(int columns) {
        return Math.max(1, Math.min(4, columns));
    }
}
