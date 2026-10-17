package com.yamakotaro.ecotp;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * /daily : 1日1回ボーナスを受け取れる。streak-window-hours 以内に次の請求をすれば
 * 連続日数(streak)が伸び、報酬もその分増える。それを過ぎると streak は 1 に戻る。
 * <p>
 * ログインボーナス: auto-claim-on-join が有効なら、参加時 (とオンラインのまま日をまたいだ時)
 * に自動で受け取る。calendar で指定した連続日数に達すると特別ボーナスが上乗せされる。
 */
public class DailyRewardManager implements Listener {

    private final EcoTpPlugin plugin;
    private final DailyRewardStorage storage;
    private final Map<UUID, Integer> streakCache = new ConcurrentHashMap<>();

    public DailyRewardManager(EcoTpPlugin plugin, DailyRewardStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        // オンラインのまま日をまたいだ人にも配る (放置中の人は除く)。
        Bukkit.getScheduler().runTaskTimer(plugin, this::autoClaimOnline, 20L * 300, 20L * 300);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        streakCache.put(player.getUniqueId(), getEffectiveStreak(player.getUniqueId()));
        // 参加直後は他のメッセージに埋もれるので少し待つ。
        Bukkit.getScheduler().runTaskLater(plugin, () -> tryAutoClaim(player), 60L);
    }

