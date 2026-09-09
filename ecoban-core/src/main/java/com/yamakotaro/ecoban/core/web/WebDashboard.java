package com.yamakotaro.ecoban.core.web;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.yamakotaro.ecoban.core.Punishment;
import com.yamakotaro.ecoban.core.PunishmentManager;
import com.yamakotaro.ecoban.core.PunishmentStorage;
import com.yamakotaro.ecoban.core.PunishmentType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
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
 * signing in with one of the named accounts from config.yml first, via the login button in the
 * corner. Built on the JDK's own {@link HttpServer} (no extra web-framework dependency to shade),
 * serving a single bundled HTML/CSS/JS page (see src/main/resources/web/) plus a small JSON REST
 * API.
 *
 * Session auth is a random token in a cookie, checked on every write /api/* route (see authed()
 * below) - each session remembers which account name signed in, so punishment history correctly
 * attributes an action to the staff member who actually took it rather than one shared label.
 */
public class WebDashboard {

    private static final long SESSION_LIFETIME_MILLIS = 12L * 60 * 60 * 1000; // 12 hours
    private static final String SESSION_COOKIE = "ecoban_session";
    // Several named accounts can share this dashboard, so a brute force login still only needs to
    // try password guesses against whichever username it targets - locking out an IP after a
    // handful of misses (regardless of which account it was aimed at) closes that off.
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final long LOGIN_LOCKOUT_MILLIS = 5L * 60 * 1000; // 5 minutes

    private final PunishmentManager punishmentManager;
    private final int port;
    private final Map<String, String> accounts;
    private final Logger logger;
    private final Gson gson = new Gson();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, LoginAttempt> loginAttempts = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    private HttpServer server;

    /** @param accounts username -> password for every login the dashboard should accept. */
    public WebDashboard(PunishmentManager punishmentManager, int port, Map<String, String> accounts, Logger logger) {
        this.punishmentManager = punishmentManager;
        this.port = port;
        this.accounts = accounts;
        this.logger = logger;
    }

