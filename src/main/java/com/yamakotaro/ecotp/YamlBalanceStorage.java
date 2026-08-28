package com.yamakotaro.ecotp;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * balances.yml に残高を保存するデフォルトのストレージ。追加設定は不要。
 * Essentials 等の外部の経済プラグインには依存しない。
 */
public class YamlBalanceStorage implements BalanceStorage {

    private final EcoTpPlugin plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Map<String, UUID> nameToUuid = new HashMap<>();
    private boolean dirty = false;

    public YamlBalanceStorage(EcoTpPlugin plugin) {
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

    @Override
    public boolean hasAccount(UUID uuid) {
        return data.contains(uuid.toString() + ".balance");
    }

    @Override
    public double getBalance(UUID uuid) {
        return data.getDouble(uuid.toString() + ".balance", 0.0);
    }

    @Override
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

    @Override
    public void createAccount(UUID uuid, String name, double initialBalance) {
        if (hasAccount(uuid)) {
            return;
        }
        setBalance(uuid, name, initialBalance);
    }

    @Override
    public Optional<UUID> findUuidByName(String name) {
        return Optional.ofNullable(nameToUuid.get(name.toLowerCase()));
    }

    @Override
    public List<BalanceEntry> getTopBalances(int limit) {
        List<BalanceEntry> entries = new ArrayList<>();
        for (String key : data.getKeys(false)) {
            String name = data.getString(key + ".name");
            if (name == null) {
                continue;
            }
            entries.add(new BalanceEntry(name, data.getDouble(key + ".balance", 0.0)));
        }
        entries.sort(Comparator.comparingDouble(BalanceEntry::balance).reversed());
        return entries.subList(0, Math.min(limit, entries.size()));
    }

    @Override
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

    @Override
    public void close() {
        saveIfDirty();
    }
}
