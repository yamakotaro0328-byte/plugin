package com.yamakotaro.sulfursoccer.arena;

/** A cuboid between two wand-selected corners, inclusive of both corner blocks - used for a goal
 * and for the field boundary alike. */
public record Box(Point corner1, Point corner2) {

    public boolean contains(int x, int y, int z) {
        return containsXZ(x, z)
                && y >= Math.min(corner1.y(), corner2.y()) && y <= Math.max(corner1.y(), corner2.y());
    }

    /** Same as {@link #contains}, ignoring y - used to carve a goal-sized gap through a wall built at every y level. */
    public boolean containsXZ(int x, int z) {
        return x >= Math.min(corner1.x(), corner2.x()) && x <= Math.max(corner1.x(), corner2.x())
                && z >= Math.min(corner1.z(), corner2.z()) && z <= Math.max(corner1.z(), corner2.z());
    }

    public int minX() {
        return Math.min(corner1.x(), corner2.x());
    }

    public int maxX() {
        return Math.max(corner1.x(), corner2.x());
    }

    public int minY() {
        return Math.min(corner1.y(), corner2.y());
    }

    public int maxY() {
        return Math.max(corner1.y(), corner2.y());
    }

    public int minZ() {
        return Math.min(corner1.z(), corner2.z());
    }

    public int maxZ() {
        return Math.max(corner1.z(), corner2.z());
    }
}
