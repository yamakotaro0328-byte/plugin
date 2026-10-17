package com.yamakotaro.ecotp;

import org.bukkit.Bukkit;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 自動お知らせ: config.yml の announcements.messages を interval-seconds ごとに順番
 * (random: true ならランダム) に全体へ流す。設定は毎回読み直すので /ecotp reload で反映される。
 */
public class AnnouncementManager {

    private final EcoTpPlugin plugin;
    private int elapsedSeconds;
    private int nextIndex;

    public AnnouncementManager(EcoTpPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("announcements.enabled", false)) {
            elapsedSeconds = 0;
            return;
        }
        int interval = Math.max(10, plugin.getConfig().getInt("announcements.interval-seconds", 300));
        if (++elapsedSeconds < interval) {
            return;
        }
        elapsedSeconds = 0;
        List<String> messages = plugin.getConfig().getStringList("announcements.messages");
        if (messages.isEmpty() || Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }
        int index;
        if (plugin.getConfig().getBoolean("announcements.random", false)) {
            index = ThreadLocalRandom.current().nextInt(messages.size());
        } else {
            index = nextIndex % messages.size();
            nextIndex = index + 1;
        }
        String prefix = plugin.getMessages().get("announce.prefix");
        // "\n" で複数行に分けられる。
        for (String line : messages.get(index).split("\\\\n|\n")) {
            Bukkit.broadcastMessage(prefix + ChatUtil.color(line));
        }
    }
}
