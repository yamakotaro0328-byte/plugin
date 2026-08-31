package com.yamakotaro.ecojobs;

/**
 * A payout for one job action. Most actions only use money/xp (flat per occurrence); enchanting
 * instead uses moneyPerLevel/xpPerLevel, scaled by the enchant's own XP-level cost (see
 * {@link #moneyFor(double)}/{@link #xpFor(double)} with scale = that cost). For every other
 * action, callers pass scale = 1, which reduces to the flat money/xp values since
 * moneyPerLevel/xpPerLevel default to 0.
 */
public record ActionReward(double money, double xp, double moneyPerLevel, double xpPerLevel) {

    public double moneyFor(double scale) {
        return money + moneyPerLevel * scale;
    }

    public double xpFor(double scale) {
        return xp + xpPerLevel * scale;
    }
}
