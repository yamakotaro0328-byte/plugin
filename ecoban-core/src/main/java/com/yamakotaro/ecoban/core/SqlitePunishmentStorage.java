package com.yamakotaro.ecoban.core;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * Default storage: a single SQLite file, no setup needed. Only usable by one process at a time,
 * so a Velocity proxy plus backend servers must use {@link MySqlPunishmentStorage} instead.
 */
public class SqlitePunishmentStorage extends AbstractJdbcPunishmentStorage {

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ignored) {
            // handled by the first connect() failure
        }
    }

    private final File file;

    public SqlitePunishmentStorage(File file, Logger logger) {
        super(logger);
        this.file = file;
        init();
    }

    @Override
    protected Connection openConnection() throws SQLException {
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        return DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
    }

    @Override
    protected String createPunishmentsTableSql() {
        return "CREATE TABLE IF NOT EXISTS ecoban_punishments ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "type VARCHAR(16) NOT NULL, "
                + "target_uuid VARCHAR(36), "
                + "target_name VARCHAR(16), "
                + "ip VARCHAR(45), "
                + "reason TEXT, "
                + "operator_name VARCHAR(16), "
                + "created_at BIGINT NOT NULL, "
                + "expires_at BIGINT NOT NULL DEFAULT 0, "
                + "active BOOLEAN NOT NULL DEFAULT 1, "
                + "removed_by_name VARCHAR(16), "
                + "removed_reason TEXT, "
                + "removed_at BIGINT)";
    }

    @Override
    protected String createPendingKicksTableSql() {
        return "CREATE TABLE IF NOT EXISTS ecoban_pending_kicks ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "target_uuid VARCHAR(36) NOT NULL, "
                + "target_name VARCHAR(16), "
                + "reason TEXT, "
                + "operator_name VARCHAR(16), "
                + "created_at BIGINT NOT NULL, "
                + "handled BOOLEAN NOT NULL DEFAULT 0)";
    }
}
