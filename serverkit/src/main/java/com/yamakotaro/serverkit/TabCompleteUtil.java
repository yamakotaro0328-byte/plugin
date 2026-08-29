package com.yamakotaro.serverkit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TabCompleteUtil {

    private TabCompleteUtil() {
    }

    /**
     * Online player names filtered by prefix (case-insensitive).
     *
     * @param exclude skip this player's own name from the results (pass null to include everyone)
     */
    public static List<String> onlinePlayerNames(String prefix, UUID exclude) {
        String lower = prefix.toLowerCase();
        List<String> result = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (exclude != null && player.getUniqueId().equals(exclude)) {
                continue;
            }
            if (player.getName().toLowerCase().startsWith(lower)) {
                result.add(player.getName());
            }
        }
        return result;
    }

    public static List<String> filterPrefix(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}
