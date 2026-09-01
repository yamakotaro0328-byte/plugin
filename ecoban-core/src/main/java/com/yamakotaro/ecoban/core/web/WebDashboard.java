package com.yamakotaro.ecoban.core.web;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.yamakotaro.ecoban.core.Punishment;
import com.yamakotaro.ecoban.core.PunishmentManager;
import com.yamakotaro.ecoban.core.PunishmentType;

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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A small self-contained panel: http://&lt;host&gt;:&lt;port&gt;/ - browsing/searching
 * punishments needs no login (anyone with the link can look), but issuing or lifting one requires
 * signing in with the username/password from config.yml first, via the login button in the
 * corner. Built on the JDK's own {@link HttpServer} (no extra web-framework dependency to shade),
 * serving a single bundled HTML/CSS/JS page (see src/main/resources/web/) plus a small JSON REST
 * API.
 *
 * Session auth is a random token in a cookie, checked on every write /api/* route (see authed()
 * below) - intentionally simple (one shared admin account) rather than a full user system, since
 * write access is meant for a handful of trusted staff even though read access is open.
 */
public class WebDashboard {

    private static final long SESSION_LIFETIME_MILLIS = 12L * 60 * 60 * 1000; // 12 hours
    private static final String SESSION_COOKIE = "ecoban_session";

    private final PunishmentManager punishmentManager;
    private final int port;
    private final String username;
    private final String password;
    private final Logger logger;
    private final Gson gson = new Gson();
    private final Map<String, Long> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    private HttpServer server;

