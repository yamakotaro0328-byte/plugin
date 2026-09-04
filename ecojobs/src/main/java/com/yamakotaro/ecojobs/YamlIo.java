package com.yamakotaro.ecojobs;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * YamlConfiguration.loadConfiguration(File) と FileConfiguration#save(File) の代わりに
 * 必ず使うこと。この2つはOS/JVMの「プラットフォームのデフォルト文字コード」に依存して
 * ファイルを読み書きするため、そのデフォルトがUTF-8でない環境では設定の非ASCII文字(日本語等)が
 * 読み込むたびに文字化けし、しかもその文字化けした内容がそのまま上書き保存されて元に戻せなく
 * なる (EcoTPで実際に起きたバグ)。plugin.getConfig()/reloadConfig() も内部で同じ問題のある
 * 読み込みをするため、このプラグインはそれらを使わず、常にこのクラス経由でconfig.ymlを読み書きする。
 */
public final class YamlIo {

    private YamlIo() {
    }

    /** ファイルが存在しない、または読み込みに失敗した場合は空の設定を返す。 */
    public static YamlConfiguration load(File file) {
        if (!file.exists()) {
            return new YamlConfiguration();
        }
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException e) {
            return new YamlConfiguration();
        }
    }

    public static void save(YamlConfiguration config, File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        Files.writeString(file.toPath(), config.saveToString(), StandardCharsets.UTF_8);
    }
}
