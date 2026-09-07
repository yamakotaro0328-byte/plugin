package com.yamakotaro.sulfursoccer.arena;

import org.bukkit.Material;
import org.bukkit.block.Block;

/**
 * Places a simple birch tree at a given block, for admins who want scenery inside an arena -
 * unlike ArenaWallBuilder/GimmickBuilder, this isn't tied to any arena field the admin has to set
 * up first, it just decorates wherever they're standing (see SoccerCommand#handleArenaTree).
 */
public class ArenaTreeBuilder {

    private static final int TRUNK_HEIGHT = 5;

    private ArenaTreeBuilder() {
    }

    /** Builds a birch trunk straight up from base, then a rounded leaf canopy on top. Never
     * overwrites a block that isn't already air, so it won't punch through existing terrain or
     * builds. */
    public static void place(Block base) {
        for (int i = 0; i < TRUNK_HEIGHT; i++) {
            setIfAir(base.getRelative(0, i, 0), Material.BIRCH_LOG);
        }

        int canopyBaseY = TRUNK_HEIGHT - 2;
        for (int dy = 0; dy <= 2; dy++) {
            int y = canopyBaseY + dy;
            int radius = dy == 2 ? 1 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0 && dy < 2) {
                        continue;
                    }
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius && radius == 2) {
                        continue;
                    }
                    setIfAir(base.getRelative(dx, y, dz), Material.BIRCH_LEAVES);
                }
            }
        }
    }

    private static void setIfAir(Block block, Material material) {
        if (block.getType() == Material.AIR) {
            block.setType(material);
        }
    }
}