    public WebDashboard(PunishmentManager punishmentManager, int port, String username, String password, Logger logger) {
        this.punishmentManager = punishmentManager;
        this.port = port;
        this.username = username;
        this.password = password;
        this.logger = logger;
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to start the EcoBan web dashboard on port " + port
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
        // Browsing punishment records is public - only issuing/lifting one requires login.
        server.createContext("/api/punishments", this::handlePunishments);
        server.createContext("/api/history", this::handleHistory);
        server.createContext("/api/ban", authed(this::handleBan));
        server.createContext("/api/ipban", authed(this::handleIpban));
        server.createContext("/api/mute", authed(this::handleMute));
        server.createContext("/api/kick", authed(this::handleKick));
        server.createContext("/api/warn", authed(this::handleWarn));
        server.createContext("/api/unban", authed(this::handleUnban));
        server.createContext("/api/unbanip", authed(this::handleUnbanIp));
        server.createContext("/api/unmute", authed(this::handleUnmute));

        server.start();
        logger.info("EcoBan web dashboard listening on http://0.0.0.0:" + port + "/");
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
            String token = readCookie(exchange, SESSION_COOKIE);
            Long expiry = token != null ? sessions.get(token) : null;
            if (expiry == null || expiry < System.currentTimeMillis()) {
                if (token != null) {
                    sessions.remove(token);
                }
                respondJson(exchange, 401, error("Not logged in"));
                return;
            }
            handler.handle(exchange);
        };
    }

    private void handleLogin(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            respondJson(exchange, 405, error("Use POST"));
            return;
        }
        JsonObject body = readJson(exchange);
        String givenUser = body.has("username") ? body.get("username").getAsString() : "";
        String givenPass = body.has("password") ? body.get("password").getAsString() : "";
        if (!constantTimeEquals(givenUser, username) || !constantTimeEquals(givenPass, password)) {
            respondJson(exchange, 401, error("Invalid username or password"));
            return;
        }
        String token = newToken();
        sessions.put(token, System.currentTimeMillis() + SESSION_LIFETIME_MILLIS);
        exchange.getResponseHeaders().add("Set-Cookie", SESSION_COOKIE + "=" + token + "; Path=/; HttpOnly; SameSite=Strict");
        respondJson(exchange, 200, okObject());
    }

    private void handleLogout(HttpExchange exchange) throws IOException {
        String token = readCookie(exchange, SESSION_COOKIE);
        if (token != null) {
            sessions.remove(token);
        }
        respondJson(exchange, 200, okObject());
    }

    /** Lets the page ask "am I still logged in?" (e.g. after a reload) without side effects. */
    private void handleSession(HttpExchange exchange) throws IOException {
        respondJson(exchange, 200, okObject());
    }

    // ---- read endpoints ----

    private void handlePunishments(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String search = query.get("q");
        int limit = parseIntOr(query.get("limit"), 100);
        List<Punishment> results;
        if (search != null && !search.isBlank()) {
            results = punishmentManager.search(search, limit);
        } else {
            PunishmentType type = query.containsKey("type") ? parseTypeOrNull(query.get("type")) : null;
            results = punishmentManager.listActive(type, limit);
        }
        respondJson(exchange, 200, gson.toJsonTree(results.stream().map(this::toDto).toList()));
    }

    private void handleHistory(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        UUID uuid = parseUuidOrNull(query.get("uuid"));
        if (uuid == null) {
            respondJson(exchange, 400, error("Missing or invalid uuid"));
            return;
        }
        List<Punishment> results = punishmentManager.history(uuid);
        respondJson(exchange, 200, gson.toJsonTree(results.stream().map(this::toDto).toList()));
    }

    // ---- write endpoints ----

    private void handleBan(HttpExchange exchange) throws IOException {
        JsonObject body = readJson(exchange);
        UUID uuid = parseUuidOrNull(getOrNull(body, "uuid"));
        if (uuid == null) {
            respondJson(exchange, 400, error("Missing or invalid uuid"));
            return;
        }
        Punishment result = punishmentManager.ban(uuid, getOrNull(body, "name"), getOrNull(body, "reason"),
                operatorName(), getLongOr(body, "durationMillis", 0));
        respondJson(exchange, 200, toDto(result));
    }

    private void handleIpban(HttpExchange exchange) throws IOException {
        JsonObject body = readJson(exchange);
        String ip = getOrNull(body, "ip");
        if (ip == null || ip.isBlank()) {
            respondJson(exchange, 400, error("Missing ip"));
            return;
        }
        Punishment result = punishmentManager.ipban(ip, getOrNull(body, "name"), getOrNull(body, "reason"), operatorName());
        respondJson(exchange, 200, toDto(result));
    }

    private void handleMute(HttpExchange exchange) throws IOException {
        JsonObject body = readJson(exchange);
        UUID uuid = parseUuidOrNull(getOrNull(body, "uuid"));
        if (uuid == null) {
            respondJson(exchange, 400, error("Missing or invalid uuid"));
            return;
        }
        Punishment result = punishmentManager.mute(uuid, getOrNull(body, "name"), getOrNull(body, "reason"),
                operatorName(), getLongOr(body, "durationMillis", 0));
        respondJson(exchange, 200, toDto(result));
    }

    private void handleKick(HttpExchange exchange) throws IOException {
        JsonObject body = readJson(exchange);
        UUID uuid = parseUuidOrNull(getOrNull(body, "uuid"));
        if (uuid == null) {
            respondJson(exchange, 400, error("Missing or invalid uuid"));
            return;
        }
        Punishment result = punishmentManager.kick(uuid, getOrNull(body, "name"), getOrNull(body, "reason"), operatorName());
        respondJson(exchange, 200, toDto(result));
    }

    private void handleWarn(HttpExchange exchange) throws IOException {
        JsonObject body = readJson(exchange);
        UUID uuid = parseUuidOrNull(getOrNull(body, "uuid"));
        if (uuid == null) {
            respondJson(exchange, 400, error("Missing or invalid uuid"));
            return;
        }
        Punishment result = punishmentManager.warn(uuid, getOrNull(body, "name"), getOrNull(body, "reason"), operatorName());
        respondJson(exchange, 200, toDto(result));
    }

    private void handleUnban(HttpExchange exchange) throws IOException {
        JsonObject body = readJson(exchange);
        UUID uuid = parseUuidOrNull(getOrNull(body, "uuid"));
        if (uuid == null) {
            respondJson(exchange, 400, error("Missing or invalid uuid"));
            return;
        }
        boolean removed = punishmentManager.unban(uuid, operatorName(), getOrNull(body, "reason"));
        respondJson(exchange, 200, resultObject(removed));
    }

    private void handleUnbanIp(HttpExchange exchange) throws IOException {
        JsonObject body = readJson(exchange);
        String ip = getOrNull(body, "ip");
        if (ip == null || ip.isBlank()) {
            respondJson(exchange, 400, error("Missing ip"));
            return;
        }
        boolean removed = punishmentManager.unbanIp(ip, operatorName(), getOrNull(body, "reason"));
        respondJson(exchange, 200, resultObject(removed));
    }

    private void handleUnmute(HttpExchange exchange) throws IOException {
        JsonObject body = readJson(exchange);
        UUID uuid = parseUuidOrNull(getOrNull(body, "uuid"));
        if (uuid == null) {
            respondJson(exchange, 400, error("Missing or invalid uuid"));
            return;
        }
        boolean removed = punishmentManager.unmute(uuid, operatorName(), getOrNull(body, "reason"));
        respondJson(exchange, 200, resultObject(removed));
    }

    private String operatorName() {
        // Every session shares the one configured admin account, so the account name itself
        // identifies who took the action for punishment history purposes.
        return username;
    }

    // ---- DTO/JSON helpers ----

    private JsonObject toDto(Punishment punishment) {
        JsonObject json = new JsonObject();
        json.addProperty("id", punishment.getId());
        json.addProperty("type", punishment.getType().name());
        json.addProperty("targetUuid", punishment.getTargetUuid() != null ? punishment.getTargetUuid().toString() : null);
        json.addProperty("targetName", punishment.getTargetName());
        json.addProperty("ip", punishment.getIp());
        json.addProperty("reason", punishment.getReason());
        json.addProperty("operatorName", punishment.getOperatorName());
        json.addProperty("createdAt", punishment.getCreatedAt());
        json.addProperty("expiresAt", punishment.getExpiresAt());
        json.addProperty("permanent", punishment.isPermanent());
        json.addProperty("active", punishment.isActive());
        json.addProperty("removedByName", punishment.getRemovedByName());
        json.addProperty("removedReason", punishment.getRemovedReason());
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

    private JsonObject resultObject(boolean success) {
        JsonObject json = new JsonObject();
        json.addProperty("success", success);
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

    private long getLongOr(JsonObject body, String key, long fallback) {
        try {
            return body.has(key) && !body.get(key).isJsonNull() ? body.get(key).getAsLong() : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private UUID parseUuidOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private PunishmentType parseTypeOrNull(String raw) {
        try {
            return raw == null || raw.isBlank() ? null : PunishmentType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
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
