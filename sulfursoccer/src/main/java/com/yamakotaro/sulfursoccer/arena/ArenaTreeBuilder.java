package com.yamakotaro.sulfursoccer.arena;

import org.bukkit.Material;
import org.bukkit.block.Block;

public class ArenaTreeBuilder {

    public static void place(Block base) {
        Block current = base.getRelative(0, 1, 0);

        for (int i = 0; i < 5; i++) {
            if (current.getType() == Material.AIR) {
                current.setType(Material.BIRCH_LOG);
            }
            current = current.getRelative(0, 1, 0);
        }

        Block top = base.getRelative(0, 6, 0);

        for (int y = 0; y < 3; y++) {
            int radius = 2;
            if (y == 2) radius = 1;

            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.abs(x) == 2 && Math.abs(z) == 2) continue;

                    Block leaf = top.getRelative(x, y, z);
                    if (leaf.getType() == Material.AIR) {
                        leaf.setType(Material.BIRCH_LEAVES);
                    }
                }
            }
        }
    }
}
