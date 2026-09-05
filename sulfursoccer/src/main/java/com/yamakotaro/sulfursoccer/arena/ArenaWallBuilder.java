package com.yamakotaro.sulfursoccer.arena;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Wraps a field's wand-selected 3D box in invisible BARRIER blocks on all four horizontal sides,
 * spanning exactly the y-range the admin selected with the wand - the same "3D select a region,
 * then wall it in" workflow WorldEdit is known for, just built specifically for this field's own
 * corners instead of a general-purpose region tool. The ball (a Sulfur Cube holding an absorbed
 * birch log - see MatchManager#spawnBall) is vanilla's own "Bouncy" archetype, so it ricochets off
 * these walls under ordinary game physics; this class only needs to make sure the walls are
 * actually there. Ceiling/floor are deliberately left open - a soccer field, not a box.
 */
public class ArenaWallBuilder {

    private ArenaWallBuilder() {
    }

    /** (Re)builds the wall for a field, leaving a gap wherever goalA or goalB's own footprint
     * overlaps the boundary so the ball (and players) can still pass through into either goal -
     * without this, a goal placed right at the field's edge would be sealed off by its own wall. */
    public static void build(World world, Box field, Box goalA, Box goalB) {
        int minX = field.minX();
        int maxX = field.maxX();
        int minZ = field.minZ();
        int maxZ = field.maxZ();
        for (int y = field.minY(); y <= field.maxY(); y++) {
            for (int x = minX; x <= maxX; x++) {
                placeIfOpen(world, x, y, minZ, goalA, goalB);
                placeIfOpen(world, x, y, maxZ, goalA, goalB);
            }
            for (int z = minZ; z <= maxZ; z++) {
                placeIfOpen(world, minX, y, z, goalA, goalB);
                placeIfOpen(world, maxX, y, z, goalA, goalB);
            }
        }
    }

    private static void placeIfOpen(World world, int x, int y, int z, Box goalA, Box goalB) {
        if (goalA.containsXZ(x, z) || goalB.containsXZ(x, z)) {
            return;
        }
        Block block = world.getBlockAt(x, y, z);
        // Only fills in empty space - a boundary the admin already built out of real blocks is
        // left alone.
        if (block.getType() == Material.AIR) {
            block.setType(Material.BARRIER);
        }
    }
}
