package com.yamakotaro.ecorail.stop;

/** An anonymous point on the track where a managed cart pauses for dwellSeconds before continuing. */
public record StopPoint(String world, int x, int y, int z, int dwellSeconds) {

    public String key() {
        return key(world, x, y, z);
    }

    public static String key(String world, int x, int y, int z) {
        return world + ";" + x + ";" + y + ";" + z;
    }

    public double centerX() {
        return x + 0.5;
    }

    public double centerZ() {
        return z + 0.5;
    }
}
