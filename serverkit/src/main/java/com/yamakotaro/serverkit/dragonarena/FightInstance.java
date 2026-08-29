package com.yamakotaro.serverkit.dragonarena;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FightInstance {

    private final World world;
    private final Set<UUID> participants;
    private final Map<UUID, Location> returnLocations = new HashMap<>();

    public FightInstance(World world, Set<UUID> participants) {
        this.world = world;
        this.participants = participants;
    }

    public World getWorld() {
        return world;
    }

    public Set<UUID> getParticipants() {
        return participants;
    }

    public void setReturnLocation(UUID uuid, Location location) {
        returnLocations.put(uuid, location);
    }

    public Location getReturnLocation(UUID uuid) {
        return returnLocations.get(uuid);
    }

    public boolean removeParticipant(UUID uuid) {
        return participants.remove(uuid);
    }

    public boolean isEmpty() {
        return participants.isEmpty();
    }
}
