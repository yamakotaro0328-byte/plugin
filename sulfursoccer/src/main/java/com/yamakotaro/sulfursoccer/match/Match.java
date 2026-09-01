package com.yamakotaro.sulfursoccer.match;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Purely in-memory - a match doesn't need to survive a restart, it just resets. */
public class Match {

    private final String arenaId;
    private final Set<UUID> teamA = new LinkedHashSet<>();
    private final Set<UUID> teamB = new LinkedHashSet<>();
    private boolean running;
    private int scoreA;
    private int scoreB;
    private UUID ballEntityId;
    private long startedAtMillis;

    public Match(String arenaId) {
        this.arenaId = arenaId;
    }

    public String getArenaId() {
        return arenaId;
    }

    public Set<UUID> getTeamA() {
        return teamA;
    }

    public Set<UUID> getTeamB() {
        return teamB;
    }

    public boolean isPlaying(UUID playerId) {
        return teamA.contains(playerId) || teamB.contains(playerId);
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public int getScoreA() {
        return scoreA;
    }

    public int getScoreB() {
        return scoreB;
    }

    public void addScoreA() {
        scoreA++;
    }

    public void addScoreB() {
        scoreB++;
    }

    public void resetScores() {
        scoreA = 0;
        scoreB = 0;
    }

    public UUID getBallEntityId() {
        return ballEntityId;
    }

    public void setBallEntityId(UUID ballEntityId) {
        this.ballEntityId = ballEntityId;
    }

    public long getStartedAtMillis() {
        return startedAtMillis;
    }

    public void setStartedAtMillis(long startedAtMillis) {
        this.startedAtMillis = startedAtMillis;
    }
}
