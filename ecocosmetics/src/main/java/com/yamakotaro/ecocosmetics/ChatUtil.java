package com.yamakotaro.ecocosmetics;

import org.bukkit.ChatColor;

public final class ChatUtil {

    private ChatUtil() {
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
