package com.yamakotaro.ecojobs;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * All of one player's EcoJobs state.
 *
 * Progress (level/xp/prestige) is permanent once earned and keyed by every job the player has
 * ever joined, even ones they've since left - leaving a job only removes it from {@link #joined}
 * (the currently-active subset actually eligible for rewards, bounded by max-concurrent-jobs), so
 * rejoining later resumes exactly where they left off instead of restarting from level 1.
 *
 * Explorer's farthest distance is tracked separately per world (so exploring a new world from
 * scratch pays out again, instead of being compared against a farthest distance set in a
 * completely different world).
 */
public class PlayerJobData {

    private String name;
    private final Map<String, PlayerJobProgress> progress = new LinkedHashMap<>();
    private final Set<String> joined = new LinkedHashSet<>();
    private final Map<String, Double> explorerDistanceByWorld = new HashMap<>();

    public PlayerJobData(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, PlayerJobProgress> getProgress() {
        return progress;
    }

    public Set<String> getJoined() {
        return joined;
    }

    public double getExplorerFarthestDistance(String worldName) {
        return explorerDistanceByWorld.getOrDefault(worldName, 0.0);
    }

    public void setExplorerFarthestDistance(String worldName, double distance) {
        explorerDistanceByWorld.put(worldName, distance);
    }

    public Map<String, Double> getExplorerDistanceByWorld() {
        return explorerDistanceByWorld;
    }
}
