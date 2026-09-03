package com.yamakotaro.ecotp;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Level;

/**
 * 残高・ホームの MySQL ストレージで共有する接続。
 * Bukkit のメインスレッドから順番に呼ばれる前提で、単一の Connection を使い回す。
 */
public class MySqlConnectionProvider {

    static {
        try {
            // Bukkit のプラグインクラスローダー環境では JDBC4 の自動登録が効かないことがあるため明示的に読み込む。
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ignored) {
            // ドライバーが同梱されていない場合、後続の接続でエラーになった時点で気づける
        }
    }

    private final EcoTpPlugin plugin;
    private final String url;
    private final String username;
    private final String password;

    private Connection connection;

    public MySqlConnectionProvider(EcoTpPlugin plugin) {
        this.plugin = plugin;
        String host = plugin.getConfig().getString("storage.mysql.host", "localhost");
        int port = plugin.getConfig().getInt("storage.mysql.port", 3306);
        String database = plugin.getConfig().getString("storage.mysql.database", "ecotp");
        this.username = plugin.getConfig().getString("storage.mysql.username", "root");
        this.password = plugin.getConfig().getString("storage.mysql.password", "");
        this.url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true&characterEncoding=UTF-8";
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
