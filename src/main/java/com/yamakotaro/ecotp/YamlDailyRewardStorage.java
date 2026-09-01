package com.yamakotaro.ecotp;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;

/**
 * daily-rewards.yml に保存するデフォルトのストレージ。請求は1人1日1回程度の頻度なので、
 * 残高のような変更フラグでのバッチ保存はせず、請求のたびに直接書き込む
 * (MySqlHomeStorage の setHome と同じ考え方)。
 */
public class YamlDailyRewardStorage implements DailyRewardStorage {

    private final EcoTpPlugin plugin;
    private final File file;
    private final YamlConfiguration data;

    public YamlDailyRewardStorage(EcoTpPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "daily-rewards.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    @Override
    public long getLastClaimMillis(UUID uuid) {
        return data.getLong(uuid.toString() + ".last-claim", 0L);
    }

    @Override
    public int getStreak(UUID uuid) {
        return data.getInt(uuid.toString() + ".streak", 0);
    }

    @Override
    public void recordClaim(UUID uuid, long claimMillis, int newStreak) {
        String path = uuid.toString();
        data.set(path + ".last-claim", claimMillis);
        data.set(path + ".streak", newStreak);
        try {
            plugin.getDataFolder().mkdirs();
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save daily-rewards.yml", e);
        }
    }

    @Override
    public void close() {
        // フラットファイルなので特に閉じるものはない。
    }
}
