package com.yamakotaro.ecotp;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 残高を MySQL に保存するストレージ。複数サーバー間で残高を共有したい場合に使う。
 * config.yml の storage.type を "mysql" にすると有効になる (storage.mysql 以下で接続先を設定)。
 * 接続自体は MySqlConnectionProvider (ホームストレージと共有) が管理する。
 *
 * 他のサーバーが同時に同じ行を更新し得るため、読み込みは (pendingBalance にある分を除いて)
 * 必ず MySQL へ問い合わせ、結果を長期間キャッシュしない。以前は読み込み結果を無期限に
 * キャッシュしていたため、他サーバーでの入出金が反映されず、さらに このサーバーが古い
 * キャッシュ値から書き戻すことで他サーバーの変更を上書き消去してしまうバグがあった。
 * pendingBalance/pendingName は「このサーバー自身がまだ MySQL へ反映していない直近の
 * 変更」だけを保持する短命なバッファで、saveIfDirty() で書き込みが完了すると同時にそこから
 * 取り除かれる (書き込みをリクエストの度に同期実行せず、まとめて行うための仕組み)。
 */
public class MySqlBalanceStorage implements BalanceStorage {

    private final EcoTpPlugin plugin;
    private final MySqlConnectionProvider connectionProvider;
    private final String table;

    private final Map<UUID, Double> pendingBalance = new ConcurrentHashMap<>();
    private final Map<UUID, String> pendingName = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();

    public MySqlBalanceStorage(EcoTpPlugin plugin, MySqlConnectionProvider connectionProvider) {
        this.plugin = plugin;
        this.connectionProvider = connectionProvider;
        this.table = plugin.getConfig().getString("storage.mysql.table-prefix", "ecotp_") + "balances";
        createTable();
    }

    private void createTable() {
        Connection conn = connectionProvider.get();
        if (conn == null) {
            return;
        }
        String sql = "CREATE TABLE IF NOT EXISTS " + table + " ("
                + "uuid VARCHAR(36) PRIMARY KEY, "
                + "name VARCHAR(16), "
                + "balance DOUBLE NOT NULL DEFAULT 0)";
        try (Statement statement = conn.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create the balances table", e);
        }
    }

    @Override
    public boolean hasAccount(UUID uuid) {
        if (pendingBalance.containsKey(uuid)) {
            return true;
        }
        return load(uuid).isPresent();
    }

    @Override
    public double getBalance(UUID uuid) {
        Double pending = pendingBalance.get(uuid);
        if (pending != null) {
            return pending;
        }
        return load(uuid).orElse(0.0);
    }

    private Optional<Double> load(UUID uuid) {
        Connection conn = connectionProvider.get();
        if (conn == null) {
            return Optional.empty();
        }
        String sql = "SELECT balance FROM " + table + " WHERE uuid = ?";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getDouble("balance"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load balance (" + uuid + ")", e);
        }
        return Optional.empty();
    }

    @Override
    public void setBalance(UUID uuid, String name, double balance) {
        pendingBalance.put(uuid, balance);
        if (name != null) {
            pendingName.put(uuid, name);
        }
        // MySQL への書き込みはネットワーク越しになるため、取引の度に同期で書くとメインスレッドが
        // 詰まりかねない。変更フラグだけ立てて、実際の反映は定期タスク (と終了時) にまとめて行う。
        dirty.add(uuid);
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
        // まだ MySQL へ反映していない、直近このサーバーが作成/改名したアカウントも拾えるようにする。
        for (Map.Entry<UUID, String> entry : pendingName.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(name)) {
                return Optional.of(entry.getKey());
            }
        }

        Connection conn = connectionProvider.get();
        if (conn == null) {
            return Optional.empty();
        }
        String sql = "SELECT uuid FROM " + table + " WHERE LOWER(name) = LOWER(?) LIMIT 1";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, name);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(UUID.fromString(rs.getString("uuid")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to look up UUID by name (" + name + ")", e);
        }
        return Optional.empty();
    }

    @Override
    public List<BalanceEntry> getTopBalances(int limit) {
        List<BalanceEntry> entries = new ArrayList<>();
        Connection conn = connectionProvider.get();
        if (conn == null) {
            return entries;
        }
        String sql = "SELECT name, balance FROM " + table + " WHERE name IS NOT NULL ORDER BY balance DESC LIMIT ?";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    entries.add(new BalanceEntry(rs.getString("name"), rs.getDouble("balance")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to fetch the balance leaderboard", e);
        }
        return entries;
    }

    @Override
    public void saveIfDirty() {
        if (dirty.isEmpty()) {
            return;
        }
        Connection conn = connectionProvider.get();
        if (conn == null) {
            return;
        }

        String sql = "INSERT INTO " + table + " (uuid, name, balance) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE name = VALUES(name), balance = VALUES(balance)";
        List<UUID> flushed = new ArrayList<>(dirty);
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            for (UUID uuid : flushed) {
                statement.setString(1, uuid.toString());
                statement.setString(2, pendingName.get(uuid));
                statement.setDouble(3, pendingBalance.getOrDefault(uuid, 0.0));
                statement.addBatch();
            }
            statement.executeBatch();
            dirty.removeAll(flushed);
            // MySQL へ反映済みになったので、これ以降は改めて DB から読む (他サーバーの変更を
            // 取りこぼさないようにするため、反映済みの値をこのまま無期限にキャッシュし続けない)。
            flushed.forEach(pendingBalance::remove);
            flushed.forEach(pendingName::remove);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save balances (MySQL)", e);
        }
    }

    @Override
    public void close() {
        saveIfDirty();
        // 接続自体のクローズは共有元の MySqlConnectionProvider (EcoTpPlugin) が行う。
    }
}
