package com.yamakotaro.ecocosmetics;

import java.util.ArrayList;
import java.util.List;

/**
 * config.yml の messages.en / messages.ja からメッセージを読み込み、プレースホルダーを
 * 置換する。plugin.config() (EcoCosmeticsPlugin が独自にUTF-8で読み込んだ設定) を経由するため、
 * Bukkit標準の getConfig() が持つ文字化けリスクを避けられる。
 */
public class Messages {

    private final EcoCosmeticsPlugin plugin;

    public Messages(EcoCosmeticsPlugin plugin) {
        this.plugin = plugin;
    }

    private String language() {
        String language = plugin.config().getString("language", "en");
        return "ja".equalsIgnoreCase(language) ? "ja" : "en";
    }

    /**
     * @param path         例: "shop.title"
     * @param replacements "key1", value1, "key2", value2 ... のペア。value は toString() される。
     */
    public String get(String path, Object... replacements) {
        String fullPath = "messages." + language() + "." + path;
        String template = plugin.config().getString(fullPath, path);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            String key = "{" + replacements[i] + "}";
            String value = String.valueOf(replacements[i + 1]);
            template = template.replace(key, value);
        }
        return ChatUtil.color(template);
    }

    public List<String> getList(String path, Object... replacements) {
        String fullPath = "messages." + language() + "." + path;
        List<String> result = new ArrayList<>();
        for (String line : plugin.config().getStringList(fullPath)) {
            String template = line;
            for (int i = 0; i + 1 < replacements.length; i += 2) {
                String key = "{" + replacements[i] + "}";
                String value = String.valueOf(replacements[i + 1]);
                template = template.replace(key, value);
            }
            result.add(ChatUtil.color(template));
        }
        return result;
    }
}
