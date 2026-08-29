package com.yamakotaro.serverkit.dragonarena;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FightInstance {

    private final World world;
    private final Set<UUID> participants;
    private final Set<UUID> defeated = new HashSet<>();
    private final Map<UUID, Location> returnLocations = new HashMap<>();
    private final Map<UUID, GameMode> previousGameModes = new HashMap<>();

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
        defeated.remove(uuid);
        previousGameModes.remove(uuid);
        return participants.remove(uuid);
    }

    public boolean isEmpty() {
        return participants.isEmpty();
    }

    /** Marks a participant as defeated (spectating) without removing them from the fight. */
    public void markDefeated(UUID uuid, GameMode previousGameMode) {
        defeated.add(uuid);
        previousGameModes.put(uuid, previousGameMode);
    }

    public boolean isDefeated(UUID uuid) {
        return defeated.contains(uuid);
    }

    public GameMode getPreviousGameMode(UUID uuid) {
        return previousGameModes.getOrDefault(uuid, GameMode.SURVIVAL);
    }

    /** True once every remaining participant has been defeated (a full party wipe). */
    public boolean allDefeated() {
        return !participants.isEmpty() && defeated.containsAll(participants);
    }
}
