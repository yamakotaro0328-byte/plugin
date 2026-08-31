package com.yamakotaro.ecojobs;

/**
 * One player's progress in one job: current level, xp accumulated towards the next level, and
 * how many times they've prestiged (reset from max level back to 1 for a permanent pay bonus).
 */
public class PlayerJobProgress {

    private int level;
    private double xp;
    private int prestige;

    public PlayerJobProgress(int level, double xp) {
        this(level, xp, 0);
    }

    public PlayerJobProgress(int level, double xp, int prestige) {
        this.level = level;
        this.xp = xp;
        this.prestige = prestige;
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

    public int getPrestige() {
        return prestige;
    }

    public void setPrestige(int prestige) {
        this.prestige = prestige;
    }
}
