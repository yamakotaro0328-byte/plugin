package com.yamakotaro.ecojobs;

/**
 * One player's progress in one job: current level and xp accumulated towards the next level.
 */
public class PlayerJobProgress {

    private int level;
    private double xp;

    public PlayerJobProgress(int level, double xp) {
        this.level = level;
        this.xp = xp;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public double getXp() {
        return xp;
    }

    public void setXp(double xp) {
        this.xp = xp;
    }
}
