package com.yamakotaro.ecotp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * 古典的なVotifier(V1)とNuVotifier互換のプロトコルv2の両方に対応したサーバーを
 * EcoTP自身に内蔵し、NuVotifier等の別プラグインを一切導入せずに投票報酬を使えるように
 * する。投票サイト側には、V1鍵方式なら votifier-rsa/public.key の中身(Base64)を、
 * トークン方式(v2)なら votifier-tokens.yml の tokens.default の値を、それぞれ
 * そのまま貼り付けてもらえばよい。
 *
 * v1/v2のどちらで送ってきたかは、クライアントが最初に送ってくる2バイトが
 * プロトコルv2のマジックナンバー(0x73 0x3A)かどうかで判別する
 * (NuVotifier本家の VotifierProtocolDifferentiator と同じ方式)。挨拶メッセージは
 * 常に "VOTIFIER 2 <challenge>\n" 形式で送る: v1のクライアントは挨拶の中身を検証せず
 * 1行読み捨てて即座に256バイトの暗号化ブロックを送ってくるだけなので実害が無い一方、
 * v2専用の投票サイトは"VOTIFIER 2 "で始まる挨拶が来ないと(vote先が対応していないと
 * 判断して)何も送らずに諦めてしまう。これを"VOTIFIER 1.9\n"のまま固定していたのが、
 * v2のみ対応の投票サイトから投票できない(送信自体が行われずサーバー側のログにも
 * 何も残らない)不具合の原因だった。
 *
 * NuVotifier(等の互換品)が既に導入されている場合は、ポート8192の競合を避けるため
 * このリスナー自身は起動を自動的に見送る(VoteRewardListenerがリフレクションで
 * NuVotifier側のイベントを拾う)。votifier.enabled: falseで明示的に無効化することもできる。
 */
public class VotifierServer {

    // プロトコルv2のクライアントが送ってくるメッセージの先頭2バイト固定値。これが無ければv1と
    // みなす (NuVotifier本家 VotifierProtocolDifferentiator の PROTOCOL_2_MAGIC と同じ値)。
    private static final byte[] PROTOCOL_2_MAGIC = {0x73, 0x3A};
    private static final int V1_BLOCK_SIZE = 256;
    // NuVotifier本家のLengthFieldBasedFrameDecoderと同じ上限 (1024バイト)。
    private static final int V2_MAX_MESSAGE_LENGTH = 1024;
    /** /ecotp votetest が送る投票のサービス名。これは数えず、報酬も出さない (real を付けた時を除く)。 */
    static final String TEST_SERVICE_NAME = "EcoTP-Test";

    private final EcoTpPlugin plugin;
    private final File rsaFolder;
    private final File tokensFile;
    private final SecureRandom random = new SecureRandom();
    private PrivateKey privateKey;
    private Map<String, String> tokens;
    private ServerSocket serverSocket;
    private volatile boolean running;
    // 通信スレッドから config を読まないよう、start() 時点の値を覚えておく。
    private volatile int readTimeoutMillis = 15_000;
    private volatile boolean logConnections = true;
    private volatile String connectHost = "127.0.0.1";
    private volatile int port = 8192;
    // 同時接続数の上限と、1接続あたりの全体の制限時間 (少しずつ送ってくる接続でスレッドを
    // 使い切られるのを防ぐ。投票サイトの通常の送信は1秒もかからない)。
    private final java.util.concurrent.Semaphore connectionSlots = new java.util.concurrent.Semaphore(16);
    private final java.util.Timer watchdog = new java.util.Timer("EcoTP-Votifier-Watchdog", true);

