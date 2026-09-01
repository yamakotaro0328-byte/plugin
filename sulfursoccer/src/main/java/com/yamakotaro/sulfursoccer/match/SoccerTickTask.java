package com.yamakotaro.sulfursoccer.match;

import com.yamakotaro.sulfursoccer.arena.Arena;
import com.yamakotaro.sulfursoccer.arena.ArenaManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.Optional;

/** Runs every match.tick-interval-ticks: goal detection, win condition, and time limit for every running match. */
public class SoccerTickTask extends BukkitRunnable {

    private final JavaPlugin plugin;
    private final ArenaManager arenaManager;
    private final MatchManager matchManager;

    public SoccerTickTask(JavaPlugin plugin, ArenaManager arenaManager, MatchManager matchManager) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
        this.matchManager = matchManager;
    }

    @Override
    public void run() {
        int goalsToWin = plugin.getConfig().getInt("match.goals-to-win", 5);
        int timeLimitMinutes = plugin.getConfig().getInt("match.time-limit-minutes", 10);
        long now = System.currentTimeMillis();

        for (Match match : matchManager.allRunningMatches()) {
            Optional<Arena> arenaOpt = arenaManager.find(match.getArenaId());
            if (arenaOpt.isEmpty()) {
                matchManager.stopWithMessage(match.getArenaId(), "match.arena-missing");
                continue;
            }
            Arena arena = arenaOpt.get();

            Entity ball = match.getBallEntityId() != null ? Bukkit.getEntity(match.getBallEntityId()) : null;
            if (ball == null || ball.isDead()) {
                matchManager.respawnBall(match);
                continue;
            }

            if (checkGoal(match, arena, ball.getLocation())) {
                if (isMatchOver(match, goalsToWin)) {
                    matchManager.stopWithMessage(match.getArenaId(), winnerKey(match));
                }
                continue;
            }

            if (timeLimitMinutes > 0 && now - match.getStartedAtMillis() > timeLimitMinutes * 60_000L) {
                matchManager.stopWithMessage(match.getArenaId(), winnerKey(match));
            }
        }
    }

    /** @return true if a goal was scored this tick (the ball has already been respawned). */
    private boolean checkGoal(Match match, Arena arena, Location ballLocation) {
        if (!ballLocation.getWorld().getName().equals(arena.world())) {
            return false;
        }
        int x = ballLocation.getBlockX();
        int y = ballLocation.getBlockY();
        int z = ballLocation.getBlockZ();
        if (arena.goalA().contains(x, y, z)) {
            match.addScoreB();
            announceGoal(match, "match.goal-b");
        } else if (arena.goalB().contains(x, y, z)) {
            match.addScoreA();
            announceGoal(match, "match.goal-a");
        } else {
            return false;
        }
        matchManager.respawnBall(match);
        return true;
    }

    private void announceGoal(Match match, String key) {
        matchManager.announceToMatch(match, key, Map.of(
                "scoreA", String.valueOf(match.getScoreA()), "scoreB", String.valueOf(match.getScoreB())));
    }

    private boolean isMatchOver(Match match, int goalsToWin) {
        return match.getScoreA() >= goalsToWin || match.getScoreB() >= goalsToWin;
    }

    private String winnerKey(Match match) {
        if (match.getScoreA() > match.getScoreB()) {
            return "match.winner-a";
        }
        if (match.getScoreB() > match.getScoreA()) {
            return "match.winner-b";
        }
        return "match.draw";
    }
}
