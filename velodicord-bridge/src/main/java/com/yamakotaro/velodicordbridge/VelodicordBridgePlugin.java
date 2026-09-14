package com.yamakotaro.velodicordbridge;

import org.bukkit.plugin.java.JavaPlugin;

import java.net.InetSocketAddress;

public class VelodicordBridgePlugin extends JavaPlugin {

    private BridgeServer server;
    private String serverName;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        serverName = getConfig().getString("server-name", "survival");
        int portIncrement = getConfig().getInt("port-increment", 1);
        int port = getServer().getPort() + portIncrement;

        server = new BridgeServer(new InetSocketAddress("0.0.0.0", port), serverName, getLogger());
        server.start();

        getServer().getPluginManager().registerEvents(new BridgeListener(server, serverName), this);
        var posCommand = new PosCommand(server, serverName);
        getCommand("pos").setExecutor(posCommand);
        getCommand("pos").setTabCompleter(posCommand);

        getLogger().info("VelodicordBridgeを起動しました (WebSocketポート: %d)".formatted(port));
    }

    @Override
    public void onDisable() {
        if (server == null) return;
        if (server.isConnected()) {
            server.sendMessage("NOTICE&🛑 **%s** が停止しました".formatted(serverName));
            server.sendMessage("FIN&%s".formatted(serverName));
        }
        try {
            server.stop();
        } catch (Exception e) {
            getLogger().warning("WebSocketサーバーの停止に失敗しました: " + e.getMessage());
        }
    }
}
