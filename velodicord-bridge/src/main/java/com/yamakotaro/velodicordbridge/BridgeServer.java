package com.yamakotaro.velodicordbridge;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.logging.Logger;

/** Velocity側のVelodicordが接続してくるのを待ち受けるWebSocketサーバー。
 * 接続してきたVelodicordから最初に届く"OK&..."ハンドシェイクにだけ応答し、
 * それ以外の設定(NoticeChannel等)はこちらでは一切保持しない
 * (このプラグインはDiscord botを持たず、投稿は全てVelodicord側のbotが行うため)。 */
public class BridgeServer extends WebSocketServer {

    private final Logger logger;
    private final String serverName;
    private volatile WebSocket connection;

    public BridgeServer(InetSocketAddress address, String serverName, Logger logger) {
        super(address);
        this.serverName = serverName;
        this.logger = logger;
        setReuseAddr(true);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        connection = conn;
        logger.info("Velodicordが接続しました: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        String[] data = message.split("&", -1);
        if (data.length > 0 && data[0].equals("OK")) {
            conn.send("RESOK&%s&".formatted(serverName));
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        if (conn == connection) connection = null;
        logger.info("Velodicordとの接続が切れました: " + reason);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        logger.warning("WebSocketエラー: " + ex.getMessage());
    }

    @Override
    public void onStart() {
        logger.info("WebSocketサーバーを起動しました: " + getAddress());
    }

    public void sendMessage(String message) {
        WebSocket conn = connection;
        if (conn != null && conn.isOpen()) {
            conn.send(message);
        } else {
            logger.warning("Velodicordに未接続のためメッセージを送信できませんでした: " + message);
        }
    }

    public boolean isConnected() {
        WebSocket conn = connection;
        return conn != null && conn.isOpen();
    }
}
