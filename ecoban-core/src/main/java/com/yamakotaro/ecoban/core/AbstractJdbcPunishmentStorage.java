package com.yamakotaro.ecoban.core;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Everything about querying/updating punishments that's identical between SQLite and MySQL
 * (which differ only in connection setup and the exact CREATE TABLE syntax - see the two
 * subclasses). Held as a single reused {@link Connection}, reconnecting on demand if it drops,
 * the same pattern as this session's other plugins' MySQL storages.
 */
public abstract class AbstractJdbcPunishmentStorage implements PunishmentStorage {

    protected final Logger logger;
    private Connection connection;

    protected AbstractJdbcPunishmentStorage(Logger logger) {
        this.logger = logger;
    }

    protected abstract Connection openConnection() throws SQLException;

    protected abstract String createPunishmentsTableSql();

    protected abstract String createPendingKicksTableSql();

    protected void init() {
        Connection conn = connection();
        if (conn == null) {
            return;
        }
        try (Statement statement = conn.createStatement()) {
            statement.executeUpdate(createPunishmentsTableSql());
            statement.executeUpdate(createPendingKicksTableSql());
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to create EcoBan's tables", e);
        }
    }

    private Connection connection() {
        try {
            if (connection == null || connection.isClosed() || !connection.isValid(2)) {
                connection = openConnection();
            }
        } catch (SQLException e) {
            try {
                connection = openConnection();
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "Failed to (re)connect to the punishment database", ex);
                return null;
            }
        }
        return connection;
    }

    @Override
    public Punishment insert(Punishment punishment) {
        Connection conn = connection();
        if (conn == null) {
            return punishment;
        }
        String sql = "INSERT INTO ecoban_punishments (type, target_uuid, target_name, ip, reason, "
                + "operator_name, created_at, expires_at, active) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, punishment.getType().name());
            statement.setString(2, punishment.getTargetUuid() != null ? punishment.getTargetUuid().toString() : null);
            statement.setString(3, punishment.getTargetName());
            statement.setString(4, punishment.getIp());
            statement.setString(5, punishment.getReason());
            statement.setString(6, punishment.getOperatorName());
            statement.setLong(7, punishment.getCreatedAt());
            statement.setLong(8, punishment.getExpiresAt());
            statement.setBoolean(9, punishment.isActive());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    punishment.setId(keys.getLong(1));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to insert a punishment", e);
        }
        return punishment;
    }

    @Override
    public boolean remove(long id, String removedByName, String removedReason) {
        Connection conn = connection();
        if (conn == null) {
            return false;
        }
        String sql = "UPDATE ecoban_punishments SET active = ?, removed_by_name = ?, removed_reason = ?, removed_at = ? "
                + "WHERE id = ? AND active = ?";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setBoolean(1, false);
            statement.setString(2, removedByName);
            statement.setString(3, removedReason);
            statement.setLong(4, System.currentTimeMillis());
            statement.setLong(5, id);
            statement.setBoolean(6, true);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to remove punishment " + id, e);
            return false;
        }
    }

    @Override
    public Punishment getActiveBan(UUID uuid) {
        return queryOne("SELECT * FROM ecoban_punishments WHERE target_uuid = ? AND active = ? "
                        + "AND (type = 'BAN' OR type = 'TEMPBAN') ORDER BY id DESC LIMIT 1",
                stmt -> {
                    stmt.setString(1, uuid.toString());
                    stmt.setBoolean(2, true);
                });
    }

    @Override
    public Punishment getActiveIpBan(String ip) {
        return queryOne("SELECT * FROM ecoban_punishments WHERE ip = ? AND active = ? AND type = 'IPBAN' "
                        + "ORDER BY id DESC LIMIT 1",
                stmt -> {
                    stmt.setString(1, ip);
                    stmt.setBoolean(2, true);
                });
    }

    @Override
    public Punishment getActiveMute(UUID uuid) {
        return queryOne("SELECT * FROM ecoban_punishments WHERE target_uuid = ? AND active = ? "
                        + "AND (type = 'MUTE' OR type = 'TEMPMUTE') ORDER BY id DESC LIMIT 1",
                stmt -> {
                    stmt.setString(1, uuid.toString());
                    stmt.setBoolean(2, true);
                });
    }

    @Override
    public List<Punishment> getHistory(UUID uuid) {
        return queryList("SELECT * FROM ecoban_punishments WHERE target_uuid = ? ORDER BY id DESC",
                stmt -> stmt.setString(1, uuid.toString()));
    }

    @Override
    public List<Punishment> search(String query, int limit) {
        String like = "%" + query.toLowerCase() + "%";
        return queryList("SELECT * FROM ecoban_punishments WHERE LOWER(target_name) LIKE ? OR ip LIKE ? "
                        + "ORDER BY id DESC LIMIT " + Math.max(1, limit),
                stmt -> {
                    stmt.setString(1, like);
                    stmt.setString(2, like);
                });
    }

    @Override
    public List<Punishment> listActive(PunishmentType type, int limit) {
        if (type == null) {
            return queryList("SELECT * FROM ecoban_punishments WHERE active = ? ORDER BY id DESC LIMIT " + Math.max(1, limit),
                    stmt -> stmt.setBoolean(1, true));
        }
        return queryList("SELECT * FROM ecoban_punishments WHERE active = ? AND type = ? ORDER BY id DESC LIMIT " + Math.max(1, limit),
                stmt -> {
                    stmt.setBoolean(1, true);
                    stmt.setString(2, type.name());
                });
    }

    @Override
    public void deactivateExpired() {
        Connection conn = connection();
        if (conn == null) {
            return;
        }
        String sql = "UPDATE ecoban_punishments SET active = ? WHERE active = ? AND expires_at > 0 AND expires_at <= ?";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setBoolean(1, false);
            statement.setBoolean(2, true);
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to deactivate expired punishments", e);
        }
    }

    @Override
    public void enqueueKick(UUID targetUuid, String targetName, String reason, String operatorName) {
        Connection conn = connection();
        if (conn == null) {
            return;
        }
        String sql = "INSERT INTO ecoban_pending_kicks (target_uuid, target_name, reason, operator_name, created_at, handled) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, targetUuid.toString());
            statement.setString(2, targetName);
            statement.setString(3, reason);
            statement.setString(4, operatorName);
            statement.setLong(5, System.currentTimeMillis());
            statement.setBoolean(6, false);
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to queue a kick", e);
        }
    }

    @Override
    public List<PendingKick> pollPendingKicks(int limit) {
        List<PendingKick> results = new ArrayList<>();
        Connection conn = connection();
        if (conn == null) {
            return results;
        }
        String sql = "SELECT id, target_uuid, target_name, reason, operator_name FROM ecoban_pending_kicks "
                + "WHERE handled = ? ORDER BY id LIMIT " + Math.max(1, limit);
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setBoolean(1, false);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    results.add(new PendingKick(rs.getLong("id"), UUID.fromString(rs.getString("target_uuid")),
                            rs.getString("target_name"), rs.getString("reason"), rs.getString("operator_name")));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to poll pending kicks", e);
        }
        return results;
    }

    @Override
    public void markKickHandled(long id) {
        Connection conn = connection();
        if (conn == null) {
            return;
        }
        try (PreparedStatement statement = conn.prepareStatement("UPDATE ecoban_pending_kicks SET handled = ? WHERE id = ?")) {
            statement.setBoolean(1, true);
            statement.setLong(2, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to mark pending kick " + id + " handled", e);
        }
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to close the punishment database connection", e);
        }
    }

    private Punishment queryOne(String sql, SqlSetter setter) {
        List<Punishment> results = queryList(sql, setter);
        return results.isEmpty() ? null : results.get(0);
    }

    private List<Punishment> queryList(String sql, SqlSetter setter) {
        List<Punishment> results = new ArrayList<>();
        Connection conn = connection();
        if (conn == null) {
            return results;
        }
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            setter.set(statement);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    results.add(fromRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to query punishments", e);
        }
        return results;
    }

    private static Punishment fromRow(ResultSet rs) throws SQLException {
        String uuidString = rs.getString("target_uuid");
        Punishment punishment = new Punishment(
                PunishmentType.valueOf(rs.getString("type")),
                uuidString != null ? UUID.fromString(uuidString) : null,
                rs.getString("target_name"),
                rs.getString("ip"),
                rs.getString("reason"),
                rs.getString("operator_name"),
                rs.getLong("created_at"),
                rs.getLong("expires_at"),
                rs.getBoolean("active"));
        punishment.setId(rs.getLong("id"));
        punishment.setRemovedByName(rs.getString("removed_by_name"));
        punishment.setRemovedReason(rs.getString("removed_reason"));
        punishment.setRemovedAt(rs.getLong("removed_at"));
        return punishment;
    }

    @FunctionalInterface
    private interface SqlSetter {
        void set(PreparedStatement statement) throws SQLException;
    }
}
