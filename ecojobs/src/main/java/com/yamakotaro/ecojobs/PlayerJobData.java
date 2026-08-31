package com.yamakotaro.ecojobs;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * All of one player's EcoJobs state: their joined jobs (with level/xp each), and their farthest
 * explorer-milestone distance so far.
 */
public class PlayerJobData {

    private String name;
    private final Map<String, PlayerJobProgress> jobs = new LinkedHashMap<>();
    private double explorerFarthestDistance;

    public PlayerJobData(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, PlayerJobProgress> getJobs() {
        return jobs;
    }

    public double getExplorerFarthestDistance() {
        return explorerFarthestDistance;
    }

    public void setExplorerFarthestDistance(double explorerFarthestDistance) {
        this.explorerFarthestDistance = explorerFarthestDistance;
    }
}
