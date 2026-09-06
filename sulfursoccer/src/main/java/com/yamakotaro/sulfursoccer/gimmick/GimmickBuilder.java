package com.yamakotaro.sulfursoccer.gimmick;

import com.yamakotaro.sulfursoccer.arena.Box;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * ICE and BOUNCE need no per-tick code at all: converting the selected region's floor to the right
 * material once is enough, because vanilla already makes packed ice slippery and slime blocks
 * bounce anything that lands on them. Unlike ArenaWallBuilder (which only fills empty air so it
 * never overwrites a boundary the admin already built), this always overwrites the floor layer -
 * the whole point of the gimmick is replacing whatever ground was there.
 */
public class GimmickBuilder {

    private GimmickBuilder() {
    }

    /** Builds the static floor for ICE/BOUNCE gimmicks - a no-op for the dynamic types (BUMP/WIND/WARP), which GimmickTask drives instead. */
    public static void build(World world, Gimmick gimmick) {
        Material floorMaterial = switch (gimmick.type()) {
            case ICE -> Material.PACKED_ICE;
            case BOUNCE -> Material.SLIME_BLOCK;
            default -> null;
        };
        if (floorMaterial == null) {
            return;
        }
        Box region = gimmick.region();
        int y = region.minY();
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                Block block = world.getBlockAt(x, y, z);
                block.setType(floorMaterial);
            }
        }
    }
}
