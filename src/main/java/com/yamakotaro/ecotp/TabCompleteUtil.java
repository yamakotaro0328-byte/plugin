package com.yamakotaro.ecotp;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TabCompleteUtil {

    private TabCompleteUtil() {
    }

    /**
     * オンラインプレイヤー名を prefix で絞り込む (大文字小文字区別なし)。
     *
     * @param exclude この UUID のプレイヤーは候補から除く (自分自身を除きたい場合)。不要なら null。
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
