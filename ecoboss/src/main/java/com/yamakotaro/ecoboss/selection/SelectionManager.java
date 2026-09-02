package com.yamakotaro.ecoboss.selection;

import com.yamakotaro.ecoboss.location.Box;
import com.yamakotaro.ecoboss.location.Point;
import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Tracks each player's in-progress wand selection for a dungeon boss region - session state only. */
public class SelectionManager {

    private record Selection(String world, Point corner1, Point corner2) {
    }

    private final Map<UUID, Selection> selections = new HashMap<>();

    public void setCorner1(UUID playerId, Location location) {
        Selection current = selections.get(playerId);
        String world = location.getWorld().getName();
        selections.put(playerId, new Selection(world, Point.fromLocation(location),
                current != null && world.equals(current.world()) ? current.corner2() : null));
    }

    public void setCorner2(UUID playerId, Location location) {
        Selection current = selections.get(playerId);
        String world = location.getWorld().getName();
        selections.put(playerId, new Selection(world,
                current != null && world.equals(current.world()) ? current.corner1() : null, Point.fromLocation(location)));
    }

    /** @return the completed selection, if the player has set both corners in the same world. */
    public Optional<Box> getCompleteSelection(UUID playerId, String world) {
        Selection selection = selections.get(playerId);
        if (selection == null || !selection.world().equals(world) || selection.corner1() == null || selection.corner2() == null) {
            return Optional.empty();
        }
        return Optional.of(new Box(world, selection.corner1(), selection.corner2()));
    }
}
