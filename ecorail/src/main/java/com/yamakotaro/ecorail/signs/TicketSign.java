package com.yamakotaro.ecorail.signs;

public record TicketSign(String world, int x, int y, int z, String fromStationId, String toStationId, double price) {

    public String key() {
        return key(world, x, y, z);
    }

    public static String key(String world, int x, int y, int z) {
        return world + ";" + x + ";" + y + ";" + z;
    }
}
