package com.yamakotaro.ecojobs.storage;

import com.yamakotaro.ecojobs.EcoJobsPlugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Level;

/**
 * The single MySQL connection shared by {@link MySqlJobStorage}, reconnecting on demand if it
 * drops. Bukkit's scheduler only ever touches this from the main thread, so one connection is
 * enough - no pooling needed.
 */
public class MySqlConnectionProvider {

    static {
        try {
            // Plugin classloaders don't always trigger the JDBC4 auto-registration, so load it
            // explicitly; if the driver truly isn't bundled, this fails silently and the first
            // real connection attempt below reports it instead.
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ignored) {
            // handled by the first connect() failure
        }
    }

    private final EcoJobsPlugin plugin;
    private final String url;
    private final String username;
    private final String password;

    private Connection connection;

    public MySqlConnectionProvider(EcoJobsPlugin plugin) {
        this.plugin = plugin;
        String host = plugin.config().getString("storage.mysql.host", "localhost");
        int port = plugin.config().getInt("storage.mysql.port", 3306);
        String database = plugin.config().getString("storage.mysql.database", "ecojobs");
        this.username = plugin.config().getString("storage.mysql.username", "root");
        this.password = plugin.config().getString("storage.mysql.password", "");
        this.url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true&characterEncoding=utf8";
        connect();
    }

    private void connect() {
        try {
            connection = DriverManager.getConnection(url, username, password);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect to MySQL. Check the storage.mysql settings in config.yml.", e);
        }
    }

    public Connection get() {
        try {
            if (connection == null || connection.isClosed() || !connection.isValid(2)) {
                connect();
            }
        } catch (SQLException e) {
            connect();
        }
        return connection;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to close the MySQL connection", e);
        }
    }
}
