package com.yamakotaro.velodicordbridge;

import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/** Velocity側のVelodicordが接続してくるのを待ち受けるWebSocketサーバー。
 * 接続してきたVelodicordから最初に届く"OK&..."ハンドシェイクにだけ応答し、
 * それ以外の設定(NoticeChannel等)はこちらでは一切保持しない
 * (このプラグインはDiscord botを持たず、投稿は全てVelodicord側のbotが行うため)。
 * secretが設定されている場合、ヘッダーで同じsecretを提示しない接続はハンドシェイクの段階で拒否する。 */
public class BridgeServer extends WebSocketServer {

    private static final String SECRET_HEADER = "X-Velodicord-Secret";
    private static final long WARN_INTERVAL_MILLIS = 60_000L;

    private final Logger logger;
    private final String serverName;
    private final byte[] secret;
    private volatile WebSocket connection;
    private volatile long lastDisconnectedWarn;
    private final AtomicBoolean startAnnounced = new AtomicBoolean(false);

    public BridgeServer(InetSocketAddress address, String serverName, String secret, Logger logger) {
        super(address);
        this.serverName = serverName;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.logger = logger;
        setReuseAddr(true);
    }

    @Override
    public ServerHandshakeBuilder onWebsocketHandshakeReceivedAsServer(WebSocket conn, Draft draft, ClientHandshake request) throws InvalidDataException {
        ServerHandshakeBuilder builder = super.onWebsocketHandshakeReceivedAsServer(conn, draft, request);
        if (secret.length == 0) return builder;
        byte[] presented = request.getFieldValue(SECRET_HEADER).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(secret, presented)) {
            logger.warning("secretが一致しない接続を拒否しました: " + conn.getRemoteSocketAddress());
            throw new InvalidDataException(CloseFrame.POLICY_VALIDATION, "invalid secret");
        }
        return builder;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        connection = conn;
        logger.info("Velodicordが接続しました: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        if (conn != connection) return;
        String[] data = message.split("&", -1);
        if (data.length > 0 && data[0].equals("OK")) {
            // 末尾を","にしておくと、古いVelodicordでも空のbot一覧として正しく読める("&"で終わると読み取りで落ちていた)。
            conn.send("RESOK&%s&,".formatted(serverName));
            // このプラグインが起動してから最初にVelodicordと繋がったときだけ「起動しました」を通知してもらう。
            // プロキシだけ再起動して繋ぎ直した場合などは、サーバー自体は起動していないので通知しない。
            if (startAnnounced.compareAndSet(false, true)) {
                conn.send("STARTED&%s".formatted(serverName));
            }
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        if (conn == connection) {
            connection = null;
            logger.info("Velodicordとの接続が切れました: " + reason);
        }
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
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastDisconnectedWarn > WARN_INTERVAL_MILLIS) {
            lastDisconnectedWarn = now;
            logger.warning("Velodicordに未接続のため、チャット・通知を中継できていません(1分ごとに表示)");
        }
    }

    public boolean isConnected() {
        WebSocket conn = connection;
        return conn != null && conn.isOpen();
    }
}
