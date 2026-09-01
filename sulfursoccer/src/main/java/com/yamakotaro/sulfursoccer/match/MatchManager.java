package com.yamakotaro.sulfursoccer.match;

import com.yamakotaro.sulfursoccer.Messages;
import com.yamakotaro.sulfursoccer.arena.Arena;
import com.yamakotaro.sulfursoccer.arena.ArenaManager;
import com.yamakotaro.sulfursoccer.arena.Point;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns every arena's live match state and the ball entity that goes with it. Everything here is
 * in-memory - a match doesn't survive a restart, it just resets. See SoccerTickTask for the
 * per-tick goal/win-condition checks that drive this.
 */
public class MatchManager {

    private final ArenaManager arenaManager;
    private final Messages messages;
    private final Map<String, Match> matchesByArena = new HashMap<>();

    public MatchManager(ArenaManager arenaManager, Messages messages) {
        this.arenaManager = arenaManager;
        this.messages = messages;
    }

    private Match getOrCreateMatch(String arenaId) {
        return matchesByArena.computeIfAbsent(arenaId, Match::new);
    }

    public Optional<Match> findMatchByBallId(UUID entityId) {
        return matchesByArena.values().stream().filter(m -> entityId.equals(m.getBallEntityId())).findFirst();
    }

    public Collection<Match> allRunningMatches() {
        return matchesByArena.values().stream().filter(Match::isRunning).toList();
    }

    /** Removes the player from whichever match they were in, on either team - a no-op if they weren't in one. */
    public void leave(UUID playerId) {
        for (Match match : matchesByArena.values()) {
            match.getTeamA().remove(playerId);
            match.getTeamB().remove(playerId);
        }
    }

    public String join(UUID playerId, String arenaId, char team) {
        Optional<Arena> arena = arenaManager.find(arenaId);
        if (arena.isEmpty()) {
            return "arena.not-found";
        }
        leave(playerId);
        Match match = getOrCreateMatch(arena.get().id());
        (team == 'a' ? match.getTeamA() : match.getTeamB()).add(playerId);
        return null;
    }

    public String start(String arenaId) {
        Optional<Arena> arenaOpt = arenaManager.find(arenaId);
        if (arenaOpt.isEmpty()) {
            return "arena.not-found";
        }
        Arena arena = arenaOpt.get();
        if (!arena.isReady()) {
            return "arena.not-ready";
        }
        Match match = getOrCreateMatch(arena.id());
        if (match.isRunning()) {
            return "match.already-running";
        }
        if (match.getTeamA().isEmpty() || match.getTeamB().isEmpty()) {
            return "match.need-both-teams";
        }
        World world = Bukkit.getWorld(arena.world());
        if (world == null) {
            return "arena.world-not-loaded";
        }
        for (UUID playerId : match.getTeamA()) {
            teleportIfOnline(playerId, world, arena.spawnA());
        }
        for (UUID playerId : match.getTeamB()) {
            teleportIfOnline(playerId, world, arena.spawnB());
        }
        match.resetScores();
        match.setStartedAtMillis(System.currentTimeMillis());
        match.setRunning(true);
        spawnBall(arena, match);
        return null;
    }

    public String stopWithMessage(String arenaId, String messageKey) {
        Match match = matchesByArena.get(ArenaManager.normalize(arenaId));
        if (match == null || !match.isRunning()) {
            return "match.not-running";
        }
        endMatch(match, messageKey);
        return null;
    }

    void endMatch(Match match, String announcementKey) {
        match.setRunning(false);
        removeBall(match);
        announceToMatch(match, announcementKey, Map.of(
                "scoreA", String.valueOf(match.getScoreA()), "scoreB", String.valueOf(match.getScoreB())));
    }

    /** Sends a message to every online player on either team of this match. */
    public void announceToMatch(Match match, String key, Map<String, String> placeholders) {
        for (UUID playerId : match.getTeamA()) {
            messageIfOnline(playerId, key, placeholders);
        }
        for (UUID playerId : match.getTeamB()) {
            messageIfOnline(playerId, key, placeholders);
        }
    }

    void spawnBall(Arena arena, Match match) {
        World world = Bukkit.getWorld(arena.world());
        if (world == null) {
            return;
        }
        Point kickoff = arena.kickoff();
        Location location = new Location(world, kickoff.centerX(), kickoff.y(), kickoff.centerZ());
        Entity ball = world.spawnEntity(location, EntityType.SULFUR_CUBE);
        if (ball instanceof Mob mob) {
            mob.setAI(false); // physics/knockback still apply - only independent wandering is disabled
        }
        match.setBallEntityId(ball.getUniqueId());
    }

    /** Called after a goal, or when the tracked ball entity is found dead/missing - either way, a fresh one appears at kickoff. */
    public void respawnBall(Match match) {
        removeBall(match);
        arenaManager.find(match.getArenaId()).ifPresent(arena -> spawnBall(arena, match));
    }

    private void removeBall(Match match) {
        if (match.getBallEntityId() == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(match.getBallEntityId());
        if (entity != null) {
            entity.remove();
        }
        match.setBallEntityId(null);
    }

    private void teleportIfOnline(UUID playerId, World world, Point point) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.teleport(new Location(world, point.centerX(), point.y(), point.centerZ()));
        }
    }

    private void messageIfOnline(UUID playerId, String key, Map<String, String> placeholders) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.sendMessage(messages.get(key, placeholders));
        }
    }
}
