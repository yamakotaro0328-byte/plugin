package com.yamakotaro.ecoban;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.UUID;

/** Small argument-parsing helpers shared by every punishment command. */
public final class PlayerResolver {

    private PlayerResolver() {
    }

    /**
     * Accepts either a raw UUID or a player name (online first, then anyone who has ever played),
     * since staff need to be able to ban/mute someone who isn't currently online.
     */
    public static UUID resolveUuid(String nameOrUuid) {
        try {
            return UUID.fromString(nameOrUuid);
        } catch (IllegalArgumentException ignored) {
            // fall through to name-based lookup
        }
        Player online = Bukkit.getPlayerExact(nameOrUuid);
        if (online != null) {
            return online.getUniqueId();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(nameOrUuid);
        return offline.hasPlayedBefore() ? offline.getUniqueId() : null;
    }

    /**
     * Accepts either a raw IP address or an online player's name (resolved to their current IP) -
     * /ipban only needs to reach players who are currently connected, since that's the only way
     * to learn someone's IP in the first place.
     */
    public static String resolveIp(String ipOrName) {
        if (isIpAddress(ipOrName)) {
            return ipOrName;
        }
        Player online = Bukkit.getPlayerExact(ipOrName);
        if (online != null && online.getAddress() != null) {
            return online.getAddress().getAddress().getHostAddress();
        }
        return ipOrName;
    }

    private static boolean isIpAddress(String value) {
        return value.matches("\\d{1,3}(\\.\\d{1,3}){3}");
    }

    public static String joinFrom(String[] args, int start) {
        return start >= args.length ? null : String.join(" ", Arrays.copyOfRange(args, start, args.length));
    }
}
