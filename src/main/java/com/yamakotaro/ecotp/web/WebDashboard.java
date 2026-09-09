package com.yamakotaro.ecotp.web;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.yamakotaro.ecotp.BalanceEntry;
import com.yamakotaro.ecotp.BalanceStorage;
import com.yamakotaro.ecotp.EcoTpEconomy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A small self-contained economy panel: http://&lt;host&gt;:&lt;port&gt;/ - browsing balances and
 * the leaderboard needs no login, but giving/taking/setting a player's balance requires signing
 * in with one of the named accounts from config.yml first. Only started when EcoTP's own
 * built-in economy is active (economy.enabled: true) - with an external Vault economy plugin
 * instead, EcoTP has no balances of its own to show (see EcoTpPlugin#onEnable).
 *
 * Deliberately its own copy of the same design as ecoban-core's WebDashboard (JDK HttpServer,
 * cookie session auth, per-IP login lockout, named accounts) rather than a shared dependency -
 * EcoTP and EcoBan are independent plugins/modules with no relationship to each other.
 */
public class WebDashboard {

    private static final long SESSION_LIFETIME_MILLIS = 12L * 60 * 60 * 1000; // 12 hours
    private static final String SESSION_COOKIE = "ecotp_session";
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final long LOGIN_LOCKOUT_MILLIS = 5L * 60 * 1000; // 5 minutes
    private static final long MAIN_THREAD_TIMEOUT_SECONDS = 5;

    private final JavaPlugin plugin;
    private final EcoTpEconomy ecoTpEconomy;
    private final BalanceStorage balances;
    private final int port;
    private final Map<String, String> accounts;
    private final Logger logger;
    private final Gson gson = new Gson();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, LoginAttempt> loginAttempts = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    private HttpServer server;

    /** @param accounts username -> password for every login the dashboard should accept. */
    public WebDashboard(JavaPlugin plugin, EcoTpEconomy ecoTpEconomy, BalanceStorage balances,
                         int port, Map<String, String> accounts, Logger logger) {
        this.plugin = plugin;
        this.ecoTpEconomy = ecoTpEconomy;
        this.balances = balances;
        this.port = port;
        this.accounts = accounts;
        this.logger = logger;
    }

    private record Session(String username, long expiresAtMillis) {
    }

    private record LoginAttempt(int failures, long lockedUntilMillis) {
    }

