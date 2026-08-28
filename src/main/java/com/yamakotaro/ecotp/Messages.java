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
 * config.yml の language: en/ja に応じて、同梱の messages_en.yml か
 * messages_ja.yml を messages.yml としてコピーする。
 * messages.yml の先頭には、生成時点の言語を記録する隠しキー (meta.language) を
 * 付け加えておき、reload() のたびに config.yml の language と比較する
 * (マーカーが無い messages.yml は、このマーカーが導入される前の "en" 固定時代の
 * ものとみなす)。値が変わっていれば「言語を切り替えたい」という意思表示とみなし、
 * その言語のテンプレートで messages.yml を再生成する (このときカスタマイズした
 * 文言は上書きされる)。値が一致している間は、既存の messages.yml (と自由な
 * 編集内容) がそのまま使われる。
 */
public class Messages {

    private static final String LANGUAGE_MARKER_PATH = "meta.language";

    private final EcoTpPlugin plugin;
    private final File file;
    private YamlConfiguration data;

    public Messages(EcoTpPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "messages.yml");
        reload();
    }

    private String configuredLanguage() {
        String language = plugin.getConfig().getString("language", "en");
        return "ja".equalsIgnoreCase(language) ? "ja" : "en";
    }

    private String bundledResourceName(String language) {
        return "ja".equals(language) ? "messages_ja.yml" : "messages_en.yml";
    }

    private void regenerateFromBundledResource(String language) {
        String resourceName = bundledResourceName(language);
        try (InputStream in = plugin.getResource(resourceName)) {
            if (in == null) {
                plugin.getLogger().warning("Bundled " + resourceName + " was not found.");
                return;
            }
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            Files.copy(in, file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            // 生成した言語を記録しておく。テンプレート自体にはこのキーは無いので追記する。
            YamlConfiguration generated = YamlConfiguration.loadConfiguration(file);
            generated.set(LANGUAGE_MARKER_PATH, language);
            generated.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create messages.yml", e);
        }
    }

    public void reload() {
        String desiredLanguage = configuredLanguage();
        if (!file.exists()) {
            regenerateFromBundledResource(desiredLanguage);
        } else {
            YamlConfiguration existing = YamlConfiguration.loadConfiguration(file);
            // マーカーが無い messages.yml は、このマーカーが存在する前のバージョンで
            // 生成されたものであり、当時のデフォルト言語は常に "en" だった。
            String storedLanguage = existing.getString(LANGUAGE_MARKER_PATH, "en");
            if (!storedLanguage.equals(desiredLanguage)) {
                plugin.getLogger().info("language changed to \"" + desiredLanguage
                        + "\" in config.yml: regenerating messages.yml from the bundled "
                        + bundledResourceName(desiredLanguage) + " template.");
                regenerateFromBundledResource(desiredLanguage);
            }
        }

        this.data = YamlConfiguration.loadConfiguration(file);
        // jar 内のデフォルト値をフォールバックとして重ねる (アップデートで新しいキーが増えても壊れない)
        try (InputStream in = plugin.getResource(bundledResourceName(desiredLanguage))) {
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
