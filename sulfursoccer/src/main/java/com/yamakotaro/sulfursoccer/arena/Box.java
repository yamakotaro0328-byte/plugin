package com.yamakotaro.sulfursoccer.arena;

/** A goal - the cuboid between two wand-selected corners, inclusive of both corner blocks. */
public record Box(Point corner1, Point corner2) {

    public boolean contains(int x, int y, int z) {
        return x >= Math.min(corner1.x(), corner2.x()) && x <= Math.max(corner1.x(), corner2.x())
                && y >= Math.min(corner1.y(), corner2.y()) && y <= Math.max(corner1.y(), corner2.y())
                && z >= Math.min(corner1.z(), corner2.z()) && z <= Math.max(corner1.z(), corner2.z());
    }
}
