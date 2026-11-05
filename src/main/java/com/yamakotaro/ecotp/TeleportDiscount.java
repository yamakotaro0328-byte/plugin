package com.yamakotaro.ecotp;

import org.bukkit.entity.Player;

/**
 * テレポート料金の割引: 連続ログイン日数と累計投票数が多い人ほど安くなる。
 * 割引率 = min(max-percent, 連続日数 × per-login-streak-day + 投票数 × per-vote) (%)
 */
public class TeleportDiscount {

    private final EcoTpPlugin plugin;

    public TeleportDiscount(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    /** @return この人の割引率 (0〜100 の %)。 */
    public double percent(Player player) {
        if (player == null || !plugin.getConfig().getBoolean("teleport-discount.enabled", true)) {
            return 0;
        }
        int streak = plugin.getDailyRewardManager().getEffectiveStreak(player.getUniqueId());
        int votes = plugin.getVoteRewardManager().getVoteCount(player.getName());
        double percent = streak * plugin.getConfig().getDouble("teleport-discount.per-login-streak-day", 1.0)
                + votes * plugin.getConfig().getDouble("teleport-discount.per-vote", 0.5);
        double max = Math.min(100, plugin.getConfig().getDouble("teleport-discount.max-percent", 30.0));
        return Math.max(0, Math.min(max, percent));
    }

    /** @return 割引後の料金 (端数は切り上げ)。payer は実際に料金を払う人。 */
    public double apply(Player payer, double cost) {
        if (cost <= 0) {
            return cost;
        }
        double percent = percent(payer);
        if (percent <= 0) {
            return cost;
        }
        return Math.ceil(cost * (100 - percent) / 100.0);
    }
}
