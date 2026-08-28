package com.yamakotaro.ecotp;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * このプラグイン自身が経済(残高)を保持するためのストレージ。
 * Essentials 等の外部の経済プラグインには依存しない。
 */
public class BalanceManager {

    private final EcoTpPlugin plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Map<String, UUID> nameToUuid = new HashMap<>();
    private boolean dirty = false;

    public BalanceManager(EcoTpPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "balances.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        for (String key : data.getKeys(false)) {
            String name = data.getString(key + ".name");
            if (name != null) {
                try {
                    nameToUuid.put(name.toLowerCase(), UUID.fromString(key));
                } catch (IllegalArgumentException ignored) {
                    // 壊れたキーは無視する
                }
            }
        }
    }

    public boolean hasAccount(UUID uuid) {
        return data.contains(uuid.toString() + ".balance");
    }

    public double getBalance(UUID uuid) {
        return data.getDouble(uuid.toString() + ".balance", 0.0);
    }

    public void setBalance(UUID uuid, String name, double balance) {
        String path = uuid.toString();
        data.set(path + ".balance", balance);
        if (name != null) {
            data.set(path + ".name", name);
            nameToUuid.put(name.toLowerCase(), uuid);
        }
        // 取引の度にディスクへ書き込むと、ショップ等から高頻度に呼ばれたときに
        // メインスレッドが詰まる原因になるため、変更フラグだけ立てて実際の保存は
        // 定期タスク (と終了時) にまとめて行う。
        dirty = true;
    }

    public void createAccount(UUID uuid, String name, double initialBalance) {
        if (hasAccount(uuid)) {
            return;
        }
        setBalance(uuid, name, initialBalance);
    }

    public Optional<UUID> findUuidByName(String name) {
        return Optional.ofNullable(nameToUuid.get(name.toLowerCase()));
    }

    /**
     * 変更があるときだけディスクに保存する。定期タスクとプラグイン終了時に呼ばれる。
     */
    public void saveIfDirty() {
        if (!dirty) {
            return;
        }
        try {
            data.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "balances.yml の保存に失敗しました", e);
        }
    }
}