    private record Session(String username, long expiresAtMillis) {
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
        server.createContext("/api/punishment", this::handlePunishmentDetail);
        server.createContext("/api/player", this::handlePlayer);
        server.createContext("/api/stats", this::handleStats);
        server.createContext("/api/history", this::handleHistory);
        server.createContext("/api/export.csv", this::handleExportCsv);
        // Notes are internal staff intel about a player, not a public punishment record - gated
        // behind login for both reading and writing, unlike everything else above.
        server.createContext("/api/notes/delete", authed(this::handleDeleteNote));
        server.createContext("/api/notes", authed(this::handleNotes));
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
            if (session(exchange) == null) {
                respondJson(exchange, 401, error("Not logged in"));
                return;
            }
            handler.handle(exchange);
        };
    }

    /** The logged-in session for this request, if any - removes it first if it's expired. */
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

    private record LoginAttempt(int failures, long lockedUntilMillis) {
    }

    private void handleLogout(HttpExchange exchange) throws IOException {
        String token = readCookie(exchange, SESSION_COOKIE);
        if (token != null) {
            sessions.remove(token);
        }
        respondJson(exchange, 200, okObject());
    }

    /** Lets the page ask "am I still logged in, and as whom?" (e.g. after a reload) without side effects. */
    private void handleSession(HttpExchange exchange) throws IOException {
        JsonObject json = okObject();
        json.addProperty("username", session(exchange).username());
        respondJson(exchange, 200, json);
    }

    // ---- read endpoints ----

    private void handlePunishments(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String search = query.get("q");
        int limit = Math.min(200, parseIntOr(query.get("limit"), 25));
        int page = Math.max(1, parseIntOr(query.get("page"), 1));
        int offset = (page - 1) * limit;
        // Search results are always shown across full history (matching a name/IP is the point
        // even if the punishment itself already expired) - only the plain "browse" mode respects
        // the activeOnly toggle.
        boolean activeOnly = !"false".equalsIgnoreCase(query.get("activeOnly"));
        PunishmentType type = query.containsKey("type") ? parseTypeOrNull(query.get("type")) : null;
        String sortColumn = query.get("sort");
        boolean ascending = "asc".equalsIgnoreCase(query.get("dir"));

        List<Punishment> results;
        int total;
        if (search != null && !search.isBlank()) {
            // Sorting a name/IP search is low value (matches are usually few) - always newest first.
            results = punishmentManager.search(search, limit, offset);
            total = punishmentManager.countSearch(search);
        } else {
            results = punishmentManager.list(type, activeOnly, limit, offset, sortColumn, ascending);
            total = punishmentManager.count(type, activeOnly);
        }

        JsonObject json = new JsonObject();
        json.add("items", gson.toJsonTree(results.stream().map(this::toDto).toList()));
        json.addProperty("total", total);
        json.addProperty("page", page);
        json.addProperty("limit", limit);
        respondJson(exchange, 200, json);
    }

    private void handlePunishmentDetail(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        long id = parseLongOr(query.get("id"), -1);
        Punishment punishment = id >= 0 ? punishmentManager.getById(id) : null;
        if (punishment == null) {
            respondJson(exchange, 404, error("No punishment with that id"));
            return;
        }
        respondJson(exchange, 200, toDto(punishment));
    }

    /** Aggregate profile for one player - their latest known name, a per-type breakdown, and their
     * full punishment history, all in one call for the dashboard's player profile panel. */
    private void handlePlayer(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        UUID uuid = parseUuidOrNull(query.get("uuid"));
        if (uuid == null) {
            respondJson(exchange, 400, error("Missing or invalid uuid"));
            return;
        }
        List<Punishment> history = punishmentManager.history(uuid);
        String latestName = history.stream().map(Punishment::getTargetName).filter(n -> n != null).findFirst().orElse(null);

        JsonObject counts = new JsonObject();
        for (PunishmentType type : PunishmentType.values()) {
            counts.addProperty(type.name(), history.stream().filter(p -> p.getType() == type).count());
        }

        JsonObject json = new JsonObject();
        json.addProperty("uuid", uuid.toString());
        json.addProperty("name", latestName);
        json.add("counts", counts);
        json.add("history", gson.toJsonTree(history.stream().map(this::toDto).toList()));
        respondJson(exchange, 200, json);
    }

    /** Dashboard-wide totals, a 14-day activity chart, and a staff leaderboard - computed with
     * real COUNT/GROUP BY queries rather than the client counting a capped row fetch, so these
     * stay accurate no matter how large the punishment table grows. */
    private void handleStats(HttpExchange exchange) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("activeTotal", punishmentManager.count(null, true));
        json.addProperty("activeBans", punishmentManager.count(PunishmentType.BAN, true)
                + punishmentManager.count(PunishmentType.TEMPBAN, true)
                + punishmentManager.count(PunishmentType.IPBAN, true));
        json.addProperty("activeMutes", punishmentManager.count(PunishmentType.MUTE, true)
                + punishmentManager.count(PunishmentType.TEMPMUTE, true));
        json.addProperty("activeWarns", punishmentManager.count(PunishmentType.WARN, true));
        json.addProperty("allTimeTotal", punishmentManager.count(null, false));

        List<JsonObject> daily = punishmentManager.dailyCounts(14).stream().map(day -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("date", day.dayStartMillis());
            entry.addProperty("count", day.count());
            return entry;
        }).toList();
        json.add("daily", gson.toJsonTree(daily));

        List<JsonObject> operators = punishmentManager.topOperators(0, 8).stream().map(op -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", op.operatorName());
            entry.addProperty("count", op.count());
            return entry;
        }).toList();
        json.add("topOperators", gson.toJsonTree(operators));

        List<JsonObject> targets = punishmentManager.topTargets(0, 8).stream().map(target -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("uuid", target.targetUuid().toString());
            entry.addProperty("name", target.targetName());
            entry.addProperty("count", target.count());
            return entry;
        }).toList();
        json.add("topTargets", gson.toJsonTree(targets));

        respondJson(exchange, 200, json);
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

    /** GET lists a player's staff notes (?uuid=...), POST adds one ({uuid, text}). */
    private void handleNotes(HttpExchange exchange) throws IOException {
        if ("GET".equals(exchange.getRequestMethod())) {
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            UUID uuid = parseUuidOrNull(query.get("uuid"));
            if (uuid == null) {
                respondJson(exchange, 400, error("Missing or invalid uuid"));
                return;
            }
            respondJson(exchange, 200, gson.toJsonTree(punishmentManager.listNotes(uuid).stream().map(this::toNoteDto).toList()));
            return;
        }
        if ("POST".equals(exchange.getRequestMethod())) {
            JsonObject body = readJson(exchange);
            UUID uuid = parseUuidOrNull(getOrNull(body, "uuid"));
            String text = getOrNull(body, "text");
            if (uuid == null || text == null || text.isBlank()) {
                respondJson(exchange, 400, error("Missing uuid or text"));
                return;
            }
            respondJson(exchange, 200, toNoteDto(punishmentManager.addNote(uuid, operatorName(exchange), text.trim())));
            return;
        }
        respondJson(exchange, 405, error("Use GET or POST"));
    }

    private void handleDeleteNote(HttpExchange exchange) throws IOException {
        JsonObject body = readJson(exchange);
        long id = getLongOr(body, "id", -1);
        if (id < 0) {
            respondJson(exchange, 400, error("Missing id"));
            return;
        }
        respondJson(exchange, 200, resultObject(punishmentManager.deleteNote(id)));
    }

    /** Same filters as {@link #handlePunishments}, minus pagination - streams up to 5000 matching
     * rows as a CSV download. Kept as public as browsing itself, since it's the same data. */
    private void handleExportCsv(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String search = query.get("q");
        boolean activeOnly = !"false".equalsIgnoreCase(query.get("activeOnly"));
        PunishmentType type = query.containsKey("type") ? parseTypeOrNull(query.get("type")) : null;
        int limit = 5000;

        List<Punishment> results = (search != null && !search.isBlank())
                ? punishmentManager.search(search, limit, 0)
                : punishmentManager.list(type, activeOnly, limit, 0);

        StringBuilder csv = new StringBuilder();
        csv.append("id,type,target_name,target_uuid,ip,reason,operator,created_at,expires_at,active,removed_by,removed_reason\n");
        for (Punishment p : results) {
            csv.append(csvRow(
                    String.valueOf(p.getId()),
                    p.getType().name(),
                    nullToEmpty(p.getTargetName()),
                    p.getTargetUuid() != null ? p.getTargetUuid().toString() : "",
                    nullToEmpty(p.getIp()),
                    nullToEmpty(p.getReason()),
                    nullToEmpty(p.getOperatorName()),
                    Instant.ofEpochMilli(p.getCreatedAt()).toString(),
                    p.isPermanent() ? "" : Instant.ofEpochMilli(p.getExpiresAt()).toString(),
                    String.valueOf(p.isActive()),
                    nullToEmpty(p.getRemovedByName()),
                    nullToEmpty(p.getRemovedReason())
            )).append("\n");
        }

        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=utf-8");
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"ecoban-export.csv\"");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
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
                operatorName(exchange), getLongOr(body, "durationMillis", 0));
        respondJson(exchange, 200, toDto(result));
    }

    private void handleIpban(HttpExchange exchange) throws IOException {
        JsonObject body = readJson(exchange);
        String ip = getOrNull(body, "ip");
        if (ip == null || ip.isBlank()) {
            respondJson(exchange, 400, error("Missing ip"));
            return;
        }
        Punishment result = punishmentManager.ipban(ip, getOrNull(body, "name"), getOrNull(body, "reason"), operatorName(exchange));
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
                operatorName(exchange), getLongOr(body, "durationMillis", 0));
        respondJson(exchange, 200, toDto(result));
    }

    private void handleKick(HttpExchange exchange) throws IOException {
        JsonObject body = readJson(exchange);
        UUID uuid = parseUuidOrNull(getOrNull(body, "uuid"));
        if (uuid == null) {
            respondJson(exchange, 400, error("Missing or invalid uuid"));
            return;
        }
        Punishment result = punishmentManager.kick(uuid, getOrNull(body, "name"), getOrNull(body, "reason"), operatorName(exchange));
        respondJson(exchange, 200, toDto(result));
    }

    private void handleWarn(HttpExchange exchange) throws IOException {
        JsonObject body = readJson(exchange);
        UUID uuid = parseUuidOrNull(getOrNull(body, "uuid"));
        if (uuid == null) {
            respondJson(exchange, 400, error("Missing or invalid uuid"));
            return;
        }
        Punishment result = punishmentManager.warn(uuid, getOrNull(body, "name"), getOrNull(body, "reason"), operatorName(exchange));
        respondJson(exchange, 200, toDto(result));
    }

    private void handleUnban(HttpExchange exchange) throws IOException {
        JsonObject body = readJson(exchange);
        UUID uuid = parseUuidOrNull(getOrNull(body, "uuid"));
        if (uuid == null) {
            respondJson(exchange, 400, error("Missing or invalid uuid"));
            return;
        }
        boolean removed = punishmentManager.unban(uuid, operatorName(exchange), getOrNull(body, "reason"));
        respondJson(exchange, 200, resultObject(removed));
    }

    private void handleUnbanIp(HttpExchange exchange) throws IOException {
        JsonObject body = readJson(exchange);
        String ip = getOrNull(body, "ip");
        if (ip == null || ip.isBlank()) {
            respondJson(exchange, 400, error("Missing ip"));
            return;
        }
        boolean removed = punishmentManager.unbanIp(ip, operatorName(exchange), getOrNull(body, "reason"));
        respondJson(exchange, 200, resultObject(removed));
    }

    private void handleUnmute(HttpExchange exchange) throws IOException {
        JsonObject body = readJson(exchange);
        UUID uuid = parseUuidOrNull(getOrNull(body, "uuid"));
        if (uuid == null) {
            respondJson(exchange, 400, error("Missing or invalid uuid"));
            return;
        }
        boolean removed = punishmentManager.unmute(uuid, operatorName(exchange), getOrNull(body, "reason"));
        respondJson(exchange, 200, resultObject(removed));
    }

    /** The account name behind this request's session - always present since every caller of
     * this is behind {@link #authed}, which already rejected the request otherwise. */
    private String operatorName(HttpExchange exchange) {
        return session(exchange).username();
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

    private JsonObject toNoteDto(PunishmentStorage.PlayerNote note) {
        JsonObject json = new JsonObject();
        json.addProperty("id", note.id());
        json.addProperty("targetUuid", note.targetUuid().toString());
        json.addProperty("authorName", note.authorName());
        json.addProperty("text", note.text());
        json.addProperty("createdAt", note.createdAt());
        return json;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String csvRow(String... values) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                row.append(',');
            }
            row.append(csvEscape(values[i]));
        }
        return row.toString();
    }

    private static String csvEscape(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
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

    private long parseLongOr(String raw, long fallback) {
        try {
            return raw == null ? fallback : Long.parseLong(raw);
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