    private record EcoActionResult(boolean success, String message, double newBalance) {
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to start the EcoTP web dashboard on port " + port
                    + " (is something else already using it?)", e);
            return;
        }
        server.setExecutor(Executors.newCachedThreadPool());

        server.createContext("/", exchange -> serveStatic(exchange, "/web/index.html", "text/html; charset=utf-8"));
        server.createContext("/app.js", exchange -> serveStatic(exchange, "/web/app.js", "application/javascript; charset=utf-8"));
        server.createContext("/app.css", exchange -> serveStatic(exchange, "/web/app.css", "text/css; charset=utf-8"));

        server.createContext("/api/login", this::handleLogin);
        server.createContext("/api/logout", this::handleLogout);
        server.createContext("/api/session", authed(this::handleSession));
        // Browsing balances is public - only give/take/set requires login.
        server.createContext("/api/stats", this::handleStats);
        server.createContext("/api/baltop", this::handleBaltop);
        server.createContext("/api/player", this::handlePlayer);
        server.createContext("/api/eco", authed(this::handleEco));

        server.start();
        logger.info("EcoTP web dashboard listening on http://0.0.0.0:" + port + "/");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    // ---- static assets ----

    private void serveStatic(HttpExchange exchange, String resourcePath, String contentType) throws IOException {
        try (InputStream in = WebDashboard.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                respond(exchange, 404, "text/plain", "Not found");
                return;
            }
            byte[] body = in.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
    }

    // ---- auth ----

    private HttpHandler authed(AuthedHandler handler) {
        return exchange -> {
            if (session(exchange) == null) {
                respondJson(exchange, 401, error("Not logged in"));
                return;
            }
            handler.handle(exchange);
        };
    }

    private Session session(HttpExchange exchange) {
        String token = readCookie(exchange, SESSION_COOKIE);
        if (token == null) {
            return null;
        }
        Session session = sessions.get(token);
        if (session == null) {
            return null;
        }
        if (session.expiresAtMillis() < System.currentTimeMillis()) {
            sessions.remove(token);
            return null;
        }
        return session;
    }

    private void handleLogin(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            respondJson(exchange, 405, error("Use POST"));
            return;
        }
        String clientIp = clientIp(exchange);
        long now = System.currentTimeMillis();
        LoginAttempt attempt = loginAttempts.get(clientIp);
        if (attempt != null && attempt.lockedUntilMillis() > now) {
            long secondsLeft = (attempt.lockedUntilMillis() - now) / 1000 + 1;
            respondJson(exchange, 429, error("Too many failed attempts - try again in " + secondsLeft + "s"));
            return;
        }

        JsonObject body = readJson(exchange);
        String givenUser = body.has("username") ? body.get("username").getAsString() : "";
        String givenPass = body.has("password") ? body.get("password").getAsString() : "";
        String expectedPassword = accounts.get(givenUser);
        if (expectedPassword == null || !constantTimeEquals(givenPass, expectedPassword)) {
            int failures = (attempt != null ? attempt.failures() : 0) + 1;
            long lockedUntil = failures >= MAX_LOGIN_ATTEMPTS ? now + LOGIN_LOCKOUT_MILLIS : 0;
            loginAttempts.put(clientIp, new LoginAttempt(failures, lockedUntil));
            respondJson(exchange, 401, error("Invalid username or password"));
            return;
        }

        loginAttempts.remove(clientIp);
        String token = newToken();
        sessions.put(token, new Session(givenUser, now + SESSION_LIFETIME_MILLIS));
        exchange.getResponseHeaders().add("Set-Cookie", SESSION_COOKIE + "=" + token + "; Path=/; HttpOnly; SameSite=Strict");
        respondJson(exchange, 200, okObject());
    }

    private String clientIp(HttpExchange exchange) {
        InetSocketAddress remote = exchange.getRemoteAddress();
        return remote != null && remote.getAddress() != null ? remote.getAddress().getHostAddress() : "unknown";
    }

    private void handleLogout(HttpExchange exchange) throws IOException {
        String token = readCookie(exchange, SESSION_COOKIE);
        if (token != null) {
            sessions.remove(token);
        }
        respondJson(exchange, 200, okObject());
    }

    /** Lets the page ask "am I still logged in, and as whom?" without side effects. */
    private void handleSession(HttpExchange exchange) throws IOException {
        JsonObject json = okObject();
        json.addProperty("username", session(exchange).username());
        respondJson(exchange, 200, json);
    }

    // ---- read endpoints ----

    private void handleStats(HttpExchange exchange) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("totalMoney", balances.totalMoney());
        json.addProperty("accountCount", balances.countAccounts());
        json.addProperty("currencyPlural", ecoTpEconomy.currencyNamePlural());
        json.addProperty("currencySingular", ecoTpEconomy.currencyNameSingular());
        json.add("topPreview", gson.toJsonTree(balances.getTopBalances(5).stream().map(this::toEntryDto).toList()));
        respondJson(exchange, 200, json);
    }

    private void handleBaltop(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        int limit = Math.min(100, Math.max(1, parseIntOr(query.get("limit"), 50)));
        List<BalanceEntry> entries = balances.getTopBalances(limit);
        respondJson(exchange, 200, gson.toJsonTree(entries.stream().map(this::toEntryDto).toList()));
    }

    private void handlePlayer(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String name = query.get("name");
        if (name == null || name.isBlank()) {
            respondJson(exchange, 400, error("Missing name"));
            return;
        }
        Optional<UUID> uuid = balances.findUuidByName(name);
        if (uuid.isEmpty()) {
            respondJson(exchange, 404, error("No account for that name"));
            return;
        }
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("uuid", uuid.get().toString());
        json.addProperty("balance", balances.getBalance(uuid.get()));
        respondJson(exchange, 200, json);
    }

    // ---- write endpoint ----

    /** {name, action: "give"|"take"|"set", amount} - the same three actions and semantics as
     * /eco, applied on the main thread since it goes through the same Vault-facing EcoTpEconomy
     * calls any other plugin listening for economy changes would expect to see fired there. */
    private void handleEco(HttpExchange exchange) throws IOException {
        JsonObject body = readJson(exchange);
        String name = getOrNull(body, "name");
        String action = getOrNull(body, "action");
        Double amount = getDoubleOrNull(body, "amount");
        if (name == null || name.isBlank() || action == null || amount == null || amount < 0) {
            respondJson(exchange, 400, error("Missing or invalid name/action/amount"));
            return;
        }

        EcoActionResult result;
        try {
            result = runOnMainThread(() -> applyEcoAction(name, action, amount));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Web dashboard eco action failed", e);
            respondJson(exchange, 500, error("Failed to apply the change"));
            return;
        }
        if (!result.success()) {
            respondJson(exchange, 400, error(result.message() != null ? result.message() : "Failed"));
            return;
        }
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("balance", result.newBalance());
        respondJson(exchange, 200, json);
    }

    private EcoActionResult applyEcoAction(String name, String action, double amount) {
        double current = ecoTpEconomy.getBalance(name);
        return switch (action) {
            case "give" -> fromResponse(ecoTpEconomy.depositPlayer(name, amount));
            case "take" -> fromResponse(ecoTpEconomy.withdrawPlayer(name, amount));
            case "set" -> {
                if (amount > current) {
                    yield fromResponse(ecoTpEconomy.depositPlayer(name, amount - current));
                } else if (amount < current) {
                    yield fromResponse(ecoTpEconomy.withdrawPlayer(name, current - amount));
                }
                yield new EcoActionResult(true, null, current);
            }
            default -> new EcoActionResult(false, "Unknown action: " + action, current);
        };
    }

    private EcoActionResult fromResponse(EconomyResponse response) {
        return new EcoActionResult(response.transactionSuccess(), response.errorMessage, response.balance);
    }

    /** Runs a task on the main server thread and blocks this (HTTP handler) thread for the
     * result - balance changes go through the same thread every other plugin's economy listeners
     * expect, instead of racing the main thread from an HTTP executor thread. */
    private <T> T runOnMainThread(Callable<T> task) throws Exception {
        try {
            return Bukkit.getScheduler().callSyncMethod(plugin, task).get(MAIN_THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Timed out waiting for the main thread", e);
        }
    }

    // ---- DTO/JSON helpers ----

    private JsonObject toEntryDto(BalanceEntry entry) {
        JsonObject json = new JsonObject();
        json.addProperty("name", entry.name());
        json.addProperty("balance", entry.balance());
        return json;
    }

    private JsonObject error(String message) {
        JsonObject json = new JsonObject();
        json.addProperty("error", message);
        return json;
    }

    private JsonObject okObject() {
        JsonObject json = new JsonObject();
        json.addProperty("ok", true);
        return json;
    }

    private JsonObject readJson(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            in.transferTo(buffer);
            String body = buffer.toString(StandardCharsets.UTF_8);
            if (body.isBlank()) {
                return new JsonObject();
            }
            return gson.fromJson(body, JsonObject.class);
        }
    }

    private void respondJson(HttpExchange exchange, int status, JsonElement body) throws IOException {
        respond(exchange, status, "application/json; charset=utf-8", gson.toJson(body));
    }

    private void respond(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private String readCookie(HttpExchange exchange, String name) {
        List<String> cookieHeaders = exchange.getRequestHeaders().get("Cookie");
        if (cookieHeaders == null) {
            return null;
        }
        for (String header : cookieHeaders) {
            for (String part : header.split(";")) {
                String trimmed = part.trim();
                if (trimmed.startsWith(name + "=")) {
                    return trimmed.substring(name.length() + 1);
                }
            }
        }
        return null;
    }

    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> result = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return result;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            result.put(key, value);
        }
        return result;
    }

    private String getOrNull(JsonObject body, String key) {
        return body.has(key) && !body.get(key).isJsonNull() ? body.get(key).getAsString() : null;
    }

    private Double getDoubleOrNull(JsonObject body, String key) {
        try {
            return body.has(key) && !body.get(key).isJsonNull() ? body.get(key).getAsDouble() : null;
        } catch (NumberFormatException | UnsupportedOperationException e) {
            return null;
        }
    }

    private int parseIntOr(String raw, int fallback) {
        try {
            return raw == null ? fallback : Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        int diff = aBytes.length ^ bBytes.length;
        for (int i = 0; i < Math.max(aBytes.length, bBytes.length); i++) {
            byte aByte = i < aBytes.length ? aBytes[i] : 0;
            byte bByte = i < bBytes.length ? bBytes[i] : 0;
            diff |= aByte ^ bByte;
        }
        return diff == 0;
    }

    @FunctionalInterface
    private interface AuthedHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