    public VotifierServer(EcoTpPlugin plugin) {
        this.plugin = plugin;
        this.rsaFolder = new File(plugin.getDataFolder(), "votifier-rsa");
        this.tokensFile = new File(plugin.getDataFolder(), "votifier-tokens.yml");
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("votifier.enabled", true)) {
            return;
        }
        if (isCompatibleVotifierPluginPresent()) {
            // Both this listener and NuVotifier default to port 8192 - if NuVotifier is also
            // installed and wins the race to bind it, votes sent to what the voting sites think
            // is NuVotifier's listener land here instead, fail to decrypt (wrong keypair), and
            // are dropped - a real, silent way for "voting doesn't work" to happen. Stepping
            // aside automatically avoids that instead of relying on the admin to notice and set
            // votifier.enabled: false themselves; NuVotifier's own events still reach
            // VoteRewardManager via VoteRewardListener regardless.
            plugin.getLogger().info("A NuVotifier-compatible plugin is already installed - not starting "
                    + "the built-in Votifier-compatible listener (both default to port 8192; votes will "
                    + "be picked up via that plugin's own events instead).");
            return;
        }
        try {
            loadOrGenerateKeys();
            loadOrGenerateTokens();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Could not load/generate the built-in Votifier RSA keypair or protocol v2 token; the built-in vote listener is disabled.", e);
            return;
        }
        String host = plugin.getConfig().getString("votifier.host", "0.0.0.0");
        this.port = plugin.getConfig().getInt("votifier.port", 8192);
        this.readTimeoutMillis = Math.max(1, plugin.getConfig().getInt("votifier.read-timeout-seconds", 15)) * 1000;
        this.logConnections = plugin.getConfig().getBoolean("votifier.log-connections", true);
        this.connectHost = host.equals("0.0.0.0") || host.equals("::") ? "127.0.0.1" : host;
        String legacyToken = plugin.getConfig().getString("votifier.token");
        if (legacyToken != null && !legacyToken.isBlank()) {
            // 値は出さない。config.yml の votifier.token はどこからも使われていない。
            plugin.getLogger().warning("config.yml has votifier.token, but it is NOT used. The only protocol v2 token "
                    + "is tokens.default in " + tokensFile.getPath() + " - register THAT value with your voting sites "
                    + "and delete votifier.token from config.yml to avoid confusion.");
        }
        try {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress(host, port));
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not open the built-in Votifier listener on " + host + ":" + port
                    + " (already in use, e.g. by NuVotifier? set votifier.enabled: false in config.yml if so). "
                    + "The built-in vote listener is disabled.", e);
            serverSocket = null;
            return;
        }
        running = true;
        Thread acceptThread = new Thread(this::acceptLoop, "EcoTP-Votifier");
        acceptThread.setDaemon(true);
        acceptThread.start();
        plugin.getLogger().info("Built-in Votifier-compatible listener started on " + host + ":" + port
                + " (read timeout " + (readTimeoutMillis / 1000) + "s)");
        plugin.getLogger().info("[Votifier] protocol v1 key: " + new File(rsaFolder, "public.key").getPath()
                + " | protocol v2 token: " + tokensFile.getPath() + " -> tokens.default"
                + (tokens.size() > 1 ? " (per-site tokens also loaded: " + String.join(", ", tokens.keySet()) + ")" : ""));
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // もう閉じている
        }
    }

    public void stop() {
        running = false;
        watchdog.cancel();
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
                // Already closing down; nothing useful to do with this.
            }
        }
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                if (!connectionSlots.tryAcquire()) {
                    logConnection(socket, "-", Outcome.REJECTED_BUSY, null);
                    closeQuietly(socket);
                    continue;
                }
                AtomicBoolean killed = new AtomicBoolean();
                java.util.TimerTask kill = new java.util.TimerTask() {
                    @Override
                    public void run() {
                        killed.set(true);
                        closeQuietly(socket);
                    }
                };
                // 1接続あたりの全体の制限時間 (少しずつ送ってくる接続でスレッドを使い切られないように)。
                watchdog.schedule(kill, readTimeoutMillis() + 5_000L);
                Thread handler = new Thread(() -> {
                    try {
                        handleConnection(socket, killed);
                    } finally {
                        kill.cancel();
                        connectionSlots.release();
                    }
                }, "EcoTP-Votifier-Connection");
                handler.setDaemon(true);
                handler.start();
            } catch (IOException e) {
                if (running) {
                    plugin.getLogger().log(Level.WARNING, "Votifier listener accept failed", e);
                }
            }
        }
    }

    private int readTimeoutMillis() {
        return readTimeoutMillis;
    }

    /** 1接続の結果。接続ごとに1行ログに出す (以前は失敗のほとんどが FINE で見えなかった)。 */
    enum Outcome {
        SUCCESS(Level.INFO, "accepted"),
        CLOSED_BEFORE_DATA(Level.INFO, "closed before sending any data"),
        INCOMPLETE(Level.INFO, "closed in the middle of the message"),
        TIMEOUT(Level.WARNING, "timed out waiting for data (votifier.read-timeout-seconds)"),
        V1_DECRYPT_FAILED(Level.WARNING, "v1 decrypt failed - the site likely has the wrong public.key"),
        V1_NOT_A_VOTE(Level.WARNING, "v1 message decrypted but did not start with \"VOTE\""),
        V2_BAD_CHALLENGE(Level.WARNING, "v2 challenge mismatch (stale or replayed request)"),
        V2_BAD_SIGNATURE(Level.WARNING, "v2 signature mismatch - the site likely has the wrong token"),
        MALFORMED(Level.WARNING, "malformed message"),
        REJECTED_BUSY(Level.WARNING, "rejected: too many simultaneous connections");

        final Level level;
        final String text;

        Outcome(Level level, String text) {
            this.level = level;
            this.text = text;
        }
    }

    private void logConnection(Socket socket, String protocol, Outcome outcome, String detail) {
        if (!logConnections) {
            return;
        }
        String from = socket.getInetAddress() == null ? "?" : socket.getInetAddress().getHostAddress();
        plugin.getLogger().log(outcome.level, "[Votifier] connection from " + from + " protocol=" + protocol
                + " result=" + outcome.name() + " (" + outcome.text + ")" + (detail == null ? "" : " " + detail));
    }

    private void handleConnection(Socket socket, AtomicBoolean killed) {
        String protocol = "?";
        try (Socket s = socket) {
            s.setSoTimeout(readTimeoutMillis());
            OutputStream out = s.getOutputStream();
            String challenge = randomToken(130);
            out.write(("VOTIFIER 2 " + challenge + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();

            InputStream in = s.getInputStream();
            byte[] head = readFully(in, 2);
            if (head == null) {
                logConnection(socket, protocol, Outcome.CLOSED_BEFORE_DATA, null);
                return;
            }
            if (head[0] == PROTOCOL_2_MAGIC[0] && head[1] == PROTOCOL_2_MAGIC[1]) {
                protocol = "v2";
                handleProtocol2(socket, in, out, challenge);
            } else {
                protocol = "v1";
                byte[] rest = readFully(in, V1_BLOCK_SIZE - 2);
                if (rest == null) {
                    logConnection(socket, protocol, Outcome.INCOMPLETE, null);
                    return;
                }
                byte[] block = new byte[V1_BLOCK_SIZE];
                System.arraycopy(head, 0, block, 0, 2);
                System.arraycopy(rest, 0, block, 2, rest.length);
                handleProtocol1(socket, block);
            }
        } catch (SocketTimeoutException e) {
            logConnection(socket, protocol, Outcome.TIMEOUT, null);
        } catch (IOException e) {
            // 制限時間で watchdog が閉じた場合もここに来る。
            logConnection(socket, protocol, killed.get() ? Outcome.TIMEOUT : Outcome.INCOMPLETE,
                    killed.get() ? null : "(" + e.getClass().getSimpleName() + ")");
        }
    }

    private void handleProtocol1(Socket socket, byte[] block) {
        byte[] decrypted;
        try {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            decrypted = cipher.doFinal(block);
        } catch (Exception e) {
            logConnection(socket, "v1", Outcome.V1_DECRYPT_FAILED,
                    "expected key: " + new File(rsaFolder, "public.key").getPath());
            return;
        }

        String message = new String(decrypted, StandardCharsets.UTF_8);
        String[] parts = message.split("\n");
        if (parts.length < 3 || !parts[0].equals("VOTE")) {
            // 中身は投票ではないので、先頭の数文字だけ出す (全文は出さない)。
            String preview = message.length() > 16 ? message.substring(0, 16) + "..." : message;
            logConnection(socket, "v1", Outcome.V1_NOT_A_VOTE, "starts with: \"" + preview.replace("\n", "\\n") + "\"");
            return;
        }
        String serviceName = parts[1];
        String username = parts[2];
        String address = parts.length > 3 ? parts[3] : "";
        String timestamp = parts.length > 4 ? parts[4] : "";
        accept(socket, "v1", serviceName, username, address, timestamp);
    }

    private void handleProtocol2(Socket socket, InputStream in, OutputStream out, String challenge) throws IOException {
        byte[] lengthBytes = readFully(in, 2);
        if (lengthBytes == null) {
            logConnection(socket, "v2", Outcome.INCOMPLETE, null);
            return;
        }
        int length = ((lengthBytes[0] & 0xFF) << 8) | (lengthBytes[1] & 0xFF);
        if (length <= 0 || length > V2_MAX_MESSAGE_LENGTH) {
            logConnection(socket, "v2", Outcome.MALFORMED, "(length " + length + ")");
            writeProtocol2Error(out, "Message too large");
            return;
        }
        byte[] body = readFully(in, length);
        if (body == null) {
            logConnection(socket, "v2", Outcome.INCOMPLETE, null);
            return;
        }
        String message = new String(body, StandardCharsets.UTF_8);
        try {
            JsonObject envelope = JsonParser.parseString(message).getAsJsonObject();
            String payload = envelope.get("payload").getAsString();
            String signature = envelope.get("signature").getAsString();

            JsonObject votePayload = JsonParser.parseString(payload).getAsJsonObject();
            String voteChallenge = votePayload.get("challenge").getAsString();
            if (!challenge.equals(voteChallenge)) {
                logConnection(socket, "v2", Outcome.V2_BAD_CHALLENGE, null);
                writeProtocol2Error(out, "Invalid challenge");
                return;
            }
            String serviceName = votePayload.get("serviceName").getAsString();
            String username = votePayload.get("username").getAsString();
            String address = votePayload.has("address") ? votePayload.get("address").getAsString() : "";
            String timestamp = votePayload.has("timestamp") ? votePayload.get("timestamp").getAsString() : "";

            String tokenKey = tokens.containsKey(serviceName) ? serviceName : "default";
            String token = tokens.get(tokenKey);
            if (token == null || !signatureValid(token, payload, signature)) {
                logConnection(socket, "v2", Outcome.V2_BAD_SIGNATURE, "service=" + serviceName
                        + " checked against " + tokensFile.getName() + " tokens." + tokenKey);
                writeProtocol2Error(out, "Invalid signature");
                return;
            }

            accept(socket, "v2", serviceName, username, address, timestamp);

            JsonObject ok = new JsonObject();
            ok.addProperty("status", "ok");
            out.write((ok + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (JsonSyntaxException | IllegalStateException | NullPointerException | UnsupportedOperationException e) {
            logConnection(socket, "v2", Outcome.MALFORMED, "(" + e.getClass().getSimpleName() + ")");
            writeProtocol2Error(out, "Malformed message");
        }
    }

    /** 正しい投票を受け付けた: ログを出し、メインスレッドでイベントを発火してから報酬処理へ回す。 */
    private void accept(Socket socket, String protocol, String serviceName, String username,
                        String address, String timestamp) {
        boolean test = TEST_SERVICE_NAME.equals(serviceName);
        boolean rewardsOn = plugin.getVoteRewardManager().isEnabled();
        logConnection(socket, protocol, Outcome.SUCCESS, "service=" + serviceName + " user=" + username
                + (test ? " (EcoTP test vote: not counted or rewarded)"
                        : rewardsOn ? "" : " (vote-reward.enabled is false: no money paid)"));
        if (test) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.getPluginManager().callEvent(new EcoTpVoteEvent(serviceName, username, address, timestamp, protocol));
            plugin.getVoteRewardManager().handleVote(username, serviceName);
        });
    }

    public boolean isRunning() {
        return running;
    }

    /** /ecotp votetest で送る内容。 */
    public enum TestMode {
        V1, V2, V1_BADKEY, V2_BADTOKEN, EMPTY, SLOW;

        public static TestMode parse(String text) {
            return switch (text.toLowerCase()) {
                case "v1" -> V1;
                case "v2" -> V2;
                case "v1-badkey" -> V1_BADKEY;
                case "v2-badtoken" -> V2_BADTOKEN;
                case "empty" -> EMPTY;
                case "slow" -> SLOW;
                default -> null;
            };
        }
    }

    /**
     * 自分自身の受け口 (ループバック) に、投票サイトと同じ形のパケットを実際に送る。
     * 通信は別スレッドで行い、結果の説明文を report にメインスレッドで渡す。
     * real が false なら、受け口側はサービス名 EcoTP-Test を見て数えず報酬も出さない。
     */
    public void sendTestVote(String username, TestMode mode, boolean real, java.util.function.Consumer<String> report) {
        Thread thread = new Thread(() -> {
            String result;
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(connectHost, port), 3000);
                socket.setSoTimeout(readTimeoutMillis + 10_000);
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                String greeting = readLine(in);
                String[] parts = greeting == null ? new String[0] : greeting.trim().split(" ");
                String challenge = parts.length >= 3 ? parts[2] : "";
                String service = real ? TEST_SERVICE_NAME + "-Real" : TEST_SERVICE_NAME;
                switch (mode) {
                    case EMPTY -> result = "connected and closed without sending anything";
                    case SLOW -> {
                        Thread.sleep(readTimeoutMillis + 2_000L);
                        try {
                            writeV2(out, service, username, challenge, tokens.get("default"));
                            result = "waited " + (readTimeoutMillis / 1000 + 2) + "s, then sent a v2 vote; reply: " + readLine(in);
                        } catch (IOException e) {
                            result = "waited " + (readTimeoutMillis / 1000 + 2) + "s; the server had already closed the connection";
                        }
                    }
                    case V1, V1_BADKEY -> {
                        PublicKey key = mode == TestMode.V1 ? loadPublicKey() : newKeyPair().getPublic();
                        String message = "VOTE\n" + service + "\n" + username + "\n127.0.0.1\n"
                                + (System.currentTimeMillis() / 1000L) + "\n";
                        Cipher cipher = Cipher.getInstance("RSA");
                        cipher.init(Cipher.ENCRYPT_MODE, key);
                        out.write(cipher.doFinal(message.getBytes(StandardCharsets.UTF_8)));
                        out.flush();
                        result = "sent a 256-byte v1 block" + (mode == TestMode.V1_BADKEY ? " encrypted with a WRONG key" : "");
                    }
                    case V2, V2_BADTOKEN -> {
                        String token = mode == TestMode.V2 ? tokens.get("default") : "wrong-" + randomToken(64);
                        writeV2(out, service, username, challenge, token);
                        result = "sent a v2 vote" + (mode == TestMode.V2_BADTOKEN ? " signed with a WRONG token" : "")
                                + "; reply: " + readLine(in);
                    }
                    default -> result = "unknown mode";
                }
            } catch (Exception e) {
                result = "error: " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " " + e.getMessage());
            }
            String finalResult = result;
            Bukkit.getScheduler().runTask(plugin, () -> report.accept(finalResult));
        }, "EcoTP-Votifier-Test");
        thread.setDaemon(true);
        thread.start();
    }

    private void writeV2(OutputStream out, String service, String username, String challenge, String token) throws Exception {
        JsonObject payload = new JsonObject();
        payload.addProperty("serviceName", service);
        payload.addProperty("username", username);
        payload.addProperty("address", "127.0.0.1");
        payload.addProperty("timestamp", System.currentTimeMillis());
        payload.addProperty("challenge", challenge);
        String payloadText = payload.toString();
        JsonObject envelope = new JsonObject();
        envelope.addProperty("payload", payloadText);
        envelope.addProperty("signature", Base64.getEncoder().encodeToString(hmacSha256(token, payloadText)));
        byte[] body = envelope.toString().getBytes(StandardCharsets.UTF_8);
        out.write(PROTOCOL_2_MAGIC);
        out.write((body.length >> 8) & 0xFF);
        out.write(body.length & 0xFF);
        out.write(body);
        out.flush();
    }

    private PublicKey loadPublicKey() throws Exception {
        byte[] bytes = Base64.getDecoder().decode(readBase64(new File(rsaFolder, "public.key")));
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bytes));
    }

    private static KeyPair newKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder line = new StringBuilder();
        int b;
        while ((b = in.read()) >= 0 && b != '\n' && line.length() < 512) {
            if (b != '\r') {
                line.append((char) b);
            }
        }
        return b < 0 && line.isEmpty() ? null : line.toString();
    }

    private void writeProtocol2Error(OutputStream out, String reason) throws IOException {
        JsonObject error = new JsonObject();
        error.addProperty("status", "error");
        error.addProperty("error", reason);
        out.write((error + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private boolean signatureValid(String token, String payload, String signatureBase64) {
        try {
            byte[] expected = hmacSha256(token, payload);
            byte[] provided = Base64.getDecoder().decode(signatureBase64);
            return MessageDigest.isEqual(expected, provided);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] hmacSha256(String token, String message) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
    }

    private String randomToken(int bits) {
        return new BigInteger(bits, random).toString(32);
    }

    private static boolean isCompatibleVotifierPluginPresent() {
        try {
            Class.forName("com.vexsoftware.votifier.model.VotifierEvent");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] buffer = new byte[length];
        int total = 0;
        while (total < length) {
            int read = in.read(buffer, total, length - total);
            if (read < 0) {
                return null;
            }
            total += read;
        }
        return buffer;
    }

    private void loadOrGenerateKeys() throws Exception {
        File publicFile = new File(rsaFolder, "public.key");
        File privateFile = new File(rsaFolder, "private.key");
        if (publicFile.exists() && privateFile.exists()) {
            byte[] publicBytes = Base64.getDecoder().decode(readBase64(publicFile));
            byte[] privateBytes = Base64.getDecoder().decode(readBase64(privateFile));
            KeyFactory factory = KeyFactory.getInstance("RSA");
            factory.generatePublic(new X509EncodedKeySpec(publicBytes)); // validates the file before trusting it
            this.privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(privateBytes));
            return;
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        PublicKey publicKey = pair.getPublic();
        this.privateKey = pair.getPrivate();
        rsaFolder.mkdirs();
        Files.write(publicFile.toPath(), Base64.getEncoder().encode(publicKey.getEncoded()));
        Files.write(privateFile.toPath(), Base64.getEncoder().encode(privateKey.getEncoded()));
    }

    /** tokens.<名前> = 投票サイトに渡す共有トークン。tokens.default が無ければ新規生成して保存する。 */
    private void loadOrGenerateTokens() throws IOException {
        YamlConfiguration data = YamlIo.load(tokensFile);
        Map<String, String> loaded = new LinkedHashMap<>();
        ConfigurationSection section = data.getConfigurationSection("tokens");
        if (section != null) {
            for (String name : section.getKeys(false)) {
                String value = section.getString(name);
                if (value != null && !value.isBlank()) {
                    loaded.put(name, value);
                }
            }
        }
        if (!loaded.containsKey("default")) {
            loaded.put("default", randomToken(256));
            YamlConfiguration toSave = new YamlConfiguration();
            for (Map.Entry<String, String> entry : loaded.entrySet()) {
                toSave.set("tokens." + entry.getKey(), entry.getValue());
            }
            YamlIo.save(toSave, tokensFile);
        }
        this.tokens = loaded;
    }

    private static String readBase64(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).replaceAll("\\s", "");
    }
}
