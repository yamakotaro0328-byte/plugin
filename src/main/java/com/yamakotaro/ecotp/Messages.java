package com.yamakotaro.ecotp;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * messages.yml からメッセージを読み込み、プレースホルダーを置換する。
 * サーバー管理者はコードを一切触らずに文言・通貨単位を変更できる。
 */
public class Messages {

    private final EcoTpPlugin plugin;
    private final File file;
    private YamlConfiguration data;

    public Messages(EcoTpPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        reload();
    }

    public void reload() {
        this.data = YamlConfiguration.loadConfiguration(file);
        // jar 内のデフォルト値をフォールバックとして重ねる (アップデートで新しいキーが増えても壊れない)
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
                data.setDefaults(defaults);
            }
        } catch (IOException ignored) {
            // デフォルトが読めなくても既存の messages.yml だけで動作を続ける
        }
    }

    /**
     * @param path         例: "home.success"
     * @param replacements "key1", value1, "key2", value2 ... のペア。value は toString() される。
     */
    public String get(String path, Object... replacements) {
        String template = data.getString(path, path);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            String key = "{" + replacements[i] + "}";
            String value = String.valueOf(replacements[i + 1]);
            template = template.replace(key, value);
        }
        return ChatUtil.color(template);
    }

    public String currencySingular() {
        return data.getString("currency.singular", "円");
    }

    public String currencyPlural() {
        return data.getString("currency.plural", "円");
    }

    public String formatMoney(double amount) {
        long rounded = Math.round(amount);
        String unit = rounded == 1 ? currencySingular() : currencyPlural();
        return rounded + unit;
    }
}
