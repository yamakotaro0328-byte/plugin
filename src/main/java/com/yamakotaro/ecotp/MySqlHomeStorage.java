package com.yamakotaro.ecotp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * ホームを MySQL に保存するストレージ。複数サーバー間でホームを共有したい場合に使う。
 * config.yml の storage.type を "mysql" にすると有効になる。接続自体は
 * MySqlConnectionProvider (残高ストレージと共有) が管理する。
 */
public class MySqlHomeStorage implements HomeStorage {

    private final EcoTpPlugin plugin;
    private final MySqlConnectionProvider connectionProvider;
    private final String homesTable;
    private final String countsTable;

    public MySqlHomeStorage(EcoTpPlugin plugin, MySqlConnectionProvider connectionProvider) {
        this.plugin = plugin;
        this.connectionProvider = connectionProvider;
        String prefix = plugin.getConfig().getString("storage.mysql.table-prefix", "ecotp_");
        this.homesTable = prefix + "homes";
        this.countsTable = prefix + "home_counts";
        createTables();
    }

    private void createTables() {
        Connection conn = connectionProvider.get();
        if (conn == null) {
            return;
        }
        // ホーム名は日本語などの非ASCII文字も許可しているため、テーブル自体の文字コードを
        // 明示的にutf8mb4にしておく (MySQLサーバーのデフォルトがlatin1等だと文字化けするため)。
        String homesSql = "CREATE TABLE IF NOT EXISTS " + homesTable + " ("
                + "uuid VARCHAR(36) NOT NULL, "
                + "name VARCHAR(16) NOT NULL, "
                + "world VARCHAR(64) NOT NULL, "
                + "x DOUBLE NOT NULL, "
                + "y DOUBLE NOT NULL, "
                + "z DOUBLE NOT NULL, "
                + "yaw FLOAT NOT NULL, "
                + "pitch FLOAT NOT NULL, "
                + "PRIMARY KEY (uuid, name)) DEFAULT CHARSET=utf8mb4";
        String countsSql = "CREATE TABLE IF NOT EXISTS " + countsTable + " ("
                + "uuid VARCHAR(36) PRIMARY KEY, "
                + "count INT NOT NULL DEFAULT 0)";
        try (Statement statement = conn.createStatement()) {
            statement.executeUpdate(homesSql);
            statement.executeUpdate(countsSql);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create the homes tables", e);
        }
    }

    @Override
    public boolean hasHome(UUID uuid, String name) {
        return getHome(uuid, name) != null;
    }

    @Override
    public Location getHome(UUID uuid, String name) {
        Connection conn = connectionProvider.get();
        if (conn == null) {
            return null;
        }
        String sql = "SELECT world, x, y, z, yaw, pitch FROM " + homesTable + " WHERE uuid = ? AND name = ?";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, name);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                World world = Bukkit.getWorld(rs.getString("world"));
                if (world == null) {
                    return null;
                }
                return new Location(world, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                        rs.getFloat("yaw"), rs.getFloat("pitch"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load home (" + uuid + "/" + name + ")", e);
            return null;
        }
    }

    @Override
    public void setHome(UUID uuid, String name, Location location) {
        Connection conn = connectionProvider.get();
        if (conn == null) {
            return;
        }
        String sql = "INSERT INTO " + homesTable + " (uuid, name, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE world = VALUES(world), x = VALUES(x), y = VALUES(y), z = VALUES(z), "
                + "yaw = VALUES(yaw), pitch = VALUES(pitch)";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, name);
            statement.setString(3, location.getWorld().getName());
            statement.setDouble(4, location.getX());
            statement.setDouble(5, location.getY());
            statement.setDouble(6, location.getZ());
            statement.setFloat(7, location.getYaw());
            statement.setFloat(8, location.getPitch());
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save home (" + uuid + "/" + name + ")", e);
        }
    }

    @Override
    public boolean deleteHome(UUID uuid, String name) {
        Connection conn = connectionProvider.get();
        if (conn == null) {
            return false;
        }
        String sql = "DELETE FROM " + homesTable + " WHERE uuid = ? AND name = ?";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, name);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete home (" + uuid + "/" + name + ")", e);
            return false;
        }
    }

    @Override
    public List<String> getHomeNames(UUID uuid) {
        List<String> names = new ArrayList<>();
        Connection conn = connectionProvider.get();
        if (conn == null) {
            return names;
        }
        String sql = "SELECT name FROM " + homesTable + " WHERE uuid = ?";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString("name"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to fetch home names (" + uuid + ")", e);
        }
        return names;
    }

    @Override
    public int getSetHomeCount(UUID uuid) {
        Connection conn = connectionProvider.get();
        if (conn == null) {
            return 0;
        }
        String sql = "SELECT count FROM " + countsTable + " WHERE uuid = ?";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt("count") : 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to fetch sethome count (" + uuid + ")", e);
            return 0;
        }
    }

    @Override
    public void incrementSetHomeCount(UUID uuid) {
        Connection conn = connectionProvider.get();
        if (conn == null) {
            return;
        }
        String sql = "INSERT INTO " + countsTable + " (uuid, count) VALUES (?, 1) "
                + "ON DUPLICATE KEY UPDATE count = count + 1";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update sethome count (" + uuid + ")", e);
        }
    }

    @Override
    public void saveIfDirty() {
        // このストレージは書き込みの度に即座に反映するため、ここでは何もしない。
    }

    @Override
    public void close() {
        // 接続自体のクローズは共有元の MySqlConnectionProvider (EcoTpPlugin) が行う。
    }
}
