package com.yamakotaro.ecoboss.location;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public record Point(String world, int x, int y, int z) {

    public static Point fromLocation(Location location) {
        return new Point(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /** @return the block-centered location, or null if the world isn't currently loaded. */
    public Location toLocation() {
        World bukkitWorld = Bukkit.getWorld(world);
        if (bukkitWorld == null) {
            return null;
        }
        return new Location(bukkitWorld, x + 0.5, y, z + 0.5);
    }
}
