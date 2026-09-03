package com.yamakotaro.ecotp;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * /daily : 1日1回ボーナスを受け取れる。streak-window-hours 以内に次の請求をすれば
 * 連続日数(streak)が伸び、報酬もその分増える。それを過ぎると streak は 1 に戻る。
 */
public class DailyRewardManager {

    private final EcoTpPlugin plugin;
    private final DailyRewardStorage storage;

    public DailyRewardManager(EcoTpPlugin plugin, DailyRewardStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    /**
     * @return 付与できた場合 true。まだクールダウン中だった場合は false (呼び出し側で
     * 既にメッセージを送っている)。
     */
    public boolean claim(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldownMillis = cooldownMillis();
        long streakWindowMillis = (long) (plugin.getConfig().getDouble("daily-reward.streak-window-hours", 48.0) * 3_600_000L);
        long lastClaim = storage.getLastClaimMillis(uuid);
        long elapsed = now - lastClaim;

        if (lastClaim > 0 && elapsed < cooldownMillis) {
            player.sendMessage(plugin.msg("daily.already-claimed", "time", formatDuration(cooldownMillis - elapsed)));
            return false;
        }

        Economy economy = plugin.getEconomyHolder().get();
        if (economy == null) {
            player.sendMessage(plugin.msg("general.no-economy"));
            return false;
        }

        int previousStreak = storage.getStreak(uuid);
        int newStreak = (lastClaim > 0 && elapsed <= streakWindowMillis) ? previousStreak + 1 : 1;

        double base = plugin.getConfig().getDouble("daily-reward.base-amount", 100.0);
        double perDay = plugin.getConfig().getDouble("daily-reward.streak-bonus-per-day", 20.0);
        int maxBonusDays = plugin.getConfig().getInt("daily-reward.max-streak-bonus-days", 30);
        double amount = base + Math.min(newStreak - 1, maxBonusDays) * perDay;

        economy.depositPlayer(player, amount);
        storage.recordClaim(uuid, now, newStreak);
        player.sendMessage(plugin.msg("daily.claimed", "amount", ChatUtil.formatMoney(amount), "streak", String.valueOf(newStreak)));
        return true;
    }

    /** @return メニュー表示用: 今すぐ請求できるか (副作用なし)。 */
    public boolean isClaimable(UUID uuid) {
        return getRemainingCooldownMillis(uuid) <= 0;
    }

    /** @return メニュー表示用: 次に請求できるまでの残り時間 (ミリ秒)。既に請求可能なら 0。 */
    public long getRemainingCooldownMillis(UUID uuid) {
        long lastClaim = storage.getLastClaimMillis(uuid);
        if (lastClaim <= 0) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - lastClaim;
        return Math.max(0, cooldownMillis() - elapsed);
    }

    /** @return メニュー表示用: 現在の連続請求日数。一度も請求していなければ 0。 */
    public int getStreak(UUID uuid) {
        return storage.getStreak(uuid);
    }

    /** @return 表示用にフォーマット済みの残り時間 ("1h 30m" 等)。 */
    public String formatRemainingCooldown(UUID uuid) {
        return formatDuration(getRemainingCooldownMillis(uuid));
    }

    private long cooldownMillis() {
        return (long) (plugin.getConfig().getDouble("daily-reward.cooldown-hours", 24.0) * 3_600_000L);
    }

    private static String formatDuration(long millis) {
        long totalMinutes = Math.max(1, millis / 60_000L);
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
    }
}
