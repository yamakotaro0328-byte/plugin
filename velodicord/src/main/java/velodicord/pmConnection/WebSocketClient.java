package velodicord.pmConnection;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.apache.commons.lang3.exception.ExceptionUtils;
import velodicord.Discordbot;
import velodicord.Velodicord;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;

import static velodicord.Config.*;
import static velodicord.Discordbot.*;

public class WebSocketClient {

    /** velodicord-bridgeのconfig.ymlのsecretと照合されるヘッダー。再接続時もrecreate()でそのまま引き継がれる。 */
    public static final String SECRET_HEADER = "X-Velodicord-Secret";

    private volatile WebSocket ws;

    private final String uri;

    private final WebSocketFactory factory;

    private volatile boolean shouldReconnect = true;

    private volatile boolean stoppedCleanly = false;

    private volatile boolean lostAnnounced = false;

    /** このプロキシ起動中に一度でもvelodicord-bridgeと繋がったか。繋がったことのあるサーバーの起動・停止はブリッジ経由で通知する。 */
    private volatile boolean everConnected = false;

    public boolean hasEverConnected() {
        return everConnected;
    }

    private volatile boolean connecting = false;

    private final String serverName;

    public WebSocketClient(InetSocketAddress address, String serverName) {
        this.uri = "ws://%s:%d/ws/pm".formatted(address.getAddress().getHostAddress(), address.getPort() + Velodicord.getWebSocketPortIncrement());
        this.factory = new WebSocketFactory();
        this.serverName = serverName;

        Velodicord.getVelodicord().getLogger().info("WebSocketClient initialized with URI: {}", uri);
        startConnectLoop();
    }

