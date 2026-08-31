package com.yamakotaro.ecojobs.storage;

import com.yamakotaro.ecojobs.EcoJobsPlugin;
import com.yamakotaro.ecojobs.PlayerJobData;
import com.yamakotaro.ecojobs.PlayerJobProgress;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Shares job progress across every server pointed at the same database - config.yml's
 * storage.type: mysql (with storage.mysql.* for connection details). Progress only ever grows
 * (jobs are never un-joined at the data level, see PlayerJobData's docs), so every write here is
 * a plain upsert; nothing is ever deleted.
 */
public class MySqlJobStorage implements JobStorage {

    private final EcoJobsPlugin plugin;
    private final MySqlConnectionProvider connectionProvider;
    private final String playersTable;
    private final String progressTable;
    private final String explorerTable;

    public MySqlJobStorage(EcoJobsPlugin plugin) {
        this.plugin = plugin;
        this.connectionProvider = new MySqlConnectionProvider(plugin);
        String prefix = plugin.getConfig().getString("storage.mysql.table-prefix", "ecojobs_");
        this.playersTable = prefix + "players";
        this.progressTable = prefix + "progress";
        this.explorerTable = prefix + "explorer";
        createTables();
    }

    private void createTables() {
        Connection conn = connectionProvider.get();
        if (conn == null) {
            return;
        }
        try (Statement statement = conn.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + playersTable + " ("
                    + "uuid VARCHAR(36) PRIMARY KEY, "
                    + "name VARCHAR(16))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + progressTable + " ("
                    + "uuid VARCHAR(36), "
                    + "job_id VARCHAR(64), "
                    + "level INT NOT NULL DEFAULT 1, "
                    + "xp DOUBLE NOT NULL DEFAULT 0, "
                    + "prestige INT NOT NULL DEFAULT 0, "
                    + "joined BOOLEAN NOT NULL DEFAULT TRUE, "
                    + "PRIMARY KEY (uuid, job_id))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + explorerTable + " ("
                    + "uuid VARCHAR(36), "
                    + "world VARCHAR(64), "
                    + "distance DOUBLE NOT NULL DEFAULT 0, "
                    + "PRIMARY KEY (uuid, world))");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create EcoJobs tables", e);
        }
    }

    @Override
    public Map<UUID, PlayerJobData> loadAll() {
        Map<UUID, PlayerJobData> data = new HashMap<>();
        Connection conn = connectionProvider.get();
        if (conn == null) {
            return data;
        }
        try (Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery("SELECT uuid, name FROM " + playersTable)) {
            while (rs.next()) {
                data.put(UUID.fromString(rs.getString("uuid")), new PlayerJobData(rs.getString("name")));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load EcoJobs players", e);
            return data;
        }
        try (Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery("SELECT uuid, job_id, level, xp, prestige, joined FROM " + progressTable)) {
            while (rs.next()) {
                PlayerJobData playerData = data.get(UUID.fromString(rs.getString("uuid")));
                if (playerData == null) {
                    continue;
                }
                playerData.getProgress().put(rs.getString("job_id"),
                        new PlayerJobProgress(rs.getInt("level"), rs.getDouble("xp"), rs.getInt("prestige")));
                if (rs.getBoolean("joined")) {
                    playerData.getJoined().add(rs.getString("job_id"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load EcoJobs progress", e);
        }
        try (Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery("SELECT uuid, world, distance FROM " + explorerTable)) {
            while (rs.next()) {
                PlayerJobData playerData = data.get(UUID.fromString(rs.getString("uuid")));
                if (playerData == null) {
                    continue;
                }
                playerData.setExplorerFarthestDistance(rs.getString("world"), rs.getDouble("distance"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load EcoJobs explorer distances", e);
        }
        return data;
    }

    @Override
    public void saveAll(Map<UUID, PlayerJobData> allData, Set<UUID> dirtyUuids) {
        if (dirtyUuids.isEmpty()) {
            return;
        }
        Connection conn = connectionProvider.get();
        if (conn == null) {
            return;
        }
        try (PreparedStatement playerStatement = conn.prepareStatement(
                "INSERT INTO " + playersTable + " (uuid, name) VALUES (?, ?) "
                        + "ON DUPLICATE KEY UPDATE name = VALUES(name)");
             PreparedStatement progressStatement = conn.prepareStatement(
                     "INSERT INTO " + progressTable + " (uuid, job_id, level, xp, prestige, joined) VALUES (?, ?, ?, ?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE level = VALUES(level), xp = VALUES(xp), prestige = VALUES(prestige), joined = VALUES(joined)");
             PreparedStatement explorerStatement = conn.prepareStatement(
                     "INSERT INTO " + explorerTable + " (uuid, world, distance) VALUES (?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE distance = VALUES(distance)")) {
            for (UUID uuid : dirtyUuids) {
                PlayerJobData playerData = allData.get(uuid);
                if (playerData == null) {
                    continue;
                }
                playerStatement.setString(1, uuid.toString());
                playerStatement.setString(2, playerData.getName());
                playerStatement.addBatch();

                for (Map.Entry<String, PlayerJobProgress> entry : playerData.getProgress().entrySet()) {
                    PlayerJobProgress progress = entry.getValue();
                    progressStatement.setString(1, uuid.toString());
                    progressStatement.setString(2, entry.getKey());
                    progressStatement.setInt(3, progress.getLevel());
                    progressStatement.setDouble(4, progress.getXp());
                    progressStatement.setInt(5, progress.getPrestige());
                    progressStatement.setBoolean(6, playerData.getJoined().contains(entry.getKey()));
                    progressStatement.addBatch();
                }

                for (Map.Entry<String, Double> entry : playerData.getExplorerDistanceByWorld().entrySet()) {
                    explorerStatement.setString(1, uuid.toString());
                    explorerStatement.setString(2, entry.getKey());
                    explorerStatement.setDouble(3, entry.getValue());
                    explorerStatement.addBatch();
                }
            }
            playerStatement.executeBatch();
            progressStatement.executeBatch();
            explorerStatement.executeBatch();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save EcoJobs data (MySQL)", e);
        }
    }

    @Override
    public void close() {
        connectionProvider.close();
    }
}
