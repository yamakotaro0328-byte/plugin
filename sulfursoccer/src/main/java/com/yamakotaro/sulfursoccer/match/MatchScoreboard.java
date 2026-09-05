package com.yamakotaro.sulfursoccer.match;

import com.yamakotaro.sulfursoccer.Messages;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The sidebar scoreboard shown to every participant of a running match: both teams' scores and the
 * time left before match.time-limit-minutes ends it (see SoccerTickTask, which calls update() every
 * tick alongside its own goal/win-condition checks). Rebuilt from scratch each time rather than
 * diffed - a couple of lines is cheap enough that a fresh Scoreboard beats tracking what changed.
 *
 * Each line needs its own uniquely-named scoreboard "entry" (a Bukkit scoreboard requirement, not
 * a display concern), so this uses one Team per line: an invisible, uniquely-colored one-character
 * entry that nobody ever sees, with the actual line text set as that team's prefix instead.
 */
public class MatchScoreboard {

    private static final String OBJECTIVE_NAME = "sulfursoccer";

    private final Messages messages;

    public MatchScoreboard(Messages messages) {
        this.messages = messages;
    }

    /** Rebuilds and reassigns the sidebar for every currently-online participant of this match. */
    public void update(Match match, String arenaId, long remainingMillis) {
        List<String> lines = new ArrayList<>();
        lines.add(messages.raw("match.scoreboard-score", Map.of(
                "scoreA", String.valueOf(match.getScoreA()), "scoreB", String.valueOf(match.getScoreB()))));
        lines.add(remainingMillis >= 0
                ? messages.raw("match.scoreboard-time", Map.of("time", formatTime(remainingMillis)))
                : messages.raw("match.scoreboard-no-limit", Map.of()));

        String title = messages.raw("match.scoreboard-title", Map.of("name", arenaId));
        for (UUID playerId : match.getTeamA()) {
            renderFor(playerId, title, lines);
        }
        for (UUID playerId : match.getTeamB()) {
            renderFor(playerId, title, lines);
        }
    }

    private void renderFor(UUID playerId, String title, List<String> lines) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective(OBJECTIVE_NAME, "dummy");
        objective.setDisplayName(title);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        int score = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            String entry = ChatColor.values()[i].toString();
            Team team = scoreboard.registerNewTeam("sulfursoccer-line" + i);
            team.addEntry(entry);
            team.setPrefix(lines.get(i));
            objective.getScore(entry).setScore(score--);
        }
        player.setScoreboard(scoreboard);
    }

    /** Resets a player back to the server's shared scoreboard. */
    public void clear(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    public void clearAll(Match match) {
        for (UUID playerId : match.getTeamA()) {
            clear(playerId);
        }
        for (UUID playerId : match.getTeamB()) {
            clear(playerId);
        }
    }

    private static String formatTime(long millis) {
        long totalSeconds = Math.max(0, millis / 1000);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
}
