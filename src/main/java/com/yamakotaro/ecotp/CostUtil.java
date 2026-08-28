package com.yamakotaro.ecotp;

import org.bukkit.Location;

public final class CostUtil {

    private CostUtil() {
    }

    /**
     * 2地点間の距離に基づく料金を計算する。ワールドが異なる場合は固定料金を使う。
     */
    public static double distanceCost(Location from, Location to, double perBlock, double crossWorldFlatCost) {
        if (from.getWorld() == null || to.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return crossWorldFlatCost;
        }
        double distance = from.distance(to);
        long blocks = (long) Math.ceil(distance);
        return blocks * perBlock;
    }
}
