package com.yamakotaro.sulfursoccer.arena;

/** Every field but id/world is set incrementally by admin commands - isReady() gates /soccer start. */
public record Arena(String id, String world, Box goalA, Box goalB, Point kickoff, Point spawnA, Point spawnB) {

    public boolean isReady() {
        return goalA != null && goalB != null && kickoff != null && spawnA != null && spawnB != null;
    }

    public Arena withGoalA(Box box) {
        return new Arena(id, world, box, goalB, kickoff, spawnA, spawnB);
    }

    public Arena withGoalB(Box box) {
        return new Arena(id, world, goalA, box, kickoff, spawnA, spawnB);
    }

    public Arena withKickoff(Point point) {
        return new Arena(id, world, goalA, goalB, point, spawnA, spawnB);
    }

    public Arena withSpawnA(Point point) {
        return new Arena(id, world, goalA, goalB, kickoff, point, spawnB);
    }

    public Arena withSpawnB(Point point) {
        return new Arena(id, world, goalA, goalB, kickoff, spawnA, point);
    }
}
