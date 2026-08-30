package com.yamakotaro.serverkit.claims;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 自由矩形選択のクレーム。高さ方向は制限せず、X/Z平面の矩形内すべてを保護する
 * (GriefPrevention等の一般的な土地保護プラグインと同じ考え方)。
 */
public class Claim {

    private final String name;
    private final UUID owner;
    private final String world;
    private final int minX;
    private final int minZ;
    private final int maxX;
    private final int maxZ;
    private final Set<UUID> trusted = new HashSet<>();

    public Claim(String name, UUID owner, String world, int minX, int minZ, int maxX, int maxZ) {
        this.name = name;
        this.owner = owner;
        this.world = world;
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
    }

    public String getName() {
        return name;
    }

    public UUID getOwner() {
        return owner;
    }

    public String getWorld() {
        return world;
    }

    public int getMinX() {
        return minX;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxZ() {
        return maxZ;
    }

    public Set<UUID> getTrusted() {
        return trusted;
    }

    public long area() {
        return (long) (maxX - minX + 1) * (long) (maxZ - minZ + 1);
    }

    public boolean contains(String world, int x, int z) {
        return this.world.equals(world) && x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public boolean overlaps(Claim other) {
        if (!world.equals(other.world)) {
            return false;
        }
        return minX <= other.maxX && maxX >= other.minX && minZ <= other.maxZ && maxZ >= other.minZ;
    }

    public boolean isMember(UUID uuid) {
        return owner.equals(uuid) || trusted.contains(uuid);
    }
}
