package com.yamakotaro.ecotp;

import org.bukkit.Location;

public final class CostUtil {

    private CostUtil() {
    }

    /**
     * 距離に基づくテレポート料金。3次元の直線距離を使い、端数は切り上げる。
     * 料金 = max(minFee, ceil(距離 ÷ blocksPerYen))
     * 呼び出し前に同じディメンションであることを確認しておくこと (異なるワールド間の
     * distance() は例外になる)。
     */
    public static double distanceCost(Location from, Location to, double minFee, double blocksPerYen) {
        double distance = from.distance(to);
        double raw = Math.ceil(distance / blocksPerYen);
        return Math.max(minFee, raw);
    }
}
