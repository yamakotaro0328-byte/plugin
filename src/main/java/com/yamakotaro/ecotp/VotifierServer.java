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

    private final EcoTpPlugin plugin;
    private final File rsaFolder;
    private final File tokensFile;
    private final SecureRandom random = new SecureRandom();
    private PrivateKey privateKey;
    private Map<String, String> tokens;
    private ServerSocket serverSocket;
    private volatile boolean running;

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
        int port = plugin.getConfig().getInt("votifier.port", 8192);
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
                + " - protocol v1 (RSA key) voting sites: give them the contents of "
                + new File(rsaFolder, "public.key").getPath()
                + " - protocol v2 (token) voting sites: give them the tokens.default value from "
                + tokensFile.getPath());
    }

    public void stop() {
        running = false;
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
                Thread handler = new Thread(() -> handleConnection(socket), "EcoTP-Votifier-Connection");
                handler.setDaemon(true);
                handler.start();
            } catch (IOException e) {
                if (running) {
                    plugin.getLogger().log(Level.WARNING, "Votifier listener accept failed", e);
                }
            }
        }
    }

    private void handleConnection(Socket socket) {
        try (Socket s = socket) {
            s.setSoTimeout(5000);
            OutputStream out = s.getOutputStream();
            String challenge = randomToken(130);
            out.write(("VOTIFIER 2 " + challenge + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();

            InputStream in = s.getInputStream();
            byte[] head = readFully(in, 2);
            if (head == null) {
                return;
            }
            if (head[0] == PROTOCOL_2_MAGIC[0] && head[1] == PROTOCOL_2_MAGIC[1]) {
                handleProtocol2(in, out, challenge);
            } else {
                byte[] rest = readFully(in, V1_BLOCK_SIZE - 2);
                if (rest == null) {
                    return;
                }
                byte[] block = new byte[V1_BLOCK_SIZE];
                System.arraycopy(head, 0, block, 0, 2);
                System.arraycopy(rest, 0, block, 2, rest.length);
                handleProtocol1(block);
            }
        } catch (IOException e) {
            // The connection dropped before sending a full message - expected occasionally on a
            // public port (scanners, health checks); nothing actionable here.
            plugin.getLogger().log(Level.FINE, "Ignoring an incomplete Votifier connection", e);
        }
    }

    private void handleProtocol1(byte[] block) {
        byte[] decrypted;
        try {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            decrypted = cipher.doFinal(block);
        } catch (Exception e) {
            // A well-formed 256-byte block that fails to decrypt with THIS keypair almost always
            // means the voting site was given the wrong public.key - a real misconfiguration, not
            // scanner noise, so this needs to be visible by default rather than buried at FINE.
            plugin.getLogger().log(Level.WARNING, "Received a protocol v1 vote that failed to decrypt - the voting "
                    + "site is likely using the wrong public key (should be the contents of "
                    + new File(rsaFolder, "public.key").getPath() + ")", e);
            return;
        }

        String message = new String(decrypted, StandardCharsets.UTF_8);
        String[] parts = message.split("\n");
        if (parts.length < 3 || !parts[0].equals("VOTE")) {
            plugin.getLogger().log(Level.FINE, "Ignoring a decrypted Votifier message that wasn't a vote: " + message);
            return;
        }
        String serviceName = parts[1];
        String username = parts[2];
        Bukkit.getScheduler().runTask(plugin, () -> plugin.getVoteRewardManager().handleVote(username, serviceName));
    }

    private void handleProtocol2(InputStream in, OutputStream out, String challenge) throws IOException {
        byte[] lengthBytes = readFully(in, 2);
        if (lengthBytes == null) {
            return;
        }
        int length = ((lengthBytes[0] & 0xFF) << 8) | (lengthBytes[1] & 0xFF);
        if (length <= 0 || length > V2_MAX_MESSAGE_LENGTH) {
            writeProtocol2Error(out, "Message too large");
            return;
        }
        byte[] body = readFully(in, length);
        if (body == null) {
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
                plugin.getLogger().warning("Received a protocol v2 vote with an invalid challenge "
                        + "(the voting site's request may be stale, or this could be a replay attempt).");
                writeProtocol2Error(out, "Invalid challenge");
                return;
            }
            String serviceName = votePayload.get("serviceName").getAsString();
            String username = votePayload.get("username").getAsString();

            String token = tokens.get(serviceName);
            if (token == null) {
                token = tokens.get("default");
            }
            if (token == null || !signatureValid(token, payload, signature)) {
                plugin.getLogger().warning("Received a protocol v2 vote that failed signature verification - the "
                        + "voting site is likely using the wrong token (should match one of the entries in "
                        + tokensFile.getPath() + ")");
                writeProtocol2Error(out, "Invalid signature");
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> plugin.getVoteRewardManager().handleVote(username, serviceName));

            JsonObject ok = new JsonObject();
            ok.addProperty("status", "ok");
            out.write((ok + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (JsonSyntaxException | IllegalStateException | NullPointerException e) {
            plugin.getLogger().log(Level.WARNING, "Received a malformed protocol v2 vote message", e);
            writeProtocol2Error(out, "Malformed message");
        }
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
