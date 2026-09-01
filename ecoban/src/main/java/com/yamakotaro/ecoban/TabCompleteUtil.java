package com.yamakotaro.ecoban;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class TabCompleteUtil {

    private TabCompleteUtil() {
    }

    public static List<String> onlinePlayerNames(String prefix) {
        String lower = prefix.toLowerCase();
        List<String> result = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
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
