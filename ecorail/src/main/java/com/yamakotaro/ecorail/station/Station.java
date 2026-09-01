package com.yamakotaro.ecorail.station;

/**
 * dirX/dirZ is the cardinal launch direction (one of -1/0/1, never both nonzero) captured from
 * the creating player's facing at /ecorail station create time - the initial push given to a
 * minecart departing from here. Vanilla rail physics takes over and corrects onto the track from
 * there, so this only needs to be roughly right, not exact.
 */
public record Station(String id, String name, String world, int x, int y, int z, int dirX, int dirZ) {

    public double centerX() {
        return x + 0.5;
    }

    public double centerZ() {
        return z + 0.5;
    }
}
