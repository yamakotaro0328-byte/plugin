package com.yamakotaro.ecotp;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * フィーバータイム: 発生中はログインボーナス・投票・サーバー目標・ボス討伐・クイズの報酬が
 * 倍になる。決まった曜日/時間 (schedule)、1時間ごとの抽選 (突然フィーバー)、管理者の
 * /fever start で始まり、発生中は全員の画面上部にボスバーで残り時間を出す。
 */
public class FeverManager implements Listener {

    private final EcoTpPlugin plugin;
    private final BossBar bar = Bukkit.createBossBar("", BarColor.RED, BarStyle.SEGMENTED_10);
    private volatile double activeMultiplier = 1.0;
    private long endsAtMillis;
    private long startedAtMillis;
    /** 管理者が止めた予定の時間帯。この終わりまでは予定で再開しない。 */
    private long suppressScheduleUntil;
    private int secondsSinceRoll;
    private int tickCount;

    public FeverManager(EcoTpPlugin plugin) {
        this.plugin = plugin;
        bar.setVisible(false);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public boolean isActive() {
        return activeMultiplier > 1.0;
    }

    /** @return 今の報酬の倍率 (フィーバー中でなければ 1)。 */
    public double multiplier() {
        return activeMultiplier;
    }

    /** @return フィーバー中なら amount に倍率を掛けた額。 */
    public double apply(double amount) {
        return amount * activeMultiplier;
    }

    public long remainingSeconds() {
        return isActive() ? Math.max(0, (endsAtMillis - System.currentTimeMillis()) / 1000) : 0;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("fever.enabled", true);
    }

    private double configMultiplier() {
        return Math.max(1.0, plugin.getConfig().getDouble("fever.multiplier", 2.0));
    }

    private void tick() {
        tickCount++;
        long now = System.currentTimeMillis();
        if (isActive()) {
            if (now >= endsAtMillis) {
                end(false);
            } else {
                updateBar(now);
            }
            return;
        }
        if (!enabled()) {
            return;
        }
        long scheduledEnd = scheduledWindowEnd();
        if (scheduledEnd > 0 && now >= suppressScheduleUntil) {
            start(scheduledEnd, configMultiplier(), "fever.started-scheduled");
            return;
        }
        // 突然フィーバー: 1時間に1回、random-chance-percent の確率で始まる。
        if (++secondsSinceRoll >= 3600) {
            secondsSinceRoll = 0;
            double chance = plugin.getConfig().getDouble("fever.random-chance-percent", 10.0);
            if (!Bukkit.getOnlinePlayers().isEmpty() && ThreadLocalRandom.current().nextDouble(100) < chance) {
                int minutes = Math.max(1, plugin.getConfig().getInt("fever.random-minutes", 10));
                start(now + minutes * 60_000L, configMultiplier(), "fever.started-random");
            }
        }
    }

    /** 管理者用。@return 始められたら true (既にフィーバー中なら false)。 */
    public boolean startManual(int minutes, double multiplier) {
        if (isActive()) {
            return false;
        }
        start(System.currentTimeMillis() + minutes * 60_000L, Math.max(1.0, multiplier), "fever.started-manual");
        return true;
    }

    /** 管理者用。@return 止められたら true。 */
    public boolean stopManual() {
        if (!isActive()) {
            return false;
        }
        long scheduledEnd = scheduledWindowEnd();
        if (scheduledEnd > 0) {
            suppressScheduleUntil = scheduledEnd; // 今の予定時間帯の間は再開しない
        }
        end(true);
        return true;
    }

    private void start(long endsAt, double multiplier, String messageKey) {
        if (multiplier <= 1.0) {
            return;
        }
        this.activeMultiplier = multiplier;
        this.startedAtMillis = System.currentTimeMillis();
        this.endsAtMillis = endsAt;
        String mult = formatMultiplier(multiplier);
        Bukkit.broadcastMessage(plugin.getMessages().get(messageKey, "multiplier", mult,
                "minutes", String.valueOf(Math.max(1, (endsAt - startedAtMillis + 59_999) / 60_000))));
        for (Player player : Bukkit.getOnlinePlayers()) {
            bar.addPlayer(player);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.4f);
            player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation().add(0, 2, 0), 60, 0.6, 0.6, 0.6, 0.15);
            player.sendTitle(plugin.getMessages().get("fever.title", "multiplier", mult),
                    plugin.getMessages().get("fever.subtitle", "multiplier", mult), 5, 50, 15);
        }
        bar.setVisible(true);
        updateBar(startedAtMillis);
    }

    private void end(boolean stoppedByAdmin) {
        activeMultiplier = 1.0;
        bar.setVisible(false);
        bar.removeAll();
        Bukkit.broadcastMessage(plugin.getMessages().get(stoppedByAdmin ? "fever.stopped" : "fever.ended"));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 1.0f);
        }
    }

    private void updateBar(long now) {
        long remaining = Math.max(0, (endsAtMillis - now) / 1000);
        bar.setTitle(plugin.getMessages().get("fever.bar", "multiplier", formatMultiplier(activeMultiplier),
                "time", String.format("%d:%02d", remaining / 60, remaining % 60)));
        double total = Math.max(1, endsAtMillis - startedAtMillis);
        bar.setProgress(Math.max(0.0, Math.min(1.0, (endsAtMillis - now) / total)));
        // 1秒ごとに色を交互に変えて目立たせる。
        bar.setColor(tickCount % 2 == 0 ? BarColor.RED : BarColor.YELLOW);
    }

    /**
     * config の fever.schedule (例 "SAT 20:00-22:00"、"* 21:00-21:30") のうち、今が入っている
     * 時間帯の終わりの時刻 (エポックミリ秒)。どれにも入っていなければ 0。
     */
    private long scheduledWindowEnd() {
        ZoneId zone;
        try {
            zone = ZoneId.of(plugin.getConfig().getString("fever.timezone", "Asia/Tokyo"));
        } catch (Exception e) {
            zone = ZoneId.systemDefault();
        }
        ZonedDateTime now = ZonedDateTime.now(zone);
        for (String entry : plugin.getConfig().getStringList("fever.schedule")) {
            try {
                String[] parts = entry.trim().split("\\s+");
                String day = parts[0].toUpperCase(Locale.ROOT);
                String[] range = parts[1].split("-");
                LocalTime from = LocalTime.parse(range[0]);
                LocalTime to = LocalTime.parse(range[1]);
                boolean dayMatches = day.equals("*") || now.getDayOfWeek() == parseDay(day);
                LocalTime time = now.toLocalTime();
                if (dayMatches && !time.isBefore(from) && time.isBefore(to)) {
                    return now.with(to).toInstant().toEpochMilli();
                }
            } catch (Exception ignored) {
                // 書式が違う行は無視
            }
        }
        return 0;
    }

    private static DayOfWeek parseDay(String text) {
        for (DayOfWeek day : DayOfWeek.values()) {
            if (day.name().startsWith(text)) {
                return day;
            }
        }
        throw new IllegalArgumentException(text);
    }

    private static String formatMultiplier(double multiplier) {
        return multiplier == Math.floor(multiplier) ? String.valueOf((long) multiplier) : String.valueOf(multiplier);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (isActive()) {
            bar.addPlayer(event.getPlayer());
            event.getPlayer().sendMessage(plugin.msg("fever.join-notice",
                    "multiplier", formatMultiplier(activeMultiplier), "minutes", String.valueOf(Math.max(1, remainingSeconds() / 60))));
        }
    }

    /** プラグイン停止時にボスバーを消す。 */
    public void shutdown() {
        bar.removeAll();
    }
}
