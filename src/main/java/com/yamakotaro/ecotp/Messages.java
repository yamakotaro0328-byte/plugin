package com.yamakotaro.ecotp;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Level;

/**
 * messages.yml からメッセージを読み込み、プレースホルダーを置換する。
 * サーバー管理者はコードを一切触らずに文言・通貨単位を変更できる。
 * 初回はプラグインの言語設定 (config.yml の language: en/ja) に応じて、
 * 同梱の messages_en.yml か messages_ja.yml を messages.yml としてコピーする。
 * 一度 messages.yml が生成された後は、その内容 (と後から追加されたキーのみ
 * デフォルト言語のフォールバック) が使われる。
 */
public class Messages {

    private final EcoTpPlugin plugin;
    private final File file;
    private YamlConfiguration data;

    public Messages(EcoTpPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            copyDefaultResource();
        }
        reload();
    }

    private String bundledResourceName() {
        String language = plugin.getConfig().getString("language", "en");
        return "ja".equalsIgnoreCase(language) ? "messages_ja.yml" : "messages_en.yml";
    }

    private void copyDefaultResource() {
        String resourceName = bundledResourceName();
        try (InputStream in = plugin.getResource(resourceName)) {
            if (in == null) {
                plugin.getLogger().warning("Bundled " + resourceName + " was not found.");
                return;
            }
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            Files.copy(in, file.toPath());
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create messages.yml", e);
        }
    }

    public void reload() {
        this.data = YamlConfiguration.loadConfiguration(file);
        // jar 内のデフォルト値をフォールバックとして重ねる (アップデートで新しいキーが増えても壊れない)
        try (InputStream in = plugin.getResource(bundledResourceName())) {
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
        return data.getString("currency.singular", "coin");
    }

    public String currencyPlural() {
        return data.getString("currency.plural", "coins");
    }

    public String formatMoney(double amount) {
        long rounded = Math.round(amount);
        String unit = rounded == 1 ? currencySingular() : currencyPlural();
        // 数字と単位の間にスペースを入れるかどうかは言語によって異なるため、
        // テンプレート自体を messages.yml 側 (currency.format) で決められるようにしてある。
        String format = data.getString("currency.format", "{amount} {unit}");
        return format.replace("{amount}", String.valueOf(rounded)).replace("{unit}", unit);
    }
}