    private void startConnectLoop() {
        new Thread(() -> {
            connecting = true;
            while (shouldReconnect) {
                try {
                    WebSocket socket = factory.createSocket(uri);
                    String secret = getConfig().getOrDefault("WebSocketSecret", "");
                    if (!secret.isEmpty()) socket.addHeader(SECRET_HEADER, secret);
                    ws = socket
                            .addListener(new WebSocketAdapter() {

                                @Override
                                public void onConnected(WebSocket ws, java.util.Map<String, java.util.List<String>> headers) {
                                    Velodicord.getVelodicord().getLogger().info("WebSocket connection established.");
                                    everConnected = true;
                                    // 切断を通知した後に繋がり直した場合、サーバーが再起動していればSTARTEDが届く。
                                    // 少し待ってもSTARTEDが来なければ、回線が一時的に切れていただけなので「再接続」を通知する。
                                    if (lostAnnounced) {
                                        Velodicord.getVelodicord().getProxy().getScheduler()
                                                .buildTask(Velodicord.getVelodicord(), () -> {
                                                    if (lostAnnounced) {
                                                        lostAnnounced = false;
                                                        announceReconnected();
                                                    }
                                                })
                                                .delay(3, java.util.concurrent.TimeUnit.SECONDS)
                                                .schedule();
                                    }

                                    try {
                                        ws.sendText("OK&%s&%s&%s&%s&%s".formatted(getNoticeChannel().getId(), getLogForumChannel().map(ForumChannel::getId).orElse(""), getCommandChannel(), Discordbot.getCommandRole().getId(), Files.readString(getDisadmincommandjson())));
                                    } catch (IOException e) {
                                        Velodicord.getVelodicord().getLogger().error("Failed to read command config files: {}", ExceptionUtils.getStackTrace(e));
                                    }
                                }

                                @Override
                                public void onTextMessage(WebSocket ws, String message) {
                                    if (message.startsWith("FIN&")) stoppedCleanly = true;
                                    if (message.startsWith("STARTED&")) lostAnnounced = false;
                                    PluginMessageManager.receive("VELOCITY&%s".formatted(message));
                                }

                                @Override
                                public void onDisconnected(WebSocket ws,
                                                           com.neovisionaries.ws.client.WebSocketFrame serverCloseFrame,
                                                           com.neovisionaries.ws.client.WebSocketFrame clientCloseFrame,
                                                           boolean closedByServer) {
                                    // 停止通知(FIN)なしに切れた = クラッシュ・強制終了・ネットワーク断。プロキシ自身の停止中(shouldReconnect=false)は除く。
                                    if (shouldReconnect && !stoppedCleanly) announceLost();
                                    stoppedCleanly = false;
                                    if (shouldReconnect) {
                                        Velodicord.getVelodicord().getLogger().info("WebSocket connection closed. try to reconnect...");
                                        reconnect(ws);
                                    } else {
                                        Velodicord.getVelodicord().getLogger().info("WebSocket connection closed.");
                                    }
                                }

                                @Override
                                public void onError(WebSocket ws, WebSocketException e) {
                                    Velodicord.getVelodicord().getLogger().error("WebSocket error occurred.: {}", ExceptionUtils.getStackTrace(e));
                                }
                            })
                            .connect();

                    connecting = false;
                    return;
                } catch (Exception e) {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ex) {
                        Velodicord.getVelodicord().getLogger().error("WebSocket connection interrupted while trying to connect.: {}", ExceptionUtils.getStackTrace(e));
                    }
                }
            }

            connecting = false;
        }, "websocket-connect").start();
    }

    public void reconnect(WebSocket oldws) {
        if (connecting) {
            return;
        }

        new Thread(() -> {
            connecting = true;
            while (shouldReconnect) {
                try {
                    ws = oldws.recreate().connect();
                    connecting = false;

                    try {
                        sendMessage("OK&%s&%s&%s&%s&%s".formatted(getNoticeChannel().getId(), getLogForumChannel().map(ForumChannel::getId).orElse(""), getCommandChannel(), Discordbot.getCommandRole().getId(), Files.readString(getDisadmincommandjson())));
                    } catch (IOException e) {
                        Velodicord.getVelodicord().getLogger().error("Failed to read command config files: {}", ExceptionUtils.getStackTrace(e));
                    }
                    return;
                } catch (Exception e) {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ex) {
                        Velodicord.getVelodicord().getLogger().error("WebSocket connection interrupted while trying to reconnect.: {}", ExceptionUtils.getStackTrace(e));
                    }
                }
            }
            connecting = false;
        }, "websocket-reconnect").start();
    }

    private void announceReconnected() {
        Velodicord.getVelodicord().getProxy().sendMessage(Component.text()
                .append(Component.text("🔄 "))
                .append(Component.text("[%s]".formatted(serverName), NamedTextColor.DARK_GREEN))
                .append(Component.text(" と再接続しました", NamedTextColor.YELLOW)));
        try {
            getNoticeChannel().sendMessage("🔄 **%s** と再接続しました".formatted(serverName)).queue();
        } catch (RuntimeException e) {
            Velodicord.getVelodicord().getLogger().warn("[{}] の再接続をDiscordに通知できませんでした: {}", serverName, e.toString());
        }
    }

    private void announceLost() {
        lostAnnounced = true;
        Velodicord.getVelodicord().getProxy().sendMessage(Component.text()
                .append(Component.text("⚠ "))
                .append(Component.text("[%s]".formatted(serverName), NamedTextColor.DARK_GREEN))
                .append(Component.text(" との接続が切れました(停止通知なし)", NamedTextColor.RED)));
        try {
            getNoticeChannel().sendMessage("⚠️ **%s** との接続が停止通知なしに切れました(クラッシュ・強制終了・回線断の可能性)".formatted(serverName)).queue();
        } catch (RuntimeException e) {
            Velodicord.getVelodicord().getLogger().warn("[{}] の切断をDiscordに通知できませんでした: {}", serverName, e.toString());
        }
    }

    public boolean isConnected() {
        WebSocket socket = ws;
        return socket != null && socket.isOpen();
    }

    public void close() {
        shouldReconnect = false;
        if (ws != null) ws.disconnect();
    }

    public void sendMessage(String message) {
        try {
            if (ws != null && ws.isOpen()) {
                ws.sendText(message);
            }
        } catch (Exception e) {
            Velodicord.getVelodicord().getLogger().error("Failed to send WebSocket message.: {}", ExceptionUtils.getStackTrace(e));
        }
    }
}
