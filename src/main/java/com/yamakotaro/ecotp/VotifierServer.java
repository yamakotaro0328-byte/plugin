package com.yamakotaro.ecotp;

import org.bukkit.Bukkit;

import javax.crypto.Cipher;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.logging.Level;

/**
 * 古典的なVotifier(V1)プロトコル互換のサーバーをEcoTP自身に内蔵し、NuVotifier等の
 * 別プラグインを一切導入せずに投票報酬を使えるようにする。投票サイト側には
 * votifier-rsa/public.key の中身(Base64)をそのまま貼り付けてもらうだけでよい。
 * NuVotifierを別途使いたい場合はvotifier.enabled: falseにすれば、VoteRewardListener
 * (リフレクションでNuVotifierのイベントを拾う側)だけが動く。
 */
public class VotifierServer {

    private final EcoTpPlugin plugin;
    private final File rsaFolder;
    private PrivateKey privateKey;
    private ServerSocket serverSocket;
    private volatile boolean running;

    public VotifierServer(EcoTpPlugin plugin) {
        this.plugin = plugin;
        this.rsaFolder = new File(plugin.getDataFolder(), "votifier-rsa");
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("votifier.enabled", true)) {
            return;
        }
        try {
            loadOrGenerateKeys();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Could not load/generate the built-in Votifier RSA keypair; the built-in vote listener is disabled.", e);
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
                + " - give voting sites the contents of " + new File(rsaFolder, "public.key").getPath());
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
            out.write("VOTIFIER 1.9\n".getBytes(StandardCharsets.UTF_8));
            out.flush();

            InputStream in = s.getInputStream();
            byte[] block = readFully(in, 256);
            if (block == null) {
                return;
            }
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decrypted = cipher.doFinal(block);
            String message = new String(decrypted, StandardCharsets.UTF_8);
            String[] parts = message.split("\n");
            if (parts.length < 3 || !parts[0].equals("VOTE")) {
                return;
            }
            String serviceName = parts[1];
            String username = parts[2];
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getVoteRewardManager().handleVote(username, serviceName));
        } catch (Exception e) {
            // Malformed/garbage connections (scanners, misconfigured clients, etc.) are expected
            // occasionally on a public port; drop them without spamming the console.
            plugin.getLogger().log(Level.FINE, "Ignoring an invalid Votifier connection", e);
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

    private static String readBase64(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).replaceAll("\\s", "");
    }
}
