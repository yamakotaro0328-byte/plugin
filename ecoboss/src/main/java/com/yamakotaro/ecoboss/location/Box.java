package com.yamakotaro.ecoboss.location;

/** A dungeon boss's trigger region - the cuboid between two wand-selected corners, inclusive. */
public record Box(String world, Point corner1, Point corner2) {

    public boolean contains(String worldName, int x, int y, int z) {
        return world.equals(worldName)
                && x >= Math.min(corner1.x(), corner2.x()) && x <= Math.max(corner1.x(), corner2.x())
                && y >= Math.min(corner1.y(), corner2.y()) && y <= Math.max(corner1.y(), corner2.y())
                && z >= Math.min(corner1.z(), corner2.z()) && z <= Math.max(corner1.z(), corner2.z());
    }

    public Point center() {
        int x = (corner1.x() + corner2.x()) / 2;
        int y = Math.max(corner1.y(), corner2.y());
        int z = (corner1.z() + corner2.z()) / 2;
        return new Point(world, x, y, z);
    }
}
