package com.yamakotaro.sulfursoccer.selection;

import com.yamakotaro.sulfursoccer.arena.Box;
import com.yamakotaro.sulfursoccer.arena.Point;
import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Tracks each player's in-progress wand selection - purely session state, never persisted. */
public class SelectionManager {

    private record Selection(String world, Point corner1, Point corner2) {
    }

    private final Map<UUID, Selection> selections = new HashMap<>();

    public void setCorner1(UUID playerId, Location location) {
        Selection current = selections.get(playerId);
        selections.put(playerId, new Selection(location.getWorld().getName(), toPoint(location),
                current != null && location.getWorld().getName().equals(current.world()) ? current.corner2() : null));
    }

    public void setCorner2(UUID playerId, Location location) {
        Selection current = selections.get(playerId);
        selections.put(playerId, new Selection(location.getWorld().getName(),
                current != null && location.getWorld().getName().equals(current.world()) ? current.corner1() : null, toPoint(location)));
    }

    /** @return the completed selection, if the player has set both corners in the same world. */
    public Optional<Box> getCompleteSelection(UUID playerId, String world) {
        Selection selection = selections.get(playerId);
        if (selection == null || !selection.world().equals(world) || selection.corner1() == null || selection.corner2() == null) {
            return Optional.empty();
        }
        return Optional.of(new Box(selection.corner1(), selection.corner2()));
    }

    private static Point toPoint(Location location) {
        return new Point(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
