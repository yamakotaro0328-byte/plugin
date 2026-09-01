package com.yamakotaro.ecobanvelocity;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/**
 * Small argument-parsing helpers shared by every punishment command. Unlike the Paper plugin,
 * Velocity has no per-server "has ever played before" cache to fall back on for an offline
 * player's name, so an offline lookup asks Mojang directly (the same approach LiteBans and most
 * other proxy-side ban plugins use).
 */
public final class PlayerResolver {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private PlayerResolver() {
    }

    public static UUID resolveUuid(ProxyServer proxyServer, String nameOrUuid) {
        try {
            return UUID.fromString(nameOrUuid);
        } catch (IllegalArgumentException ignored) {
            // fall through to name-based lookup
        }
        Optional<Player> online = proxyServer.getPlayer(nameOrUuid);
        if (online.isPresent()) {
            return online.get().getUniqueId();
        }
        return lookupMojangUuid(nameOrUuid);
    }

    private static UUID lookupMojangUuid(String name) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + name))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            String rawId = json.get("id").getAsString();
            return UUID.fromString(rawId.replaceFirst(
                    "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Accepts either a raw IP address or an online player's name (resolved to their current IP,
     * as seen directly by the proxy) - /ipban only needs to reach players who are currently
     * connected, since that's the only way to learn someone's IP in the first place.
     */
    public static String resolveIp(ProxyServer proxyServer, String ipOrName) {
        if (isIpAddress(ipOrName)) {
            return ipOrName;
        }
        Optional<Player> online = proxyServer.getPlayer(ipOrName);
        if (online.isPresent()) {
            InetSocketAddress address = online.get().getRemoteAddress();
            return address.getAddress().getHostAddress();
        }
        return ipOrName;
    }

    private static boolean isIpAddress(String value) {
        return value.matches("\\d{1,3}(\\.\\d{1,3}){3}");
    }

    public static String joinFrom(String[] args, int start) {
        return start >= args.length ? null : String.join(" ", Arrays.copyOfRange(args, start, args.length));
    }

    /** CommandSource has no name of its own; only a Player does, everything else is the console. */
    public static String operatorName(CommandSource source) {
        return source instanceof Player player ? player.getUsername() : "CONSOLE";
    }
}