    private void autoClaimOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            AfkManager afk = plugin.getAfkManager();
            if (afk != null && afk.isAfk(player.getUniqueId())) {
                continue;
            }
            tryAutoClaim(player);
        }
    }

    private void tryAutoClaim(Player player) {
        if (!player.isOnline()
                || !plugin.getConfig().getBoolean("daily-reward.auto-claim-on-join", true)
                || !plugin.isFeatureEnabled("daily")
                || !player.hasPermission("ecotp.daily")
                || !isClaimable(player.getUniqueId())
                || plugin.getEconomyHolder().get() == null) {
            return;
        }
        claim(player, true);
    }

    /**
     * @return 付与できた場合 true。まだクールダウン中だった場合は false (呼び出し側で
     * 既にメッセージを送っている)。
     */
    public boolean claim(Player player) {
        return claim(player, false);
    }

    private boolean claim(Player player, boolean auto) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldownMillis = cooldownMillis();
        long streakWindowMillis = (long) (plugin.getConfig().getDouble("daily-reward.streak-window-hours", 48.0) * 3_600_000L);
        long lastClaim = storage.getLastClaimMillis(uuid);
        int previousStreak = storage.getStreak(uuid);
        if (lastClaim < 0 || previousStreak < 0) {
            // 保存先 (MySQL) に繋がらない: 受け取り済みか分からないので払わない
            // (払うと DB が戻るまで何度でも受け取れてしまう)。
            if (!auto) {
                player.sendMessage(plugin.msg("daily.storage-error"));
            }
            return false;
        }
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

        int newStreak = (lastClaim > 0 && elapsed <= streakWindowMillis) ? previousStreak + 1 : 1;

        double normal = amountForStreak(newStreak);
        double special = calendarBonus(newStreak);
        double amount = normal + special;

        // 先に記録する: 記録に失敗したまま払うと、何度でも受け取れてしまう。
        if (!storage.recordClaim(uuid, now, newStreak)) {
            if (!auto) {
                player.sendMessage(plugin.msg("daily.storage-error"));
            }
            return false;
        }
        EconomyResponse response = economy.depositPlayer(player, amount);
        if (!response.transactionSuccess()) {
            storage.recordClaim(uuid, lastClaim, previousStreak); // 受け取っていないので元に戻す
            // Was previously ignored entirely - the player was told "claimed" and their streak
            // was consumed even when the deposit itself failed (only possible with an external
            // Vault economy; EcoTP's own always succeeds for a non-negative amount).
            plugin.getLogger().log(Level.WARNING, "Failed to deposit the daily reward for "
                    + player.getName() + ": " + response.errorMessage);
            player.sendMessage(plugin.msg("general.no-economy"));
            return false;
        }
        streakCache.put(uuid, newStreak);
        player.sendMessage(plugin.msg(auto ? "daily.login-bonus" : "daily.claimed",
                "amount", ChatUtil.formatMoney(normal), "streak", String.valueOf(newStreak)));
        if (special > 0) {
            player.sendMessage(plugin.msg("daily.calendar-bonus",
                    "bonus", ChatUtil.formatMoney(special), "streak", String.valueOf(newStreak)));
            if (plugin.getConfig().getBoolean("daily-reward.calendar-broadcast", true)) {
                Bukkit.broadcastMessage(plugin.getMessages().get("daily.calendar-broadcast",
                        "player", player.getName(), "streak", String.valueOf(newStreak), "bonus", ChatUtil.formatMoney(special)));
            }
        }
        int next = nextCalendarDay(newStreak);
        if (next > 0) {
            player.sendMessage(plugin.msg("daily.calendar-next", "days", String.valueOf(next - newStreak), "day", String.valueOf(next)));
        }
        return true;
    }

    /**
     * config.yml の daily-reward.calendar で、連続日数がちょうどその日に達した時の特別ボーナス。
     * calendar-repeat が true なら、最大の日数を1周として繰り返す (30日周期なら31日目は1日目扱い)。
     */
    public double calendarBonus(int streak) {
        return plugin.getConfig().getDouble("daily-reward.calendar." + calendarDay(streak), 0.0);
    }

    public int calendarDay(int streak) {
        int cycle = calendarCycle();
        if (cycle > 0 && streak > cycle && plugin.getConfig().getBoolean("daily-reward.calendar-repeat", true)) {
            return (streak - 1) % cycle + 1;
        }
        return streak;
    }

    public int calendarCycle() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("daily-reward.calendar");
        int max = 0;
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    max = Math.max(max, Integer.parseInt(key));
                } catch (NumberFormatException ignored) {
                    // 数字でないキーは無視
                }
            }
        }
        return max;
    }

    /** @return 次に特別ボーナスがもらえる連続日数 (実際の日数)。無ければ 0。 */
    public int nextCalendarDay(int streak) {
        int cycle = calendarCycle();
        if (cycle <= 0) {
            return 0;
        }
        boolean repeat = plugin.getConfig().getBoolean("daily-reward.calendar-repeat", true);
        int limit = repeat ? streak + cycle : cycle;
        for (int day = streak + 1; day <= limit; day++) {
            if (calendarBonus(day) > 0) {
                return day;
            }
        }
        return 0;
    }

    /** @return その連続日数の日に受け取れる通常の報酬 (特別ボーナスは含まない)。 */
    public double amountForStreak(int streak) {
        double base = plugin.getConfig().getDouble("daily-reward.base-amount", 100.0);
        double perDay = plugin.getConfig().getDouble("daily-reward.streak-bonus-per-day", 20.0);
        int maxBonusDays = plugin.getConfig().getInt("daily-reward.max-streak-bonus-days", 30);
        return base + Math.min(Math.max(0, streak - 1), maxBonusDays) * perDay;
    }

    /** @return 連続が途切れていなければ現在の連続日数、途切れていれば (次は1日目からなので) 0。 */
    public int getEffectiveStreak(UUID uuid) {
        long lastClaim = storage.getLastClaimMillis(uuid);
        long window = (long) (plugin.getConfig().getDouble("daily-reward.streak-window-hours", 48.0) * 3_600_000L);
        if (lastClaim <= 0 || System.currentTimeMillis() - lastClaim > window) {
            return 0;
        }
        return Math.max(0, storage.getStreak(uuid));
    }

    /**
     * @return プレースホルダー用: 参加時/受け取り時に覚えておいた連続日数。PlaceholderAPI は
     * 別スレッドから呼ぶことがあり、そこから MySQL に問い合わせると危ないので保存先は見ない。
     */
    public int getCachedStreak(UUID uuid) {
        return streakCache.getOrDefault(uuid, 0);
    }

    /** @return メニュー表示用: 今すぐ請求できるか (副作用なし)。 */
    public boolean isClaimable(UUID uuid) {
        return getRemainingCooldownMillis(uuid) <= 0;
    }

    /** @return メニュー表示用: 次に請求できるまでの残り時間 (ミリ秒)。既に請求可能なら 0。 */
    public long getRemainingCooldownMillis(UUID uuid) {
        long lastClaim = storage.getLastClaimMillis(uuid);
        if (lastClaim < 0) {
            return cooldownMillis(); // 読み込み失敗: 受け取れない扱い
        }
        if (lastClaim == 0) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - lastClaim;
        return Math.max(0, cooldownMillis() - elapsed);
    }

    /** @return メニュー表示用: 現在の連続請求日数。一度も請求していなければ 0。 */
    public int getStreak(UUID uuid) {
        return Math.max(0, storage.getStreak(uuid));
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
