package com.yamakotaro.ecoban.core;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * Shares punishments across every process pointed at the same database - required when EcoBan
 * runs on a Velocity proxy (see ecoban-velocity) alongside backend servers, since a ban issued on
 * one server (or the proxy) needs to be visible everywhere immediately.
 */
public class MySqlPunishmentStorage extends AbstractJdbcPunishmentStorage {

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ignored) {
            // handled by the first connect() failure
        }
    }

    private final String url;
    private final String username;
    private final String password;

    public MySqlPunishmentStorage(String host, int port, String database, String username, String password, Logger logger) {
        super(logger);
        this.url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true&characterEncoding=utf8";
        this.username = username;
        this.password = password;
        init();
    }

    @Override
    protected Connection openConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }

    @Override
    protected String createPunishmentsTableSql() {
        return "CREATE TABLE IF NOT EXISTS ecoban_punishments ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                + "type VARCHAR(16) NOT NULL, "
                + "target_uuid VARCHAR(36), "
                + "target_name VARCHAR(16), "
                + "ip VARCHAR(45), "
                + "reason TEXT, "
                + "operator_name VARCHAR(16), "
                + "created_at BIGINT NOT NULL, "
                + "expires_at BIGINT NOT NULL DEFAULT 0, "
                + "active BOOLEAN NOT NULL DEFAULT TRUE, "
                + "removed_by_name VARCHAR(16), "
                + "removed_reason TEXT, "
                + "removed_at BIGINT, "
                + "INDEX idx_target_uuid (target_uuid), "
                + "INDEX idx_ip (ip), "
                + "INDEX idx_active_type (active, type))";
    }

    @Override
    protected String createPendingKicksTableSql() {
        return "CREATE TABLE IF NOT EXISTS ecoban_pending_kicks ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                + "target_uuid VARCHAR(36) NOT NULL, "
                + "target_name VARCHAR(16), "
                + "reason TEXT, "
                + "operator_name VARCHAR(16), "
                + "created_at BIGINT NOT NULL, "
                + "handled BOOLEAN NOT NULL DEFAULT FALSE, "
                + "INDEX idx_handled (handled))";
    }
}
