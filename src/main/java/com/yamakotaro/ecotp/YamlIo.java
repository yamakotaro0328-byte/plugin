package com.yamakotaro.ecotp;

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
 * ファイルを読み書きするため、そのデフォルトがUTF-8でない環境ではホーム名・寄付メッセージ・
 * messages.yml の文言など、非ASCII文字(日本語等)を含むデータが読み込むたびに文字化けし、
 * しかもその文字化けした内容がそのまま上書き保存されて元に戻せなくなるバグがあった。
 * ここでは常に明示的にUTF-8を指定して読み書きすることで、サーバー環境に左右されず
 * 正しく往復できるようにする。
 */
public final class YamlIo {

    private YamlIo() {
    }

    /** ファイルが存在しない、または読み込みに失敗した場合は空の設定を返す (loadConfiguration(File)と同じ挙動)。 */
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
        // 一時ファイルに書いてから差し替える: 書き込み中にサーバーが落ちても元のファイルが壊れない。
        java.nio.file.Path target = file.toPath();
        java.nio.file.Path temp = target.resolveSibling(file.getName() + ".tmp");
        Files.writeString(temp, config.saveToString(), StandardCharsets.UTF_8);
        try {
            Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
