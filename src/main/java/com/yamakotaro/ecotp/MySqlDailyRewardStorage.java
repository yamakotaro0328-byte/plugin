package com.yamakotaro.ecotp;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.logging.Level;

/**
 * /daily の請求履歴を MySQL に保存するストレージ。複数サーバー間で streak を共有したい
 * 場合に使う (これが無いと、MySQL で残高を共有している構成でもサーバーごとに
 * 別々に1日1回請求できてしまう)。接続自体は MySqlConnectionProvider (残高・ホームと共有) が管理する。
 */
public class MySqlDailyRewardStorage implements DailyRewardStorage {

    private final EcoTpPlugin plugin;
    private final MySqlConnectionProvider connectionProvider;
    private final String table;

    public MySqlDailyRewardStorage(EcoTpPlugin plugin, MySqlConnectionProvider connectionProvider) {
        this.plugin = plugin;
        this.connectionProvider = connectionProvider;
        String prefix = plugin.getConfig().getString("storage.mysql.table-prefix", "ecotp_");
        this.table = prefix + "daily_rewards";
        createTable();
    }

    private void createTable() {
        Connection conn = connectionProvider.get();
        if (conn == null) {
            return;
        }
        String sql = "CREATE TABLE IF NOT EXISTS " + table + " ("
                + "uuid VARCHAR(36) PRIMARY KEY, "
                + "last_claim BIGINT NOT NULL DEFAULT 0, "
                + "streak INT NOT NULL DEFAULT 0)";
        try (Statement statement = conn.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create the daily rewards table", e);
        }
    }

    @Override
    public long getLastClaimMillis(UUID uuid) {
        Connection conn = connectionProvider.get();
        if (conn == null) {
            return 0L;
        }
        String sql = "SELECT last_claim FROM " + table + " WHERE uuid = ?";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong("last_claim") : 0L;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to fetch last daily claim (" + uuid + ")", e);
            return 0L;
        }
    }

    @Override
    public int getStreak(UUID uuid) {
        Connection conn = connectionProvider.get();
        if (conn == null) {
            return 0;
        }
        String sql = "SELECT streak FROM " + table + " WHERE uuid = ?";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt("streak") : 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to fetch daily streak (" + uuid + ")", e);
            return 0;
        }
    }

    @Override
    public void recordClaim(UUID uuid, long claimMillis, int newStreak) {
        Connection conn = connectionProvider.get();
        if (conn == null) {
            return;
        }
        String sql = "INSERT INTO " + table + " (uuid, last_claim, streak) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE last_claim = VALUES(last_claim), streak = VALUES(streak)";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setLong(2, claimMillis);
            statement.setInt(3, newStreak);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to record daily claim (" + uuid + ")", e);
        }
    }

    @Override
    public void close() {
        // 接続自体のクローズは共有元の MySqlConnectionProvider (EcoTpPlugin) が行う。
    }
}
